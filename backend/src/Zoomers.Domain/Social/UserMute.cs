using Zoomers.Domain.Identity;

namespace Zoomers.Domain.Social;

/// <summary>
/// A directed mute: <see cref="MuterId"/> mutes <see cref="MutedId"/>. Unlike a
/// block, the muted user is not notified and can still interact; their posts
/// and/or stories are simply hidden from the muter's feeds.
/// </summary>
public class UserMute
{
    public Guid MuterId { get; set; }
    public User? Muter { get; set; }

    public Guid MutedId { get; set; }
    public User? Muted { get; set; }

    public bool MutePosts { get; set; } = true;
    public bool MuteStories { get; set; } = true;

    public DateTimeOffset CreatedAt { get; set; }
}
