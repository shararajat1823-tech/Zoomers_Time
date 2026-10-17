using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Zoomers.Domain.Stories;

namespace Zoomers.Infrastructure.Persistence.Configurations;

public sealed class StoryConfiguration : IEntityTypeConfiguration<Story>
{
    public void Configure(EntityTypeBuilder<Story> b)
    {
        b.ToTable("Stories");
        b.HasKey(s => s.Id);
        b.Property(s => s.MediaKey).IsRequired();

        b.HasQueryFilter(s => s.DeletedAt == null);

        b.HasOne(s => s.Author)
            .WithMany()
            .HasForeignKey(s => s.AuthorId)
            .OnDelete(DeleteBehavior.Cascade);

        // Active-stories lookups by author and expiry.
        b.HasIndex(s => new { s.AuthorId, s.ExpiresAt });
    }
}

public sealed class StoryViewConfiguration : IEntityTypeConfiguration<StoryView>
{
    public void Configure(EntityTypeBuilder<StoryView> b)
    {
        b.ToTable("StoryViews");
        b.HasKey(v => v.Id);

        b.HasOne(v => v.Story)
            .WithMany(s => s.Views)
            .HasForeignKey(v => v.StoryId)
            .OnDelete(DeleteBehavior.Cascade);

        b.HasOne(v => v.Viewer)
            .WithMany()
            .HasForeignKey(v => v.ViewerId)
            .OnDelete(DeleteBehavior.Cascade);

        // One view record per (story, viewer).
        b.HasIndex(v => new { v.StoryId, v.ViewerId }).IsUnique();
    }
}
