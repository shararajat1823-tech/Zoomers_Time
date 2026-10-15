namespace Zoomers.Application.Abstractions;

/// <summary>
/// Sends transactional emails (verification, password reset). The first
/// implementation logs the message for development; production swaps in a real
/// provider. Reset/verification links carry single-use, short-lived tokens.
/// </summary>
public interface IEmailSender
{
    Task SendPasswordResetAsync(string email, string resetToken, CancellationToken ct = default);
    Task SendEmailVerificationAsync(string email, string verificationToken, CancellationToken ct = default);
}
