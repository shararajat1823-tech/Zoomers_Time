using Zoomers.Domain.Common;
using Zoomers.Domain.Identity;

namespace Zoomers.Domain.Messaging;

/// <summary>
/// A 1:1 direct-message thread between two users. The pair is stored in a
/// canonical order (<see cref="ParticipantAId"/> is always the smaller GUID,
/// <see cref="ParticipantBId"/> the larger) so a unique index guarantees exactly
/// one conversation per pair regardless of who starts it.
/// </summary>
public class Conversation : Entity
{
    public Guid ParticipantAId { get; set; }
    public User? ParticipantA { get; set; }

    public Guid ParticipantBId { get; set; }
    public User? ParticipantB { get; set; }

    /// <summary>Sort key for the inbox; seeded to CreatedAt and bumped on every send.</summary>
    public DateTimeOffset LastMessageAt { get; set; }

    /// <summary>Short preview of the most recent message for the inbox list.</summary>
    public string? LastMessagePreview { get; set; }

    public ICollection<Message> Messages { get; set; } = new List<Message>();

    /// <summary>Orders two user ids canonically so a pair maps to one row.</summary>
    public static (Guid a, Guid b) Pair(Guid x, Guid y) =>
        x.CompareTo(y) <= 0 ? (x, y) : (y, x);

    public bool HasParticipant(Guid userId) => ParticipantAId == userId || ParticipantBId == userId;

    public Guid OtherParticipant(Guid userId) => ParticipantAId == userId ? ParticipantBId : ParticipantAId;
}

/// <summary>A single direct message within a <see cref="Conversation"/>.</summary>
public class Message : Entity, ISoftDeletable
{
    public Guid ConversationId { get; set; }
    public Conversation? Conversation { get; set; }

    public Guid SenderId { get; set; }
    public User? Sender { get; set; }

    public string Body { get; set; } = string.Empty;

    /// <summary>When the recipient read this message; null until then.</summary>
    public DateTimeOffset? ReadAt { get; set; }

    public DateTimeOffset? DeletedAt { get; set; }
}
