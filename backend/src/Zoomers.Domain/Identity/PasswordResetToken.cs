using Zoomers.Domain.Common;

namespace Zoomers.Domain.Identity;

/// <summary>Single-use, short-lived, hashed password-reset token.</summary>
public class PasswordResetToken : Entity
{
    public Guid UserId { get; set; }
    public User? User { get; set; }

    public string TokenHash { get; set; } = string.Empty;
    public DateTimeOffset ExpiresAt { get; set; }
    public DateTimeOffset? ConsumedAt { get; set; }

    public bool IsConsumed => ConsumedAt is not null;
    public bool IsUsable(DateTimeOffset now) => !IsConsumed && now < ExpiresAt;
}
