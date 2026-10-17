using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Zoomers.Domain.Messaging;

namespace Zoomers.Infrastructure.Persistence.Configurations;

public sealed class ConversationConfiguration : IEntityTypeConfiguration<Conversation>
{
    public void Configure(EntityTypeBuilder<Conversation> b)
    {
        b.ToTable("Conversations");
        b.HasKey(c => c.Id);
        b.Property(c => c.LastMessagePreview).HasMaxLength(120);

        b.HasOne(c => c.ParticipantA)
            .WithMany()
            .HasForeignKey(c => c.ParticipantAId)
            .OnDelete(DeleteBehavior.Restrict);

        b.HasOne(c => c.ParticipantB)
            .WithMany()
            .HasForeignKey(c => c.ParticipantBId)
            .OnDelete(DeleteBehavior.Restrict);

        // Exactly one conversation per canonical participant pair.
        b.HasIndex(c => new { c.ParticipantAId, c.ParticipantBId }).IsUnique();

        // Inbox ordering and per-user lookups.
        b.HasIndex(c => c.ParticipantAId);
        b.HasIndex(c => c.ParticipantBId);
        b.HasIndex(c => c.LastMessageAt);
    }
}

public sealed class MessageConfiguration : IEntityTypeConfiguration<Message>
{
    public void Configure(EntityTypeBuilder<Message> b)
    {
        b.ToTable("Messages");
        b.HasKey(m => m.Id);
        b.Property(m => m.Body).IsRequired().HasMaxLength(2000);

        b.HasQueryFilter(m => m.DeletedAt == null);

        b.HasOne(m => m.Conversation)
            .WithMany(c => c.Messages)
            .HasForeignKey(m => m.ConversationId)
            .OnDelete(DeleteBehavior.Cascade);

        b.HasOne(m => m.Sender)
            .WithMany()
            .HasForeignKey(m => m.SenderId)
            .OnDelete(DeleteBehavior.Restrict);

        // Thread paging (newest first) and unread scans.
        b.HasIndex(m => new { m.ConversationId, m.CreatedAt });
        b.HasIndex(m => new { m.ConversationId, m.SenderId, m.ReadAt });
    }
}
