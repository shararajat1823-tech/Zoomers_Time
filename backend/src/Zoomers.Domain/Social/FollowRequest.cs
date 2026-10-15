using Zoomers.Domain.Common;
using Zoomers.Domain.Identity;

namespace Zoomers.Domain.Social;

public enum FollowRequestStatus
{
    Pending = 0,
    Accepted = 1,
    Rejected = 2,
    Cancelled = 3
}

/// <summary>
/// A pending request to follow a private account. A pending request grants NO
/// access to protected content — only an accepted request creates a
/// <see cref="Follow"/> edge.
/// </summary>
public class FollowRequest : Entity
{
    public Guid RequesterId { get; set; }
    public User? Requester { get; set; }

    public Guid TargetId { get; set; }
    public User? Target { get; set; }

    public FollowRequestStatus Status { get; set; } = FollowRequestStatus.Pending;
    public DateTimeOffset? DecidedAt { get; set; }

    public bool IsPending => Status == FollowRequestStatus.Pending;
}
