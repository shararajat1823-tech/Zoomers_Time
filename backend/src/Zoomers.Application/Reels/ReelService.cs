using Microsoft.EntityFrameworkCore;
using Zoomers.Application.Abstractions;
using Zoomers.Application.Reels.Dtos;
using Zoomers.Domain.Media;
using Zoomers.Domain.Reels;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Reels;

public sealed class ReelService : IReelService
{
    private const int MaxCaption = 2200;

    private readonly IApplicationDbContext _db;
    private readonly IMediaStorage _storage;
    private readonly IClock _clock;

    public ReelService(IApplicationDbContext db, IMediaStorage storage, IClock clock)
    {
        _db = db;
        _storage = storage;
        _clock = clock;
    }

    public async Task<Result<ReelView>> CreateAsync(Guid userId, CreateReelRequest request, CancellationToken ct = default)
    {
        var video = await _db.UploadSessions.FirstOrDefaultAsync(s => s.Id == request.UploadSessionId, ct);
        if (video is null || video.UserId != userId || video.Purpose != UploadPurpose.ReelVideo)
            return Error.Validation("reel.invalid_media", "That reel video could not be found.");
        if (video.Status != UploadStatus.Completed)
            return Error.Validation("reel.media_incomplete", "The reel video upload is not complete.");

        string? thumbnailKey = null;
        if (request.ThumbnailUploadSessionId is { } thumbId)
        {
            var thumb = await _db.UploadSessions.FirstOrDefaultAsync(s => s.Id == thumbId, ct);
            if (thumb is null || thumb.UserId != userId || thumb.Purpose != UploadPurpose.ReelThumbnail || thumb.Status != UploadStatus.Completed)
                return Error.Validation("reel.invalid_thumbnail", "That reel thumbnail could not be found.");
            thumbnailKey = thumb.StorageKey;
        }

        var caption = request.Caption?.Trim();
        if (caption is { Length: > MaxCaption })
            return Error.Validation("reel.caption_too_long", $"Captions are limited to {MaxCaption} characters.");

        var now = _clock.UtcNow;
        var reel = new Reel
        {
            AuthorId = userId,
            MediaKey = video.StorageKey,
            ThumbnailKey = thumbnailKey,
            Caption = string.IsNullOrWhiteSpace(caption) ? null : caption,
            Width = request.Width,
            Height = request.Height,
            DurationMs = request.DurationMs,
            CreatedAt = now,
        };
        _db.Reels.Add(reel);
        await _db.SaveChangesAsync(ct);

        var view = await BuildViewAsync(userId, reel.Id, ct);
        return view is null
            ? Error.NotFound("reel.not_found", "Reel not found.")
            : Result.Success(view);
    }

    public Task<Result<CursorPage<ReelView>>> GetFeedAsync(Guid userId, string? cursor, int limit, CancellationToken ct = default)
    {
        // Discovery-safe reels: public accounts (or the caller's own), not blocked, not muted.
        var q = _db.Reels.Where(r =>
            (!r.Author!.Profile!.IsPrivate || r.AuthorId == userId) &&
            !_db.UserBlocks.Any(b =>
                (b.BlockerId == userId && b.BlockedId == r.AuthorId) ||
                (b.BlockerId == r.AuthorId && b.BlockedId == userId)) &&
            !_db.UserMutes.Any(m => m.MuterId == userId && m.MutedId == r.AuthorId && m.MutePosts));

        return PageAsync(userId, q, cursor, limit, ct);
    }

    public async Task<Result<CursorPage<ReelView>>> GetUserReelsAsync(Guid viewerId, Guid authorId, string? cursor, int limit, CancellationToken ct = default)
    {
        if (authorId != viewerId)
        {
            var blocked = await _db.UserBlocks.AnyAsync(b =>
                (b.BlockerId == viewerId && b.BlockedId == authorId) ||
                (b.BlockerId == authorId && b.BlockedId == viewerId), ct);
            if (blocked) return Result.Success(CursorPage<ReelView>.Empty());

            var isPrivate = await _db.UserProfiles.Where(p => p.UserId == authorId).Select(p => p.IsPrivate).FirstOrDefaultAsync(ct);
            if (isPrivate)
            {
                var follows = await _db.Follows.AnyAsync(f => f.FollowerId == viewerId && f.FolloweeId == authorId, ct);
                if (!follows) return Result.Success(CursorPage<ReelView>.Empty());
            }
        }

        var q = _db.Reels.Where(r => r.AuthorId == authorId);
        return await PageAsync(viewerId, q, cursor, limit, ct);
    }

    public Task<Result<ReelLikeState>> LikeAsync(Guid userId, Guid reelId, CancellationToken ct = default) =>
        ToggleLikeAsync(userId, reelId, like: true, ct);

    public Task<Result<ReelLikeState>> UnlikeAsync(Guid userId, Guid reelId, CancellationToken ct = default) =>
        ToggleLikeAsync(userId, reelId, like: false, ct);

    public async Task<Result> DeleteAsync(Guid userId, Guid reelId, CancellationToken ct = default)
    {
        var reel = await _db.Reels.FirstOrDefaultAsync(r => r.Id == reelId, ct);
        if (reel is null) return Error.NotFound("reel.not_found", "Reel not found.");
        if (reel.AuthorId != userId) return Error.Forbidden("reel.not_owner", "You can only delete your own reels.");

        reel.DeletedAt = _clock.UtcNow;
        await _db.SaveChangesAsync(ct);
        return Result.Success();
    }

