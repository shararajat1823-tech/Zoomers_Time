using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Zoomers.Application.Abstractions;

namespace Zoomers.Infrastructure.Media;

public sealed class LocalMediaOptions
{
    public const string SectionName = "Media:Local";
    public string RootPath { get; set; } = Path.Combine(Path.GetTempPath(), "zoomers-media");
    /// <summary>Base URL the dev media endpoint is served from.</summary>
    public string PublicBaseUrl { get; set; } = "/api/v1/media/local";
    public int SignedUrlMinutes { get; set; } = 15;
}

/// <summary>
/// Development-only media storage backed by the local filesystem. It implements
/// real disk operations (size check, delete) and produces dev URLs that the
/// media endpoints serve. Production swaps in an S3-compatible implementation
/// via configuration — no caller changes. Never used to permanently store
/// production media on the API host.
/// </summary>
public sealed class LocalFileMediaStorage : IMediaStorage
{
    private readonly LocalMediaOptions _options;
    private readonly IClock _clock;
    private readonly ILogger<LocalFileMediaStorage> _logger;

    public LocalFileMediaStorage(IOptions<LocalMediaOptions> options, IClock clock, ILogger<LocalFileMediaStorage> logger)
    {
        _options = options.Value;
        _clock = clock;
        _logger = logger;
        Directory.CreateDirectory(_options.RootPath);
    }

    public Task<SignedUrl> CreateUploadUrlAsync(string storageKey, string contentType, long maxBytes, CancellationToken ct = default)
    {
        var url = $"{_options.PublicBaseUrl.TrimEnd('/')}/{Uri.EscapeDataString(storageKey)}";
        _logger.LogDebug("Issued local upload URL for {StorageKey}", storageKey);
        return Task.FromResult(new SignedUrl(url, _clock.UtcNow.AddMinutes(_options.SignedUrlMinutes)));
    }

    public Task<SignedUrl> CreateDownloadUrlAsync(string storageKey, CancellationToken ct = default)
    {
        var url = $"{_options.PublicBaseUrl.TrimEnd('/')}/{Uri.EscapeDataString(storageKey)}";
        return Task.FromResult(new SignedUrl(url, _clock.UtcNow.AddMinutes(_options.SignedUrlMinutes)));
    }

    public Task<long?> TryGetObjectSizeAsync(string storageKey, CancellationToken ct = default)
    {
        var path = ResolvePath(storageKey);
        return Task.FromResult<long?>(File.Exists(path) ? new FileInfo(path).Length : null);
    }

    public Task DeleteAsync(string storageKey, CancellationToken ct = default)
    {
        var path = ResolvePath(storageKey);
        if (File.Exists(path)) File.Delete(path);
        return Task.CompletedTask;
    }

    private string ResolvePath(string storageKey)
    {
        // Prevent path traversal outside the media root.
        var safe = storageKey.Replace("..", string.Empty).TrimStart('/', '\\');
        var full = Path.GetFullPath(Path.Combine(_options.RootPath, safe));
        if (!full.StartsWith(Path.GetFullPath(_options.RootPath), StringComparison.Ordinal))
            throw new UnauthorizedAccessException("Invalid storage key.");
        return full;
    }
}
