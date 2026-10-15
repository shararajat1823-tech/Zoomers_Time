using Microsoft.EntityFrameworkCore;
using Zoomers.Application.Abstractions;
using Zoomers.Application.Social.Dtos;
using Zoomers.Domain.Profiles;
using Zoomers.Domain.Social;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Social;

public sealed class SocialGraphService : ISocialGraphService
{
    private readonly IApplicationDbContext _db;
    private readonly IClock _clock;

    public SocialGraphService(IApplicationDbContext db, IClock clock)
    {
        _db = db;
        _clock = clock;
    }

    // Blocked interactions return NotFound rather than Forbidden so a block is
    // not revealed to the other party (anti-enumeration).
    private static readonly Error UserNotFound = Error.NotFound("user.not_found", "User not found.");

    public async Task<Result<FollowResult>> FollowAsync(Guid actorId, Guid targetUserId, CancellationToken ct = default)
    {
        if (actorId == targetUserId)
            return Error.Validation("social.cannot_follow_self", "You cannot follow yourself.");

        var target = await _db.Users.FirstOrDefaultAsync(u => u.Id == targetUserId, ct);
        if (target is null)
            return UserNotFound;

        if (await IsBlockedEitherWayAsync(actorId, targetUserId, ct))
            return UserNotFound;

        if (await _db.Follows.AnyAsync(f => f.FollowerId == actorId && f.FolloweeId == targetUserId, ct))
            return Result.Success(new FollowResult(FollowOutcome.AlreadyFollowing));

        var targetProfile = await _db.UserProfiles.FirstOrDefaultAsync(p => p.UserId == targetUserId, ct);
        var isPrivate = targetProfile?.IsPrivate ?? false;

        if (isPrivate)
        {
            var existing = await _db.FollowRequests.FirstOrDefaultAsync(
                r => r.RequesterId == actorId && r.TargetId == targetUserId && r.Status == FollowRequestStatus.Pending, ct);
            if (existing is not null)
                return Result.Success(new FollowResult(FollowOutcome.AlreadyRequested));

            _db.FollowRequests.Add(new FollowRequest
            {
                RequesterId = actorId,
                TargetId = targetUserId,
                Status = FollowRequestStatus.Pending,
                CreatedAt = _clock.UtcNow
            });
            await _db.SaveChangesAsync(ct);
            return Result.Success(new FollowResult(FollowOutcome.Requested));
        }

        await _db.ExecuteInTransactionAsync(async token =>
        {
            _db.Follows.Add(new Follow { FollowerId = actorId, FolloweeId = targetUserId, CreatedAt = _clock.UtcNow });
            await AdjustCountsAsync(followerId: actorId, followeeId: targetUserId, delta: +1, token);
            await _db.SaveChangesAsync(token);
        }, ct);

        return Result.Success(new FollowResult(FollowOutcome.Following));
    }

    public async Task<Result> UnfollowAsync(Guid actorId, Guid targetUserId, CancellationToken ct = default)
    {
        var edge = await _db.Follows.FirstOrDefaultAsync(f => f.FollowerId == actorId && f.FolloweeId == targetUserId, ct);
        if (edge is null)
            return Result.Success(); // idempotent

        await _db.ExecuteInTransactionAsync(async token =>
        {
            _db.Follows.Remove(edge);
            await AdjustCountsAsync(followerId: actorId, followeeId: targetUserId, delta: -1, token);
            await _db.SaveChangesAsync(token);
        }, ct);

        return Result.Success();
    }

    public async Task<Result> CancelFollowRequestAsync(Guid actorId, Guid targetUserId, CancellationToken ct = default)
    {
        var req = await _db.FollowRequests.FirstOrDefaultAsync(
            r => r.RequesterId == actorId && r.TargetId == targetUserId && r.Status == FollowRequestStatus.Pending, ct);
        if (req is null)
            return Result.Success(); // idempotent

        req.Status = FollowRequestStatus.Cancelled;
        req.DecidedAt = _clock.UtcNow;
        await _db.SaveChangesAsync(ct);
        return Result.Success();
    }

    public async Task<Result> RespondToRequestAsync(Guid actorId, Guid requestId, bool accept, CancellationToken ct = default)
    {
        var req = await _db.FollowRequests.FirstOrDefaultAsync(r => r.Id == requestId, ct);
        if (req is null || req.TargetId != actorId)
            return Error.NotFound("social.request_not_found", "Follow request not found.");

        if (!req.IsPending)
            return Error.Conflict("social.request_decided", "This request has already been handled.");

        if (!accept)
        {
            req.Status = FollowRequestStatus.Rejected;
            req.DecidedAt = _clock.UtcNow;
            await _db.SaveChangesAsync(ct);
            return Result.Success();
        }

        await _db.ExecuteInTransactionAsync(async token =>
        {
            req.Status = FollowRequestStatus.Accepted;
            req.DecidedAt = _clock.UtcNow;

            var alreadyFollows = await _db.Follows.AnyAsync(
                f => f.FollowerId == req.RequesterId && f.FolloweeId == req.TargetId, token);
            if (!alreadyFollows)
            {
                _db.Follows.Add(new Follow { FollowerId = req.RequesterId, FolloweeId = req.TargetId, CreatedAt = _clock.UtcNow });
                await AdjustCountsAsync(followerId: req.RequesterId, followeeId: req.TargetId, delta: +1, token);
            }
            await _db.SaveChangesAsync(token);
        }, ct);

        return Result.Success();
    }

