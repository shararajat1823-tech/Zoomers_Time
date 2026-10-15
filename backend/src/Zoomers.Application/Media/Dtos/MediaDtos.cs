using Zoomers.Domain.Media;

namespace Zoomers.Application.Media.Dtos;

public sealed record CreateUploadSessionRequest(string Purpose, string ContentType);

/// <summary>Returned when an upload session is created: where to upload and how.</summary>
public sealed record UploadSessionResponse(
    Guid SessionId,
    string StorageKey,
    string UploadUrl,
    DateTimeOffset ExpiresAt,
    UploadStatus Status,
    long MaxBytes);

/// <summary>An authorized media object ready to stream to the client.</summary>
public sealed record MediaDownload(Stream Content, string ContentType);
