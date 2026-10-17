using Zoomers.Domain.Common;
using Zoomers.Domain.Identity;

namespace Zoomers.Domain.Stories;

/// <summary>
/// A 24-hour photo story. Media bytes live in object storage (MediaKey); the
/// story is "active" while <see cref="ExpiresAt"/> is in the future.
/// </summary>
public class Story : Entity, ISoftDeletable
{
    public Guid AuthorId { get; set; }
    public User? Author { get; set; }

    public string MediaKey { get; set; } = string.Empty;

    public DateTimeOffset ExpiresAt { get; set; }
    public DateTimeOffset? DeletedAt { get; set; }

    public ICollection<StoryView> Views { get; set; } = new List<StoryView>();
}

/// <summary>Records that a viewer has seen a story (drives the seen/unseen ring).</summary>
public class StoryView : Entity
{
    public Guid StoryId { get; set; }
    public Story? Story { get; set; }

    public Guid ViewerId { get; set; }
    public User? Viewer { get; set; }
}
