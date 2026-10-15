using Asp.Versioning;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Zoomers.Api.Common;
using Zoomers.Application.Media;
using Zoomers.Application.Media.Dtos;

namespace Zoomers.Api.Controllers;

[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/media")]
[Authorize]
public sealed class MediaController : ApiControllerBase
{
    private readonly IMediaService _media;
    public MediaController(IMediaService media) => _media = media;

    /// <summary>Creates a scoped upload session and returns an authorized upload URL.</summary>
    [HttpPost("upload-sessions")]
    [ProducesResponseType(typeof(UploadSessionResponse), StatusCodes.Status201Created)]
    public async Task<IActionResult> CreateSession(CreateUploadSessionRequest request, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _media.CreateUploadSessionAsync(userId, request, ct)).ToResponse(StatusCodes.Status201Created);
    }

    /// <summary>Verifies the uploaded object and marks the session complete.</summary>
    [HttpPost("upload-sessions/{sessionId:guid}/complete")]
    [ProducesResponseType(typeof(UploadSessionResponse), StatusCodes.Status200OK)]
    public async Task<IActionResult> CompleteSession(Guid sessionId, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _media.CompleteUploadSessionAsync(userId, sessionId, ct)).ToResponse();
    }

    /// <summary>
    /// Development storage endpoint: direct upload of bytes into a pending session.
    /// In production the client PUTs to a presigned object-storage URL and this is unused.
    /// </summary>
    [HttpPut("local/{*storageKey}")]
    [RequestSizeLimit(MediaRequestLimits.MaxUploadBytes)]
    public async Task<IActionResult> Upload(string storageKey, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        var contentType = Request.ContentType ?? "application/octet-stream";
        return (await _media.AcceptUploadAsync(userId, storageKey, Request.Body, contentType, ct)).ToResponse();
    }

    /// <summary>Authorized media download (private media is not served to unauthorized viewers).</summary>
    [HttpGet("local/{*storageKey}")]
    public async Task<IActionResult> Download(string storageKey, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        var result = await _media.OpenAuthorizedAsync(userId, storageKey, ct);
        if (result.IsFailure)
            return ApiResults.Problem(result.Error!);

        var download = result.Value;
        return File(download.Content, download.ContentType);
    }
}

internal static class MediaRequestLimits
{
    public const long MaxUploadBytes = 300L * 1024 * 1024; // matches MediaPolicy.MaxVideoBytes
}
