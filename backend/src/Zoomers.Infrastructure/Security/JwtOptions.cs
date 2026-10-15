using System.ComponentModel.DataAnnotations;

namespace Zoomers.Infrastructure.Security;

/// <summary>
/// JWT signing configuration. <see cref="SigningKey"/> is a secret supplied via
/// environment/secret manager — never committed. Validated at startup.
/// </summary>
public sealed class JwtOptions
{
    public const string SectionName = "Jwt";

    [Required] public string Issuer { get; set; } = "zoomers";
    [Required] public string Audience { get; set; } = "zoomers.clients";

    /// <summary>HMAC signing key; must be at least 32 bytes. Supplied as a secret.</summary>
    [Required, MinLength(32)] public string SigningKey { get; set; } = string.Empty;

    public int AccessTokenMinutes { get; set; } = 15;
    public int RefreshTokenDays { get; set; } = 30;
}
