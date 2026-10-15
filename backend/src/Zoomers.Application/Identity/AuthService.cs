using System.Security.Cryptography;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Zoomers.Application.Abstractions;
using Zoomers.Application.Identity.Dtos;
using Zoomers.Domain.Identity;
using Zoomers.Domain.Profiles;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Identity;

/// <summary>
/// Implements registration, login, refresh-with-rotation, logout, and password
/// reset. All identity decisions are made here (server-side); the client is
/// never trusted for identity or ownership. Expected failures are returned as
/// <see cref="Result"/> errors rather than thrown.
/// </summary>
public sealed class AuthService : IAuthService
{
    private readonly IApplicationDbContext _db;
    private readonly IPasswordHasher _passwordHasher;
    private readonly ITokenService _tokens;
    private readonly IEmailSender _emailSender;
    private readonly IClock _clock;
    private readonly AuthOptions _options;
    private readonly ILogger<AuthService> _logger;

    // Cached valid hash used to equalize timing on unknown-user logins.
    private static string? _dummyHash;

    public AuthService(
        IApplicationDbContext db,
        IPasswordHasher passwordHasher,
        ITokenService tokens,
        IEmailSender emailSender,
        IClock clock,
        IOptions<AuthOptions> options,
        ILogger<AuthService> logger)
    {
        _db = db;
        _passwordHasher = passwordHasher;
        _tokens = tokens;
        _emailSender = emailSender;
        _clock = clock;
        _options = options.Value;
        _logger = logger;
    }

    public async Task<Result<AuthResponse>> RegisterAsync(RegisterRequest request, RequestContext ctx, CancellationToken ct = default)
    {
        var username = request.Username.Trim();
        var email = string.IsNullOrWhiteSpace(request.Email) ? null : request.Email.Trim();

        // Uniqueness is enforced by DB constraints too; this check gives a clean error first.
        var usernameTaken = await _db.Users.AnyAsync(u => u.Username.ToLower() == username.ToLower(), ct);
        if (usernameTaken)
            return Error.Conflict("auth.username_taken", "That username is already taken.");

        if (email is not null)
        {
            var emailTaken = await _db.Users.AnyAsync(u => u.Email != null && u.Email.ToLower() == email.ToLower(), ct);
            if (emailTaken)
                return Error.Conflict("auth.email_taken", "An account with that email already exists.");
        }

        var now = _clock.UtcNow;
        var user = new User
        {
            Username = username,
            Email = email,
            PasswordHash = _passwordHasher.Hash(request.Password),
            Status = UserStatus.Active,
            CreatedAt = now
        };
        user.Profile = new UserProfile
        {
            UserId = user.Id,
            DisplayName = string.IsNullOrWhiteSpace(request.DisplayName) ? username : request.DisplayName.Trim(),
            CreatedAt = now
        };
        user.Settings = new UserSettings { UserId = user.Id, CreatedAt = now };

        _db.Users.Add(user);

        var auth = await IssueTokensAsync(user, ctx, ct);
        await _db.SaveChangesAsync(ct);

        _logger.LogInformation("User registered {UserId}", user.Id);
        return auth;
    }

    public async Task<Result<AuthResponse>> LoginAsync(LoginRequest request, RequestContext ctx, CancellationToken ct = default)
    {
        var identifier = request.UsernameOrEmail.Trim().ToLower();
        var user = await _db.Users
            .FirstOrDefaultAsync(u =>
                u.Username.ToLower() == identifier ||
                (u.Email != null && u.Email.ToLower() == identifier), ct);

        // Generic error for both "no such user" and "wrong password" to avoid enumeration.
        var invalid = Error.Unauthorized("auth.invalid_credentials", "Invalid username or password.");

        if (user is null)
        {
            // Verify against a valid dummy hash to reduce the timing signal between
            // "no such user" and "wrong password". Must not throw on malformed input.
            _dummyHash ??= _passwordHasher.Hash("timing-mitigation-dummy");
            _passwordHasher.Verify(_dummyHash, request.Password);
            return invalid;
        }

        var verification = _passwordHasher.Verify(user.PasswordHash, request.Password);
        if (verification == PasswordVerificationResult.Failed)
            return invalid;

        if (!user.IsActive)
            return Error.Forbidden("auth.account_inactive", "This account is not active.");

        if (verification == PasswordVerificationResult.SuccessRehashNeeded)
            user.PasswordHash = _passwordHasher.Hash(request.Password);

        var auth = await IssueTokensAsync(user, ctx, ct);
        await _db.SaveChangesAsync(ct);
        return auth;
    }

    public async Task<Result<AuthResponse>> RefreshAsync(RefreshRequest request, RequestContext ctx, CancellationToken ct = default)
    {
        var now = _clock.UtcNow;
        var incomingHash = _tokens.HashRefreshToken(request.RefreshToken);

        var stored = await _db.RefreshTokens
            .Include(t => t.User)
            .FirstOrDefaultAsync(t => t.TokenHash == incomingHash, ct);

        var invalid = Error.Unauthorized("auth.invalid_refresh_token", "The refresh token is invalid or expired.");

        if (stored is null)
            return invalid;

        // Reuse of an already-rotated (revoked) token => likely theft. Revoke the whole chain.
        if (stored.IsRevoked)
        {
            await RevokeAllActiveTokensAsync(stored.UserId, now, ct);
            await _db.SaveChangesAsync(ct);
            _logger.LogWarning("Refresh token reuse detected for user {UserId}; all sessions revoked.", stored.UserId);
            return invalid;
        }

        if (!stored.IsActive(now) || stored.User is null || !stored.User.IsActive)
            return invalid;

        // Rotate: revoke the presented token and issue a new one, chaining lineage.
        var issued = _tokens.CreateRefreshToken();
        var newToken = new RefreshToken
        {
            UserId = stored.UserId,
            TokenHash = issued.Hash,
            ExpiresAt = issued.ExpiresAt,
            CreatedAt = now,
            CreatedByIp = ctx.IpAddress,
            UserAgent = ctx.UserAgent
        };
        stored.RevokedAt = now;
        stored.ReplacedByTokenId = newToken.Id;
        _db.RefreshTokens.Add(newToken);

        var access = _tokens.CreateAccessToken(stored.User);
        await _db.SaveChangesAsync(ct);

        return Result.Success(BuildResponse(stored.User, access, issued.RawValue, issued.ExpiresAt));
    }

