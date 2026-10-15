using Zoomers.Domain.Common;
using Zoomers.Domain.Identity;
using Zoomers.Domain.Posts;

namespace Zoomers.Domain.Engagement;

/// <summary>A comment on a post, optionally a reply to another comment.</summary>
public class Comment : AuditableEntity, ISoftDeletable
{
    public Guid PostId { get; set; }
    public Post? Post { get; set; }

    public Guid AuthorId { get; set; }
    public User? Author { get; set; }

    /// <summary>Non-null for a reply. One level of threading (replies are not re-nested).</summary>
    public Guid? ParentCommentId { get; set; }
    public Comment? ParentComment { get; set; }

    public string Body { get; set; } = string.Empty;
    public int LikeCount { get; set; }

    public DateTimeOffset? DeletedAt { get; set; }
}

/// <summary>A like on a comment. Composite key (CommentId, UserId) makes it idempotent.</summary>
public class CommentLike
{
    public Guid CommentId { get; set; }
    public Comment? Comment { get; set; }

    public Guid UserId { get; set; }
    public User? User { get; set; }

    public DateTimeOffset CreatedAt { get; set; }
}
