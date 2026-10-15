namespace Zoomers.Api.Common;

/// <summary>Configurable rate-limit knobs so limits can be tuned per environment.</summary>
public sealed class RateLimitingOptions
{
    public const string SectionName = "RateLimiting";

    /// <summary>Strict per-IP limit for auth endpoints (login/register/refresh/reset).</summary>
    public int AuthPermitLimit { get; set; } = 10;
    public int AuthWindowSeconds { get; set; } = 60;

    /// <summary>Global per-IP limit for the rest of the API.</summary>
    public int GlobalPermitLimit { get; set; } = 200;
    public int GlobalWindowSeconds { get; set; } = 60;
}