    public async Task<Result> RemoveFollowerAsync(Guid actorId, Guid followerUserId, CancellationToken ct = default)
    {
        var edge = await _db.Follows.FirstOrDefaultAsync(f => f.FollowerId == followerUserId && f.FolloweeId == actorId, ct);
        if (edge is null)
            return Result.Success(); // idempotent

        await _db.ExecuteInTransactionAsync(async token =>
        {
            _db.Follows.Remove(edge);
            await AdjustCountsAsync(followerId: followerUserId, followeeId: actorId, delta: -1, token);
            await _db.SaveChangesAsync(token);
        }, ct);

        return Result.Success();
    }

    public async Task<Result<CursorPage<UserListItem>>> GetFollowersAsync(Guid viewerId, Guid targetUserId, string? cursor, int limit, CancellationToken ct = default)
    {
        var denied = await CheckListAccessAsync(viewerId, targetUserId, ct);
        if (denied is not null) return denied;

        limit = PageLimit.Clamp(limit);
        var q = _db.Follows.Where(f => f.FolloweeId == targetUserId);
        if (KeysetCursor.TryParse(cursor, out var k))
            q = q.Where(f => f.CreatedAt < k.CreatedAt || (f.CreatedAt == k.CreatedAt && f.FollowerId < k.Id));

        // Order on the edge/FK columns (translatable), project after.
        var page = await q
            .OrderByDescending(f => f.CreatedAt).ThenByDescending(f => f.FollowerId)
            .Take(limit + 1)
            .Select(f => new UserEdgeRow(
                f.Follower!.Id, f.Follower.Username, f.Follower.Profile!.DisplayName,
                f.Follower.Profile.IsVerified, f.Follower.Profile.IsPrivate, f.CreatedAt))
            .ToListAsync(ct);

        return Result.Success(BuildPage(page, limit));
    }

    public async Task<Result<CursorPage<UserListItem>>> GetFollowingAsync(Guid viewerId, Guid targetUserId, string? cursor, int limit, CancellationToken ct = default)
    {
        var denied = await CheckListAccessAsync(viewerId, targetUserId, ct);
        if (denied is not null) return denied;

        limit = PageLimit.Clamp(limit);
        var q = _db.Follows.Where(f => f.FollowerId == targetUserId);
        if (KeysetCursor.TryParse(cursor, out var k))
            q = q.Where(f => f.CreatedAt < k.CreatedAt || (f.CreatedAt == k.CreatedAt && f.FolloweeId < k.Id));

        var page = await q
            .OrderByDescending(f => f.CreatedAt).ThenByDescending(f => f.FolloweeId)
            .Take(limit + 1)
            .Select(f => new UserEdgeRow(
                f.Followee!.Id, f.Followee.Username, f.Followee.Profile!.DisplayName,
                f.Followee.Profile.IsVerified, f.Followee.Profile.IsPrivate, f.CreatedAt))
            .ToListAsync(ct);

        return Result.Success(BuildPage(page, limit));
    }

    public async Task<Result<CursorPage<FollowRequestItem>>> GetIncomingRequestsAsync(Guid actorId, string? cursor, int limit, CancellationToken ct = default)
    {
        limit = PageLimit.Clamp(limit);
        var query = _db.FollowRequests
            .Where(r => r.TargetId == actorId && r.Status == FollowRequestStatus.Pending);

        if (KeysetCursor.TryParse(cursor, out var k))
            query = query.Where(r => r.CreatedAt < k.CreatedAt || (r.CreatedAt == k.CreatedAt && r.Id < k.Id));

        var rows = await query
            .OrderByDescending(r => r.CreatedAt).ThenByDescending(r => r.Id)
            .Take(limit + 1)
            .Select(r => new
            {
                r.Id,
                r.RequesterId,
                r.CreatedAt,
                Username = r.Requester!.Username,
                DisplayName = r.Requester.Profile!.DisplayName
            })
            .ToListAsync(ct);

        var hasMore = rows.Count > limit;
        var page = rows.Take(limit).ToList();
        var items = page
            .Select(r => new FollowRequestItem(r.Id, r.RequesterId, r.Username, r.DisplayName, r.CreatedAt))
            .ToList();

        string? next = hasMore && page.Count > 0
            ? new KeysetCursor(page[^1].CreatedAt, page[^1].Id).Encode()
            : null;

        return Result.Success(new CursorPage<FollowRequestItem>(items, next));
    }