    // ---- helpers ----

    private async Task<Result<ReelLikeState>> ToggleLikeAsync(Guid userId, Guid reelId, bool like, CancellationToken ct)
    {
        var reel = await _db.Reels.FirstOrDefaultAsync(r => r.Id == reelId, ct);
        if (reel is null) return Error.NotFound("reel.not_found", "Reel not found.");

        var existing = await _db.ReelLikes.FirstOrDefaultAsync(l => l.ReelId == reelId && l.UserId == userId, ct);

        if (like)
        {
            if (existing is not null) return Result.Success(new ReelLikeState(true, reel.LikeCount));
            try
            {
                await _db.ExecuteInTransactionAsync(async token =>
                {
                    _db.ReelLikes.Add(new ReelLike { ReelId = reelId, UserId = userId, CreatedAt = _clock.UtcNow });
                    reel.LikeCount += 1;
                    await _db.SaveChangesAsync(token);
                }, ct);
            }
            catch (DbUpdateException)
            {
                var count = await _db.Reels.Where(r => r.Id == reelId).Select(r => r.LikeCount).FirstOrDefaultAsync(ct);
                return Result.Success(new ReelLikeState(true, count));
            }
            return Result.Success(new ReelLikeState(true, reel.LikeCount));
        }
        else
        {
            if (existing is null) return Result.Success(new ReelLikeState(false, reel.LikeCount));
            await _db.ExecuteInTransactionAsync(async token =>
            {
                _db.ReelLikes.Remove(existing);
                reel.LikeCount = Math.Max(0, reel.LikeCount - 1);
                await _db.SaveChangesAsync(token);
            }, ct);
            return Result.Success(new ReelLikeState(false, reel.LikeCount));
        }
    }

    private async Task<Result<CursorPage<ReelView>>> PageAsync(Guid viewerId, IQueryable<Reel> query, string? cursor, int limit, CancellationToken ct)
    {
        limit = PageLimit.Clamp(limit);

        if (KeysetCursor.TryParse(cursor, out var k))
            query = query.Where(r => r.CreatedAt < k.CreatedAt || (r.CreatedAt == k.CreatedAt && r.Id < k.Id));

        var rows = await query
            .OrderByDescending(r => r.CreatedAt).ThenByDescending(r => r.Id)
            .Take(limit + 1)
            .Select(r => new Row(
                r.Id, r.AuthorId, r.MediaKey, r.ThumbnailKey, r.Caption, r.Width, r.Height, r.DurationMs, r.LikeCount, r.CreatedAt,
                r.Author!.Username, r.Author.Profile!.DisplayName, r.Author.Profile.IsVerified, r.Author.Profile.AvatarMediaKey,
                _db.ReelLikes.Any(l => l.ReelId == r.Id && l.UserId == viewerId)))
            .ToListAsync(ct);

        var hasMore = rows.Count > limit;
        var page = rows.Take(limit).ToList();

        var views = new List<ReelView>(page.Count);
        foreach (var r in page) views.Add(await ToViewAsync(r, ct));

        var next = hasMore && page.Count > 0
            ? new KeysetCursor(page[^1].CreatedAt, page[^1].Id).Encode()
            : null;

        return Result.Success(new CursorPage<ReelView>(views, next));
    }

    private async Task<ReelView?> BuildViewAsync(Guid viewerId, Guid reelId, CancellationToken ct)
    {
        var row = await _db.Reels.Where(r => r.Id == reelId)
            .Select(r => new Row(
                r.Id, r.AuthorId, r.MediaKey, r.ThumbnailKey, r.Caption, r.Width, r.Height, r.DurationMs, r.LikeCount, r.CreatedAt,
                r.Author!.Username, r.Author.Profile!.DisplayName, r.Author.Profile.IsVerified, r.Author.Profile.AvatarMediaKey,
                _db.ReelLikes.Any(l => l.ReelId == r.Id && l.UserId == viewerId)))
            .FirstOrDefaultAsync(ct);
        return row is null ? null : await ToViewAsync(row, ct);
    }

    private async Task<ReelView> ToViewAsync(Row r, CancellationToken ct)
    {
        var mediaUrl = (await _storage.CreateDownloadUrlAsync(r.MediaKey, ct)).Url;
        var thumbUrl = string.IsNullOrEmpty(r.ThumbnailKey) ? null : (await _storage.CreateDownloadUrlAsync(r.ThumbnailKey, ct)).Url;
        return new ReelView(
            r.Id,
            new ReelAuthorDto(
                r.AuthorId, r.Username, r.DisplayName,
                string.IsNullOrEmpty(r.AvatarKey) ? null : (await _storage.CreateDownloadUrlAsync(r.AvatarKey, ct)).Url,
                r.IsVerified),
            mediaUrl, thumbUrl, r.Caption, r.Width, r.Height, r.DurationMs, r.LikeCount, r.ViewerHasLiked, r.CreatedAt);
    }

    private sealed record Row(
        Guid Id, Guid AuthorId, string MediaKey, string? ThumbnailKey, string? Caption,
        int Width, int Height, int DurationMs, int LikeCount, DateTimeOffset CreatedAt,
        string Username, string DisplayName, bool IsVerified, string? AvatarKey, bool ViewerHasLiked);
}
