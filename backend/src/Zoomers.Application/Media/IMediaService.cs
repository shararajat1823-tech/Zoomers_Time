using Zoomers.Application.Media.Dtos;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Media;

public interface IMediaService
{
    /// <summary>Creates a scoped upload session + authorized upload URL for the caller.</summary>
    Task<Result<UploadSessionResponse>> CreateUploadSessionAsync(Guid userId, CreateUploadSessionRequest request, CancellationToken ct = default);

    /// <summary>Verifies the uploaded object server-side and marks the session completed.</summary>
    Task<Result<UploadSessionResponse>> CompleteUploadSessionAsync(Guid userId, Guid sessionId, CancellationToken ct = default);

    /// <summary>
    /// Dev storage endpoint: accept a direct upload into a pending session owned by the
    /// caller (in production the client PUTs to a presigned URL and this is unused).
    /// </summary>
    Task<Result> AcceptUploadAsync(Guid userId, string storageKey, Stream content, string contentType, CancellationToken ct = default);

    /// <summary>Opens a media object for download only if the caller is authorized to view it.</summary>
    Task<Result<MediaDownload>> OpenAuthorizedAsync(Guid viewerId, string storageKey, CancellationToken ct = default);
}
