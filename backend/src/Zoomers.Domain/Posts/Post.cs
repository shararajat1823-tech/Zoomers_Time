using Zoomers.Domain.Common;
using Zoomers.Domain.Identity;

namespace Zoomers.Domain.Posts;

public enum PostType { Image = 0, Video = 1, Carousel = 2 }

/// <summary>
/// Post visibility. Public is visible to anyone (not blocked); Followers is
/// visible to accepted followers; Private is visible only to the author. The
/// author's account privacy also applies (a private account's public posts are
/// still only visible to accepted followers).
/// </summary>
public enum PostVisibility { Public = 0, Followers = 1, Private = 2 }

/// <summary>A feed post with one or more media items. Media bytes live in object storage.</summary>
public class Post : AuditableEntity, ISoftDeletable
{
    public Guid AuthorId { get; set; }
    public User? Author { get; set; }

    public PostType Type { get; set; }
    public PostVisibility Visibility { get; set; } = PostVisibility.Public;

    public string? Caption { get; set; }
    public string? LocationName { get; set; }
    public string? AccessibilityText { get; set; }

    public int LikeCount { get; set; }
    public int CommentCount { get; set; }

    public DateTimeOffset? DeletedAt { get; set; }

    public ICollection<PostMedia> Media { get; set; } = new List<PostMedia>();
}
