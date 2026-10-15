using Zoomers.Domain.Common;

namespace Zoomers.Domain.Posts;

public enum MediaType { Image = 0, Video = 1 }

/// <summary>Processing state of a media object (thumbnails/transcode happen asynchronously).</summary>
public enum MediaStatus { Pending = 0, Ready = 1, Failed = 2 }

/// <summary>One media item belonging to a post. Stores metadata + storage keys, never bytes.</summary>
public class PostMedia : Entity
{
    public Guid PostId { get; set; }
    public Post? Post { get; set; }

    public int OrderIndex { get; set; }
    public MediaType MediaType { get; set; }
    public MediaStatus Status { get; set; } = MediaStatus.Ready;

    public string StorageKey { get; set; } = string.Empty;
    public string? ThumbnailKey { get; set; }

    public int? Width { get; set; }
    public int? Height { get; set; }
    public int? DurationMs { get; set; }
}
