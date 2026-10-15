using Zoomers.Domain.Identity;

namespace Zoomers.Domain.Social;

/// <summary>
/// A directed block: <see cref="BlockerId"/> has blocked
/// <see cref="BlockedId"/>. Blocks are consulted everywhere for visibility and
/// interaction (a block makes the two users mutually invisible for interactions
/// and removes any existing follow edges in either direction).
/// </summary>
public class UserBlock
{
    public Guid BlockerId { get; set; }
    public User? Blocker { get; set; }

    public Guid BlockedId { get; set; }
    public User? Blocked { get; set; }

    public DateTimeOffset CreatedAt { get; set; }
}