    public async Task<Result> LogoutAsync(LogoutRequest request, CancellationToken ct = default)
    {
        var hash = _tokens.HashRefreshToken(request.RefreshToken);
        var stored = await _db.RefreshTokens.FirstOrDefaultAsync(t => t.TokenHash == hash, ct);
        if (stored is not null && !stored.IsRevoked)
        {
            stored.RevokedAt = _clock.UtcNow;
            await _db.SaveChangesAsync(ct);
        }
        // Idempotent: logging out an unknown/already-revoked token still succeeds.
        return Result.Success();
    }

    public async Task<Result> ForgotPasswordAsync(ForgotPasswordRequest request, CancellationToken ct = default)
    {
        var email = request.Email.Trim().ToLower();
        var user = await _db.Users.FirstOrDefaultAsync(u => u.Email != null && u.Email.ToLower() == email, ct);

        // Always return success to avoid revealing whether the email exists.
        if (user is null)
            return Result.Success();

        var raw = GenerateUrlSafeToken();
        var token = new PasswordResetToken
        {
            UserId = user.Id,
            TokenHash = Sha256Hex(raw),
            ExpiresAt = _clock.UtcNow.Add(_options.PasswordResetTokenLifetime),
            CreatedAt = _clock.UtcNow
        };
        _db.PasswordResetTokens.Add(token);
        await _db.SaveChangesAsync(ct);

        await _emailSender.SendPasswordResetAsync(user.Email!, raw, ct);
        return Result.Success();
    }

    public async Task<Result> ResetPasswordAsync(ResetPasswordRequest request, CancellationToken ct = default)
    {
        var email = request.Email.Trim().ToLower();
        var now = _clock.UtcNow;
        var hash = Sha256Hex(request.Token);

        var user = await _db.Users.FirstOrDefaultAsync(u => u.Email != null && u.Email.ToLower() == email, ct);
        var invalid = Error.Validation("auth.invalid_reset_token", "The reset link is invalid or has expired.");
        if (user is null)
            return invalid;

        var token = await _db.PasswordResetTokens
            .Where(t => t.UserId == user.Id && t.TokenHash == hash)
            .FirstOrDefaultAsync(ct);

        if (token is null || !token.IsUsable(now))
            return invalid;

        user.PasswordHash = _passwordHasher.Hash(request.NewPassword);
        user.UpdatedAt = now;
        token.ConsumedAt = now;

        // Security: invalidate all existing sessions on password reset.
        await RevokeAllActiveTokensAsync(user.Id, now, ct);
        await _db.SaveChangesAsync(ct);

        _logger.LogInformation("Password reset completed for user {UserId}", user.Id);
        return Result.Success();
    }

    private async Task<Result<AuthResponse>> IssueTokensAsync(User user, RequestContext ctx, CancellationToken ct)
    {
        var access = _tokens.CreateAccessToken(user);
        var issued = _tokens.CreateRefreshToken();
        _db.RefreshTokens.Add(new RefreshToken
        {
            UserId = user.Id,
            TokenHash = issued.Hash,
            ExpiresAt = issued.ExpiresAt,
            CreatedAt = _clock.UtcNow,
            CreatedByIp = ctx.IpAddress,
            UserAgent = ctx.UserAgent
        });
        await Task.CompletedTask;
        return Result.Success(BuildResponse(user, access, issued.RawValue, issued.ExpiresAt));
    }

    private async Task RevokeAllActiveTokensAsync(Guid userId, DateTimeOffset now, CancellationToken ct)
    {
        var active = await _db.RefreshTokens
            .Where(t => t.UserId == userId && t.RevokedAt == null)
            .ToListAsync(ct);
        foreach (var t in active)
            t.RevokedAt = now;
    }

    private static AuthResponse BuildResponse(User user, AccessToken access, string refreshRaw, DateTimeOffset refreshExpiry)
    {
        var summary = new UserSummary(
            user.Id,
            user.Username,
            user.Profile?.DisplayName ?? user.Username,
            user.Profile?.IsPrivate ?? false,
            user.Profile?.IsVerified ?? false);

        return new AuthResponse(access.Value, access.ExpiresAt, refreshRaw, refreshExpiry, summary);
    }

    private static string GenerateUrlSafeToken(int bytes = 32)
    {
        var buffer = RandomNumberGenerator.GetBytes(bytes);
        return Convert.ToBase64String(buffer).TrimEnd('=').Replace('+', '-').Replace('/', '_');
    }

    private static string Sha256Hex(string input)
    {
        var bytes = SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(input));
        return Convert.ToHexString(bytes);
    }
}