    public async Task<Result> BlockAsync(Guid actorId, Guid targetUserId, CancellationToken ct = default)
    {
        if (actorId == targetUserId)
            return Error.Validation("social.cannot_block_self", "You cannot block yourself.");

        var target = await _db.Users.FirstOrDefaultAsync(u => u.Id == targetUserId, ct);
        if (target is null)
            return UserNotFound;

        if (await _db.UserBlocks.AnyAsync(x => x.BlockerId == actorId && x.BlockedId == targetUserId, ct))
            return Result.Success(); // idempotent

        await _db.ExecuteInTransactionAsync(async token =>
        {
            // Remove follow edges in both directions, adjusting counters.
            var aFollowsB = await _db.Follows.FirstOrDefaultAsync(f => f.FollowerId == actorId && f.FolloweeId == targetUserId, token);
            if (aFollowsB is not null)
            {
                _db.Follows.Remove(aFollowsB);
                await AdjustCountsAsync(actorId, targetUserId, -1, token);
            }
            var bFollowsA = await _db.Follows.FirstOrDefaultAsync(f => f.FollowerId == targetUserId && f.FolloweeId == actorId, token);
            if (bFollowsA is not null)
            {
                _db.Follows.Remove(bFollowsA);
                await AdjustCountsAsync(targetUserId, actorId, -1, token);
            }

            // Cancel any pending requests in either direction.
            var pending = await _db.FollowRequests
                .Where(r => r.Status == FollowRequestStatus.Pending &&
                            ((r.RequesterId == actorId && r.TargetId == targetUserId) ||
                             (r.RequesterId == targetUserId && r.TargetId == actorId)))
                .ToListAsync(token);
            foreach (var r in pending)
            {
                r.Status = FollowRequestStatus.Cancelled;
                r.DecidedAt = _clock.UtcNow;
            }

            _db.UserBlocks.Add(new UserBlock { BlockerId = actorId, BlockedId = targetUserId, CreatedAt = _clock.UtcNow });
            await _db.SaveChangesAsync(token);
        }, ct);

        return Result.Success();
    }

    public async Task<Result> UnblockAsync(Guid actorId, Guid targetUserId, CancellationToken ct = default)
    {
        var block = await _db.UserBlocks.FirstOrDefaultAsync(x => x.BlockerId == actorId && x.BlockedId == targetUserId, ct);
        if (block is null)
            return Result.Success(); // idempotent

        _db.UserBlocks.Remove(block);
        await _db.SaveChangesAsync(ct);
        return Result.Success();
    }

    public async Task<RelationshipState> GetRelationshipAsync(Guid viewerId, Guid targetUserId, CancellationToken ct = default)
    {
        if (viewerId == targetUserId)
            return new RelationshipState(true, false, false, false, false, false, false);

        var isFollowing = await _db.Follows.AnyAsync(f => f.FollowerId == viewerId && f.FolloweeId == targetUserId, ct);
        var isFollowedBy = await _db.Follows.AnyAsync(f => f.FollowerId == targetUserId && f.FolloweeId == viewerId, ct);
        var outgoing = await _db.FollowRequests.AnyAsync(r => r.RequesterId == viewerId && r.TargetId == targetUserId && r.Status == FollowRequestStatus.Pending, ct);
        var incoming = await _db.FollowRequests.AnyAsync(r => r.RequesterId == targetUserId && r.TargetId == viewerId && r.Status == FollowRequestStatus.Pending, ct);
        var blocking = await _db.UserBlocks.AnyAsync(x => x.BlockerId == viewerId && x.BlockedId == targetUserId, ct);
        var blockedBy = await _db.UserBlocks.AnyAsync(x => x.BlockerId == targetUserId && x.BlockedId == viewerId, ct);

        return new RelationshipState(false, isFollowing, isFollowedBy, outgoing, incoming, blocking, blockedBy);
    }

    public Task<bool> IsBlockedEitherWayAsync(Guid a, Guid b, CancellationToken ct = default) =>
        _db.UserBlocks.AnyAsync(x =>
            (x.BlockerId == a && x.BlockedId == b) ||
            (x.BlockerId == b && x.BlockedId == a), ct);

    public async Task<bool> CanViewContentAsync(Guid viewerId, Guid targetUserId, CancellationToken ct = default)
    {
        if (viewerId == targetUserId) return true;
        if (await IsBlockedEitherWayAsync(viewerId, targetUserId, ct)) return false;

        var isPrivate = await _db.UserProfiles
            .Where(p => p.UserId == targetUserId)
            .Select(p => (bool?)p.IsPrivate)
            .FirstOrDefaultAsync(ct) ?? false;
        if (!isPrivate) return true;

        return await _db.Follows.AnyAsync(f => f.FollowerId == viewerId && f.FolloweeId == targetUserId, ct);
    }

