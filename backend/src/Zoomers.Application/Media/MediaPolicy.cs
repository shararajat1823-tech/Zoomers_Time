using Zoomers.Domain.Media;
using Zoomers.Domain.Posts;

namespace Zoomers.Application.Media;

/// <summary>
/// Allowed content types and size limits, validated on both client and server.
/// Kept in one place so limits are consistent across endpoints.
/// </summary>
public static class MediaPolicy
{
    public const long MaxImageBytes = 15L * 1024 * 1024;   // 15 MB
    public const long MaxVideoBytes = 300L * 1024 * 1024;  // 300 MB

    public static readonly IReadOnlySet<string> ImageTypes =
        new HashSet<string>(StringComparer.OrdinalIgnoreCase) { "image/jpeg", "image/png", "image/webp", "image/heic" };

    public static readonly IReadOnlySet<string> VideoTypes =
        new HashSet<string>(StringComparer.OrdinalIgnoreCase) { "video/mp4", "video/quicktime", "video/webm" };

    public static bool IsAllowed(string contentType) =>
        ImageTypes.Contains(contentType) || VideoTypes.Contains(contentType);

    public static MediaType MediaTypeFor(string contentType) =>
        VideoTypes.Contains(contentType) ? MediaType.Video : MediaType.Image;

    public static long MaxBytesFor(string contentType) =>
        VideoTypes.Contains(contentType) ? MaxVideoBytes : MaxImageBytes;

    public const int MaxCarouselItems = 10;
}
