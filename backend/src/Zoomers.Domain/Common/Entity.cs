namespace Zoomers.Domain.Common;

/// <summary>Base for entities with a GUID primary key and a creation timestamp (UTC).</summary>
public abstract class Entity
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public DateTimeOffset CreatedAt { get; set; }
}

/// <summary>Adds a last-updated timestamp for entities that mutate.</summary>
public abstract class AuditableEntity : Entity
{
    public DateTimeOffset? UpdatedAt { get; set; }
}

/// <summary>Marks entities that support soft deletion (retention / recovery).</summary>
public interface ISoftDeletable
{
    DateTimeOffset? DeletedAt { get; set; }
}
