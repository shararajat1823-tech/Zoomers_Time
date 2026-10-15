using Zoomers.Application.Social.Dtos;

namespace Zoomers.Application.Profiles.Dtos;

/// <summary>Public profile view with the viewer's relationship and a posts-visibility flag.</summary>
public sealed record PublicProfileView(
    Guid Id,
    string Username,
    string DisplayName,
    string? Bio,
    string? Website,
    bool IsVerified,
    bool IsPrivate,
    int FollowerCount,
    int FollowingCount,
    int PostCount,
    RelationshipState Relationship,
    bool CanViewPosts);

/// <summary>Partial profile edit. Null fields are left unchanged.</summary>
public sealed record UpdateProfileRequest(
    string? DisplayName,
    string? Bio,
    string? Website,
    bool? IsPrivate);
