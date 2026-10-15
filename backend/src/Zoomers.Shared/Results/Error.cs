namespace Zoomers.Shared.Results;

/// <summary>
/// The category of an <see cref="Error"/>. Controllers map each kind to an
/// HTTP status code in one place, so application/domain code never needs to
/// know about HTTP.
/// </summary>
public enum ErrorKind
{
    Validation,
    Unauthorized,
    Forbidden,
    NotFound,
    Conflict,
    RateLimited,
    Unexpected
}

/// <summary>
/// A typed, transport-agnostic error. <paramref name="Code"/> is a stable
/// machine-readable string (e.g. "auth.invalid_credentials"); Message is a
/// safe, user-facing description that must never leak secrets or internals.
/// </summary>
public sealed record Error(ErrorKind Kind, string Code, string Message)
{
    public static Error Validation(string code, string message) => new(ErrorKind.Validation, code, message);
    public static Error Unauthorized(string code, string message) => new(ErrorKind.Unauthorized, code, message);
    public static Error Forbidden(string code, string message) => new(ErrorKind.Forbidden, code, message);
    public static Error NotFound(string code, string message) => new(ErrorKind.NotFound, code, message);
    public static Error Conflict(string code, string message) => new(ErrorKind.Conflict, code, message);
    public static Error RateLimited(string code, string message) => new(ErrorKind.RateLimited, code, message);
    public static Error Unexpected(string code, string message) => new(ErrorKind.Unexpected, code, message);

    public static readonly Error None = new(ErrorKind.Unexpected, "none", string.Empty);
}
