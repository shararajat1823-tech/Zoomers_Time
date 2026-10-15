using Microsoft.AspNetCore.Mvc;
using Zoomers.Shared.Results;

namespace Zoomers.Api.Common;

/// <summary>
/// Central mapping from application <see cref="Error"/> kinds to HTTP responses
/// (RFC 7807 ProblemDetails). Controllers stay thin and never choose status
/// codes themselves, so error semantics are consistent across the API.
/// </summary>
public static class ApiResults
{
    public static int StatusFor(ErrorKind kind) => kind switch
    {
        ErrorKind.Validation => StatusCodes.Status400BadRequest,
        ErrorKind.Unauthorized => StatusCodes.Status401Unauthorized,
        ErrorKind.Forbidden => StatusCodes.Status403Forbidden,
        ErrorKind.NotFound => StatusCodes.Status404NotFound,
        ErrorKind.Conflict => StatusCodes.Status409Conflict,
        ErrorKind.RateLimited => StatusCodes.Status429TooManyRequests,
        _ => StatusCodes.Status500InternalServerError
    };

    public static ObjectResult Problem(Error error)
    {
        var status = StatusFor(error.Kind);
        var problem = new ProblemDetails
        {
            Status = status,
            Title = error.Kind.ToString(),
            Detail = error.Message,
            Extensions = { ["code"] = error.Code }
        };
        return new ObjectResult(problem) { StatusCode = status };
    }

    /// <summary>Maps a value result to 200 OK or a ProblemDetails error.</summary>
    public static IActionResult ToResponse<T>(this Result<T> result) =>
        result.IsSuccess ? new OkObjectResult(result.Value) : Problem(result.Error!);

    /// <summary>Maps a value result to a custom success status (e.g. 201).</summary>
    public static IActionResult ToResponse<T>(this Result<T> result, int successStatus) =>
        result.IsSuccess
            ? new ObjectResult(result.Value) { StatusCode = successStatus }
            : Problem(result.Error!);

    /// <summary>Maps a no-value result to 204 No Content or a ProblemDetails error.</summary>
    public static IActionResult ToResponse(this Result result) =>
        result.IsSuccess ? new NoContentResult() : Problem(result.Error!);
}
