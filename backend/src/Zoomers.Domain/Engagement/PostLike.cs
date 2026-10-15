using Zoomers.Domain.Identity;
using Zoomers.Domain.Posts;

namespace Zoomers.Domain.Engagement;

/// <summary>A like on a post. Composite key (PostId, UserId) makes likes idempotent.</summary>
public class PostLike
{
    public Guid PostId { get; set; }
    public Post? Post { get; set; }

    public Guid UserId { get; set; }
    public User? User { get; set; }

    public DateTimeOffset CreatedAt { get; set; }
}

/// <summary>A saved/bookmarked post. Composite key (UserId, PostId) makes saves idempotent.</summary>
public class SavedPost
{
    public Guid UserId { get; set; }
    public User? User { get; set; }

    public Guid PostId { get; set; }
    public Post? Post { get; set; }

    public DateTimeOffset CreatedAt { get; set; }
}
