namespace Zoomers.Application.Identity;

/// <summary>Non-secret auth tuning knobs (bound from configuration).</summary>
public sealed class AuthOptions
{
    public const string SectionName = "Auth";

    /// <summary>How long a password-reset token remains usable.</summary>
    public TimeSpan PasswordResetTokenLifetime { get; set; } = TimeSpan.FromHours(1);
}
