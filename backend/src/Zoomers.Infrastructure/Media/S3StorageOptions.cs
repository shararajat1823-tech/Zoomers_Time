namespace Zoomers.Infrastructure.Media;

/// <summary>
/// Configuration for S3-compatible object storage (AWS S3, Cloudflare R2,
/// Backblaze B2, MinIO, …). Supplied via environment/secret manager; when
/// <see cref="Enabled"/> is false the app falls back to local disk storage.
/// </summary>
public sealed class S3StorageOptions
{
    public const string SectionName = "Media:S3";

    /// <summary>Endpoint, e.g. https://&lt;accountid&gt;.r2.cloudflarestorage.com for Cloudflare R2.</summary>
    public string ServiceUrl { get; set; } = string.Empty;

    /// <summary>Signing region. R2 uses "auto".</summary>
    public string Region { get; set; } = "auto";

    public string AccessKey { get; set; } = string.Empty;
    public string SecretKey { get; set; } = string.Empty;
    public string Bucket { get; set; } = string.Empty;

    /// <summary>Path-style addressing (bucket in the path). Safest for custom endpoints like R2.</summary>
    public bool ForcePathStyle { get; set; } = true;

    /// <summary>Lifetime of a presigned upload (PUT) URL.</summary>
    public int UploadUrlMinutes { get; set; } = 15;

    /// <summary>Lifetime of a presigned download (GET) URL returned in feeds.</summary>
    public int DownloadUrlMinutes { get; set; } = 60;

    /// <summary>True when enough is configured to use S3-compatible storage.</summary>
    public bool Enabled =>
        !string.IsNullOrWhiteSpace(ServiceUrl) &&
        !string.IsNullOrWhiteSpace(Bucket) &&
        !string.IsNullOrWhiteSpace(AccessKey) &&
        !string.IsNullOrWhiteSpace(SecretKey);
}
