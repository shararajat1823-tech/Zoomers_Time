using Zoomers.Application.Profiles.Dtos;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Profiles;

/// <summary>Detailed profile view returned to the owner.</summary>
public sealed record ProfileView(
    Guid Id,
    string Username,
    string DisplayName,
    string? Bio,
    string? Website,
    string? AvatarUrl,
    bool IsPrivate,
    bool IsVerified,
    int FollowerCount,
    int FollowingCount,
    int PostCount);

public interface IProfileService
{
    /// <summary>Returns the authenticated user's own profile.</summary>
    Task<Result<ProfileView>> GetMeAsync(Guid userId, CancellationToken ct = default);

    /// <summary>Returns a public profile by username with the viewer's relationship and visibility.</summary>
    Task<Result<PublicProfileView>> GetByUsernameAsync(Guid viewerId, string username, CancellationToken ct = default);

    /// <summary>Edits the authenticated user's profile (partial update).</summary>
    Task<Result<ProfileView>> UpdateMeAsync(Guid userId, UpdateProfileRequest request, CancellationToken ct = default);
}
