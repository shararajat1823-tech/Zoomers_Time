using Microsoft.EntityFrameworkCore;
using Zoomers.Application.Abstractions;
using Zoomers.Application.Profiles.Dtos;
using Zoomers.Application.Social;
using Zoomers.Domain.Media;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Profiles;

public sealed class ProfileService : IProfileService
{
    private readonly IApplicationDbContext _db;
    private readonly ISocialGraphService _social;
    private readonly IMediaStorage _storage;
    private readonly IClock _clock;

    public ProfileService(IApplicationDbContext db, ISocialGraphService social, IMediaStorage storage, IClock clock)
    {
        _db = db;
        _social = social;
        _storage = storage;
        _clock = clock;
    }

    public async Task<Result<ProfileView>> GetMeAsync(Guid userId, CancellationToken ct = default)
    {
        var row = await _db.Users
            .Where(u => u.Id == userId)
            .Select(u => new
            {
                u.Id,
                u.Username,
                u.Profile!.DisplayName,
                u.Profile.Bio,
                u.Profile.Website,
                u.Profile.AvatarMediaKey,
                u.Profile.IsPrivate,
                u.Profile.IsVerified,
                u.Profile.FollowerCount,
                u.Profile.FollowingCount,
                u.Profile.PostCount,
            })
            .FirstOrDefaultAsync(ct);

        if (row is null)
            return Error.NotFound("profile.not_found", "Profile not found.");

        var view = new ProfileView(
            row.Id, row.Username, row.DisplayName, row.Bio, row.Website,
            await ResolveAvatarUrlAsync(row.AvatarMediaKey, ct),
            row.IsPrivate, row.IsVerified, row.FollowerCount, row.FollowingCount, row.PostCount);

        return Result.Success(view);
    }

    private async Task<string?> ResolveAvatarUrlAsync(string? avatarMediaKey, CancellationToken ct) =>
        string.IsNullOrEmpty(avatarMediaKey) ? null : (await _storage.CreateDownloadUrlAsync(avatarMediaKey, ct)).Url;

    public async Task<Result<PublicProfileView>> GetByUsernameAsync(Guid viewerId, string username, CancellationToken ct = default)
    {
        username = username.Trim();
        var target = await _db.Users
            .Where(u => u.Username == username)
            .Select(u => new { u.Id, u.Username, Profile = u.Profile! })
            .FirstOrDefaultAsync(ct);

        // Blocked either way behaves as "not found" to avoid revealing the block or the account.
        if (target is null || await _social.IsBlockedEitherWayAsync(viewerId, target.Id, ct))
            return Error.NotFound("profile.not_found", "Profile not found.");

        var relationship = await _social.GetRelationshipAsync(viewerId, target.Id, ct);
        var canViewPosts = await _social.CanViewContentAsync(viewerId, target.Id, ct);

        var view = new PublicProfileView(
            target.Id,
            target.Username,
            target.Profile.DisplayName,
            target.Profile.Bio,
            target.Profile.Website,
            await ResolveAvatarUrlAsync(target.Profile.AvatarMediaKey, ct),
            target.Profile.IsVerified,
            target.Profile.IsPrivate,
            target.Profile.FollowerCount,
            target.Profile.FollowingCount,
            target.Profile.PostCount,
            relationship,
            canViewPosts);

        return Result.Success(view);
    }

    public async Task<Result<ProfileView>> UpdateMeAsync(Guid userId, UpdateProfileRequest request, CancellationToken ct = default)
    {
        var profile = await _db.UserProfiles.FirstOrDefaultAsync(p => p.UserId == userId, ct);
        if (profile is null)
            return Error.NotFound("profile.not_found", "Profile not found.");

        if (request.DisplayName is not null)
            profile.DisplayName = request.DisplayName.Trim();
        if (request.Bio is not null)
            profile.Bio = string.IsNullOrWhiteSpace(request.Bio) ? null : request.Bio.Trim();
        if (request.Website is not null)
            profile.Website = string.IsNullOrWhiteSpace(request.Website) ? null : request.Website.Trim();

        if (request.AvatarUploadSessionId is { } avatarSessionId)
        {
            var session = await _db.UploadSessions.FirstOrDefaultAsync(s => s.Id == avatarSessionId, ct);
            if (session is null || session.UserId != userId || session.Purpose != UploadPurpose.Avatar)
                return Error.Validation("profile.invalid_avatar", "That avatar upload could not be found.");
            if (session.Status != UploadStatus.Completed)
                return Error.Validation("profile.avatar_incomplete", "The avatar upload is not complete.");
            profile.AvatarMediaKey = session.StorageKey;
        }

        var switchingToPublic = request.IsPrivate is false && profile.IsPrivate;
        if (request.IsPrivate is not null)
            profile.IsPrivate = request.IsPrivate.Value;

        profile.UpdatedAt = _clock.UtcNow;
        await _db.SaveChangesAsync(ct);

        // Going public approves any pending follow requests.
        if (switchingToPublic)
            await _social.ConvertPendingRequestsToFollowsAsync(userId, ct);

        return await GetMeAsync(userId, ct);
    }
}
