using Zoomers.Domain.Common;
using Zoomers.Domain.Identity;

namespace Zoomers.Domain.Notifications;

public enum NotificationType
{
    Like = 0,
    Comment = 1,
    Follow = 2,
    FollowRequest = 3,
}

/// <summary>
/// An activity notification for a recipient: who did what (and on which post,
/// when relevant). Generated transactionally with the underlying action.
/// </summary>
public class Notification : Entity
{
    public Guid RecipientId { get; set; }
    public User? Recipient { get; set; }

    public Guid ActorId { get; set; }
    public User? Actor { get; set; }

    public NotificationType Type { get; set; }

    public Guid? PostId { get; set; }
    public Guid? CommentId { get; set; }

    public DateTimeOffset? ReadAt { get; set; }
}
