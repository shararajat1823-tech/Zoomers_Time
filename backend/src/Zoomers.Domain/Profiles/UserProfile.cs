using Zoomers.Domain.Common;
using Zoomers.Domain.Identity;

namespace Zoomers.Domain.Profiles;

/// <summary>
/// Presentation data for a user (1:1 with <see cref="User"/>). Follower/following/
/// post counts are denormalized caches maintained transactionally with the
/// relevant edge changes and reconcilable by a periodic recount.
/// </summary>
public class UserProfile : AuditableEntity
{
    public Guid UserId { get; set; }
    public User? User { get; set; }

    public string DisplayName { get; set; } = string.Empty;
    public string? Bio { get; set; }
    public string? Website { get; set; }

    /// <summary>Storage key for the avatar object (not a public URL).</summary>
    public string? AvatarMediaKey { get; set; }
    public string? CoverMediaKey { get; set; }

    public bool IsPrivate { get; set; }
    public bool IsVerified { get; set; }

    public int FollowerCount { get; set; }
    public int FollowingCount { get; set; }
    public int PostCount { get; set; }
}