    public async Task<Result> MuteAsync(Guid muterId, Guid mutedId, bool mutePosts, bool muteStories, CancellationToken ct = default)
    {
        if (muterId == mutedId)
            return Error.Validation("social.cannot_mute_self", "You cannot mute yourself.");
        if (!await _db.Users.AnyAsync(u => u.Id == mutedId, ct))
            return UserNotFound;

        var existing = await _db.UserMutes.FirstOrDefaultAsync(m => m.MuterId == muterId && m.MutedId == mutedId, ct);
        if (existing is null)
            _db.UserMutes.Add(new UserMute { MuterId = muterId, MutedId = mutedId, MutePosts = mutePosts, MuteStories = muteStories, CreatedAt = _clock.UtcNow });
        else
        {
            existing.MutePosts = mutePosts;
            existing.MuteStories = muteStories;
        }
        await _db.SaveChangesAsync(ct);
        return Result.Success();
    }

    public async Task<Result> UnmuteAsync(Guid muterId, Guid mutedId, CancellationToken ct = default)
    {
        var mute = await _db.UserMutes.FirstOrDefaultAsync(m => m.MuterId == muterId && m.MutedId == mutedId, ct);
        if (mute is not null)
        {
            _db.UserMutes.Remove(mute);
            await _db.SaveChangesAsync(ct);
        }
        return Result.Success();
    }

    public async Task ConvertPendingRequestsToFollowsAsync(Guid targetId, CancellationToken ct = default)
    {
        var pending = await _db.FollowRequests
            .Where(r => r.TargetId == targetId && r.Status == FollowRequestStatus.Pending)
            .ToListAsync(ct);
        if (pending.Count == 0)
            return;

        await _db.ExecuteInTransactionAsync(async token =>
        {
            foreach (var req in pending)
            {
                req.Status = FollowRequestStatus.Accepted;
                req.DecidedAt = _clock.UtcNow;

                var exists = await _db.Follows.AnyAsync(
                    f => f.FollowerId == req.RequesterId && f.FolloweeId == targetId, token);
                if (!exists)
                {
                    _db.Follows.Add(new Follow { FollowerId = req.RequesterId, FolloweeId = targetId, CreatedAt = _clock.UtcNow });
                    await AdjustCountsAsync(req.RequesterId, targetId, +1, token);
                }
            }
            await _db.SaveChangesAsync(token);
        }, ct);
    }

    // ---- helpers ----

    /// <summary>Intermediate projection carrying the user fields + the edge timestamp for keyset paging.</summary>
    private sealed record UserEdgeRow(Guid Id, string Username, string DisplayName, bool IsVerified, bool IsPrivate, DateTimeOffset CreatedAt);

    /// <summary>Returns a blocking error if the viewer may not see the target's lists, else null.</summary>
    private async Task<Error?> CheckListAccessAsync(Guid viewerId, Guid targetUserId, CancellationToken ct)
    {
        if (!await _db.Users.AnyAsync(u => u.Id == targetUserId, ct))
            return UserNotFound;

        if (await IsBlockedEitherWayAsync(viewerId, targetUserId, ct))
            return UserNotFound;

        if (!await CanViewContentAsync(viewerId, targetUserId, ct))
            return Error.Forbidden("social.private_account", "This account is private.");

        return null;
    }

    /// <summary>Assembles a cursor page from a materialized (limit+1) result set.</summary>
    private static CursorPage<UserListItem> BuildPage(List<UserEdgeRow> rows, int limit)
    {
        var hasMore = rows.Count > limit;
        var items = rows.Take(limit)
            .Select(r => new UserListItem(r.Id, r.Username, r.DisplayName, r.IsVerified, r.IsPrivate))
            .ToList();

        string? next = null;
        if (hasMore)
        {
            var last = rows[limit - 1];
            next = new KeysetCursor(last.CreatedAt, last.Id).Encode();
        }

        return new CursorPage<UserListItem>(items, next);
    }

    private async Task AdjustCountsAsync(Guid followerId, Guid followeeId, int delta, CancellationToken ct)
    {
        var followerProfile = await _db.UserProfiles.FirstOrDefaultAsync(p => p.UserId == followerId, ct);
        var followeeProfile = await _db.UserProfiles.FirstOrDefaultAsync(p => p.UserId == followeeId, ct);

        if (followerProfile is not null)
            followerProfile.FollowingCount = Math.Max(0, followerProfile.FollowingCount + delta);
        if (followeeProfile is not null)
            followeeProfile.FollowerCount = Math.Max(0, followeeProfile.FollowerCount + delta);
    }
}
