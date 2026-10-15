using Zoomers.Application.Identity.Dtos;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Identity;

/// <summary>Context about the caller used for auditing refresh-token issuance.</summary>
public sealed record RequestContext(string? IpAddress, string? UserAgent);

public interface IAuthService
{
    Task<Result<AuthResponse>> RegisterAsync(RegisterRequest request, RequestContext ctx, CancellationToken ct = default);
    Task<Result<AuthResponse>> LoginAsync(LoginRequest request, RequestContext ctx, CancellationToken ct = default);
    Task<Result<AuthResponse>> RefreshAsync(RefreshRequest request, RequestContext ctx, CancellationToken ct = default);
    Task<Result> LogoutAsync(LogoutRequest request, CancellationToken ct = default);
    Task<Result> ForgotPasswordAsync(ForgotPasswordRequest request, CancellationToken ct = default);
    Task<Result> ResetPasswordAsync(ResetPasswordRequest request, CancellationToken ct = default);
}
