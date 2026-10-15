using Zoomers.Domain.Common;
using Zoomers.Domain.Identity;

namespace Zoomers.Domain.Tags;

public enum MentionSource { Post = 0, Comment = 1, Story = 2 }

/// <summary>A resolved @mention of a user from a post/comment/story.</summary>
public class Mention : Entity
{
    public MentionSource SourceType { get; set; }
    public Guid SourceId { get; set; }

    public Guid MentionedUserId { get; set; }
    public User? MentionedUser { get; set; }
}
