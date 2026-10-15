namespace Zoomers.Application.Abstractions;

/// <summary>A short-lived, authorized URL for uploading or downloading an object.</summary>
public sealed record SignedUrl(string Url, DateTimeOffset ExpiresAt);

/// <summary>
/// Abstraction over object storage for media. The first implementation stores
/// files on local disk for development; production swaps in an S3-compatible
/// provider via configuration. Media bytes never live in the relational DB and
/// private media is never served from a public, guessable URL.
/// </summary>
public interface IMediaStorage
{
    /// <summary>Creates a scoped, short-lived URL the client uses to upload directly.</summary>
    Task<SignedUrl> CreateUploadUrlAsync(string storageKey, string contentType, long maxBytes, CancellationToken ct = default);

    /// <summary>Creates a scoped, short-lived URL for an authorized download.</summary>
    Task<SignedUrl> CreateDownloadUrlAsync(string storageKey, CancellationToken ct = default);

    /// <summary>Verifies an uploaded object exists and returns its size, for server-side validation.</summary>
    Task<long?> TryGetObjectSizeAsync(string storageKey, CancellationToken ct = default);

    /// <summary>Stores an object server-side (used by the dev storage endpoint; S3 SDKs support this too).</summary>
    Task SaveAsync(string storageKey, Stream content, string contentType, CancellationToken ct = default);

    /// <summary>Opens an object for reading, or null if it does not exist.</summary>
    Task<Stream?> OpenReadAsync(string storageKey, CancellationToken ct = default);

    Task DeleteAsync(string storageKey, CancellationToken ct = default);
}
