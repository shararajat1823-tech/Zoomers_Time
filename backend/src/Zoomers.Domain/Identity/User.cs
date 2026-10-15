using Zoomers.Domain.Common;

namespace Zoomers.Domain.Identity;

public enum UserStatus
{
    Active = 0,
    Deactivated = 1,
    Suspended = 2,
    PendingDeletion = 3
}

/// <summary>
/// A platform account. Credentials and lifecycle live here; presentation data
/// lives in the 1:1 <see cref="Profiles.UserProfile"/>.
/// </summary>
public class User : AuditableEntity, ISoftDeletable
{
    public string Username { get; set; } = string.Empty;
    public string? Email { get; set; }
    public string? PhoneNumber { get; set; }

    /// <summary>PBKDF2 hash (ASP.NET Identity hasher). Never plaintext.</summary>
    public string PasswordHash { get; set; } = string.Empty;

    public DateTimeOffset? EmailVerifiedAt { get; set; }
    public DateTimeOffset? PhoneVerifiedAt { get; set; }

    public UserStatus Status { get; set; } = UserStatus.Active;

    public DateTimeOffset? DeletedAt { get; set; }

    // Navigation
    public Profiles.UserProfile? Profile { get; set; }
    public Profiles.UserSettings? Settings { get; set; }
    public ICollection<RefreshToken> RefreshTokens { get; set; } = new List<RefreshToken>();

    public bool IsEmailVerified => EmailVerifiedAt is not null;
    public bool IsActive => Status == UserStatus.Active && DeletedAt is null;
}
