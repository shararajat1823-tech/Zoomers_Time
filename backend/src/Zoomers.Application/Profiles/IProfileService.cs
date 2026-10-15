using Zoomers.Application.Identity.Dtos;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Profiles;

/// <summary>Detailed profile view returned to the owner or an authorized viewer.</summary>
public sealed record ProfileView(
    Guid Id,
    string Username,
    string DisplayName,
    string? Bio,
    string? Website,
    bool IsPrivate,
    bool IsVerified,
    int FollowerCount,
    int FollowingCount,
    int PostCount);

public interface IProfileService
{
    /// <summary>Returns the authenticated user's own profile.</summary>
    Task<Result<ProfileView>> GetMeAsync(Guid userId, CancellationToken ct = default);
}
