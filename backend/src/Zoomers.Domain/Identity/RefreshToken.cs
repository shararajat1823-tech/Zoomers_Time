using Zoomers.Domain.Common;

namespace Zoomers.Domain.Identity;

/// <summary>
/// A server-side refresh token record. The raw token is never stored — only a
/// hash — and tokens are rotated on each refresh (<see cref="ReplacedByTokenId"/>
/// chains the lineage so reuse of a revoked token can be detected).
/// </summary>
public class RefreshToken : Entity
{
    public Guid UserId { get; set; }
    public User? User { get; set; }

    /// <summary>SHA-256 hash of the opaque token. The raw value is returned to the client once.</summary>
    public string TokenHash { get; set; } = string.Empty;

    public DateTimeOffset ExpiresAt { get; set; }
    public DateTimeOffset? RevokedAt { get; set; }
    public Guid? ReplacedByTokenId { get; set; }

    public string? CreatedByIp { get; set; }
    public string? UserAgent { get; set; }

    public bool IsExpired(DateTimeOffset now) => now >= ExpiresAt;
    public bool IsRevoked => RevokedAt is not null;
    public bool IsActive(DateTimeOffset now) => !IsRevoked && !IsExpired(now);
}
