using Zoomers.Domain.Common;
using Zoomers.Domain.Identity;

namespace Zoomers.Domain.Reels;

/// <summary>
/// A short vertical video. The video object lives in storage (MediaKey); an
/// optional poster frame is ThumbnailKey. LikeCount is a denormalized cache kept
/// in step with ReelLike rows.
/// </summary>
public class Reel : Entity, ISoftDeletable
{
    public Guid AuthorId { get; set; }
    public User? Author { get; set; }

    public string MediaKey { get; set; } = string.Empty;
    public string? ThumbnailKey { get; set; }

    public string? Caption { get; set; }

    public int Width { get; set; }
    public int Height { get; set; }
    public int DurationMs { get; set; }

    public int LikeCount { get; set; }

    public DateTimeOffset? DeletedAt { get; set; }

    public ICollection<ReelLike> Likes { get; set; } = new List<ReelLike>();
}

/// <summary>A like on a reel. Composite key (ReelId, UserId) makes likes idempotent.</summary>
public class ReelLike
{
    public Guid ReelId { get; set; }
    public Reel? Reel { get; set; }

    public Guid UserId { get; set; }
    public User? User { get; set; }

    public DateTimeOffset CreatedAt { get; set; }
}
