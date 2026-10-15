namespace Zoomers.Application.Abstractions;

/// <summary>
/// Abstraction over the system clock so time-dependent logic (token expiry,
/// story expiry) is testable. Always returns UTC.
/// </summary>
public interface IClock
{
    DateTimeOffset UtcNow { get; }
}
