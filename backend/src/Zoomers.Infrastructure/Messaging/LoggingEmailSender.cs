using Microsoft.Extensions.Logging;
using Zoomers.Application.Abstractions;

namespace Zoomers.Infrastructure.Messaging;

/// <summary>
/// Development email sender that logs the message instead of delivering it (no
/// external account required). Production swaps in a real provider. The reset
/// token itself is logged at Debug only in development builds; it is never
/// written at Information level and never returned in API responses.
/// </summary>
public sealed class LoggingEmailSender : IEmailSender
{
    private readonly ILogger<LoggingEmailSender> _logger;
    public LoggingEmailSender(ILogger<LoggingEmailSender> logger) => _logger = logger;

    public Task SendPasswordResetAsync(string email, string resetToken, CancellationToken ct = default)
    {
        _logger.LogInformation("Password reset email queued for {Email}", email);
        _logger.LogDebug("[DEV ONLY] Password reset token for {Email}: {Token}", email, resetToken);
        return Task.CompletedTask;
    }

    public Task SendEmailVerificationAsync(string email, string verificationToken, CancellationToken ct = default)
    {
        _logger.LogInformation("Verification email queued for {Email}", email);
        _logger.LogDebug("[DEV ONLY] Verification token for {Email}: {Token}", email, verificationToken);
        return Task.CompletedTask;
    }
}
