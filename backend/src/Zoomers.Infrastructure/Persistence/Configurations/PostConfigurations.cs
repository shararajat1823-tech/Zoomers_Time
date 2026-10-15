using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Zoomers.Domain.Posts;

namespace Zoomers.Infrastructure.Persistence.Configurations;

public sealed class PostConfiguration : IEntityTypeConfiguration<Post>
{
    public void Configure(EntityTypeBuilder<Post> b)
    {
        b.ToTable("Posts");
        b.HasKey(p => p.Id);

        b.Property(p => p.Type).HasConversion<int>();
        b.Property(p => p.Visibility).HasConversion<int>();
        b.Property(p => p.Caption).HasMaxLength(2200);
        // Trigram index for caption search.
        b.HasIndex(p => p.Caption).HasDatabaseName("IX_Posts_Caption_Trgm")
            .HasMethod("gin").HasOperators("gin_trgm_ops");
        b.Property(p => p.LocationName).HasMaxLength(100);
        b.Property(p => p.AccessibilityText).HasMaxLength(1000);

        // Profile grid + recency feeds.
        b.HasIndex(p => new { p.AuthorId, p.CreatedAt }).HasDatabaseName("IX_Posts_Author_CreatedAt");
        b.HasIndex(p => p.CreatedAt).HasDatabaseName("IX_Posts_CreatedAt");

        b.HasQueryFilter(p => p.DeletedAt == null && p.Author!.DeletedAt == null);

        b.HasOne(p => p.Author).WithMany()
            .HasForeignKey(p => p.AuthorId)
            .OnDelete(DeleteBehavior.Cascade);

        b.HasMany(p => p.Media).WithOne(m => m.Post!)
            .HasForeignKey(m => m.PostId)
            .OnDelete(DeleteBehavior.Cascade);
    }
}

public sealed class PostMediaConfiguration : IEntityTypeConfiguration<PostMedia>
{
    public void Configure(EntityTypeBuilder<PostMedia> b)
    {
        b.ToTable("PostMedia");
        b.HasKey(m => m.Id);

        b.Property(m => m.MediaType).HasConversion<int>();
        b.Property(m => m.Status).HasConversion<int>();
        b.Property(m => m.StorageKey).HasMaxLength(512).IsRequired();
        b.Property(m => m.ThumbnailKey).HasMaxLength(512);

        b.HasIndex(m => new { m.PostId, m.OrderIndex }).HasDatabaseName("IX_PostMedia_Post_Order");

        b.HasQueryFilter(m => m.Post!.DeletedAt == null && m.Post.Author!.DeletedAt == null);
    }
}
