using Zoomers.Domain.Identity;

namespace Zoomers.Application.Abstractions;

/// <summary>A freshly issued access token and its expiry.</summary>
public sealed record AccessToken(string Value, DateTimeOffset ExpiresAt);

/// <summary>
/// A freshly issued refresh token. <see cref="RawValue"/> is returned to the
/// client exactly once; only <see cref="Hash"/> is persisted server-side.
/// </summary>
public sealed record IssuedRefreshToken(string RawValue, string Hash, DateTimeOffset ExpiresAt);

/// <summary>
/// Issues signed JWT access tokens and opaque refresh tokens. Implemented in
/// Infrastructure using established JWT libraries; signing keys come from
/// configuration and are never committed.
/// </summary>
public interface ITokenService
{
    AccessToken CreateAccessToken(User user);

    /// <summary>Generates a new random refresh token (raw + hash + expiry).</summary>
    IssuedRefreshToken CreateRefreshToken();

    /// <summary>Hashes a raw refresh token for lookup/comparison (constant per input).</summary>
    string HashRefreshToken(string rawValue);
}
