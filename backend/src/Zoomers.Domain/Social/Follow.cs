using Zoomers.Domain.Identity;

namespace Zoomers.Domain.Social;

/// <summary>
/// A directed following edge: <see cref="FollowerId"/> follows
/// <see cref="FolloweeId"/>. The composite key (FollowerId, FolloweeId) makes
/// duplicate relationships impossible at the database level.
/// </summary>
public class Follow
{
    public Guid FollowerId { get; set; }
    public User? Follower { get; set; }

    public Guid FolloweeId { get; set; }
    public User? Followee { get; set; }

    public DateTimeOffset CreatedAt { get; set; }
}
