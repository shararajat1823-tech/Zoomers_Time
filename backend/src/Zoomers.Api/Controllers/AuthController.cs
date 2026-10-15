using Asp.Versioning;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;
using Zoomers.Api.Common;
using Zoomers.Application.Identity;
using Zoomers.Application.Identity.Dtos;

namespace Zoomers.Api.Controllers;

[ApiController]
[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/auth")]
[AllowAnonymous]
[EnableRateLimiting("auth")]
public sealed class AuthController : ControllerBase
{
    private readonly IAuthService _auth;
    public AuthController(IAuthService auth) => _auth = auth;

    /// <summary>Registers a new account and returns an authenticated session.</summary>
    [HttpPost("register")]
    [ProducesResponseType(typeof(AuthResponse), StatusCodes.Status201Created)]
    public async Task<IActionResult> Register(RegisterRequest request, CancellationToken ct)
        => (await _auth.RegisterAsync(request, BuildContext(), ct)).ToResponse(StatusCodes.Status201Created);

    /// <summary>Logs in with username/email + password.</summary>
    [HttpPost("login")]
    [ProducesResponseType(typeof(AuthResponse), StatusCodes.Status200OK)]
    public async Task<IActionResult> Login(LoginRequest request, CancellationToken ct)
        => (await _auth.LoginAsync(request, BuildContext(), ct)).ToResponse();

    /// <summary>Exchanges a valid refresh token for a new session (rotates the refresh token).</summary>
    [HttpPost("refresh")]
    [ProducesResponseType(typeof(AuthResponse), StatusCodes.Status200OK)]
    public async Task<IActionResult> Refresh(RefreshRequest request, CancellationToken ct)
        => (await _auth.RefreshAsync(request, BuildContext(), ct)).ToResponse();

    /// <summary>Revokes the presented refresh token.</summary>
    [HttpPost("logout")]
    public async Task<IActionResult> Logout(LogoutRequest request, CancellationToken ct)
        => (await _auth.LogoutAsync(request, ct)).ToResponse();

    /// <summary>Requests a password-reset link. Always succeeds to avoid account enumeration.</summary>
    [HttpPost("forgot-password")]
    public async Task<IActionResult> ForgotPassword(ForgotPasswordRequest request, CancellationToken ct)
        => (await _auth.ForgotPasswordAsync(request, ct)).ToResponse();

    /// <summary>Completes a password reset using a valid single-use token.</summary>
    [HttpPost("reset-password")]
    public async Task<IActionResult> ResetPassword(ResetPasswordRequest request, CancellationToken ct)
        => (await _auth.ResetPasswordAsync(request, ct)).ToResponse();

    private RequestContext BuildContext()
    {
        var ip = HttpContext.Connection.RemoteIpAddress?.ToString();
        var ua = Request.Headers.UserAgent.ToString();
        return new RequestContext(ip, string.IsNullOrWhiteSpace(ua) ? null : ua);
    }
}
