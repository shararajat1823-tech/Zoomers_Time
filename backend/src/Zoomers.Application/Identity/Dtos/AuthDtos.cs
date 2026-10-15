namespace Zoomers.Application.Identity.Dtos;

public sealed record RegisterRequest(
    string Username,
    string? Email,
    string Password,
    string DisplayName);

public sealed record LoginRequest(
    string UsernameOrEmail,
    string Password);

public sealed record RefreshRequest(string RefreshToken);

public sealed record LogoutRequest(string RefreshToken);

public sealed record ForgotPasswordRequest(string Email);

public sealed record ResetPasswordRequest(
    string Email,
    string Token,
    string NewPassword);

/// <summary>Minimal public view of a user, safe to embed in auth responses.</summary>
public sealed record UserSummary(
    Guid Id,
    string Username,
    string DisplayName,
    bool IsPrivate,
    bool IsVerified);

/// <summary>Issued on successful register/login/refresh.</summary>
public sealed record AuthResponse(
    string AccessToken,
    DateTimeOffset AccessTokenExpiresAt,
    string RefreshToken,
    DateTimeOffset RefreshTokenExpiresAt,
    UserSummary User);
