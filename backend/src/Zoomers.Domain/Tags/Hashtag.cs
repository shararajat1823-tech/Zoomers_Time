using Zoomers.Domain.Common;
using Zoomers.Domain.Posts;

namespace Zoomers.Domain.Tags;

/// <summary>A normalized hashtag (stored without the leading '#', lowercased).</summary>
public class Hashtag : Entity
{
    public string Tag { get; set; } = string.Empty;
    public int UsageCount { get; set; }
}

/// <summary>Link between a post and a hashtag. Composite key prevents duplicates.</summary>
public class PostHashtag
{
    public Guid PostId { get; set; }
    public Post? Post { get; set; }

    public Guid HashtagId { get; set; }
    public Hashtag? Hashtag { get; set; }
}
