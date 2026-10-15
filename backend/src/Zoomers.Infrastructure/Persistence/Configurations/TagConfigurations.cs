using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Zoomers.Domain.Tags;

namespace Zoomers.Infrastructure.Persistence.Configurations;

public sealed class HashtagConfiguration : IEntityTypeConfiguration<Hashtag>
{
    public void Configure(EntityTypeBuilder<Hashtag> b)
    {
        b.ToTable("Hashtags");
        b.HasKey(h => h.Id);

        // Stored as normalized lowercase text so the plain-text unique index is
        // effectively case-insensitive and trigram search (text-only) applies.
        b.Property(h => h.Tag).HasMaxLength(100).IsRequired();

        // Two distinct indexes on the same column need explicit names.
        b.HasIndex(h => h.Tag, "UQ_Hashtags_Tag").IsUnique();
        b.HasIndex(h => h.Tag, "IX_Hashtags_Tag_Trgm").HasMethod("gin").HasOperators("gin_trgm_ops");
    }
}

public sealed class PostHashtagConfiguration : IEntityTypeConfiguration<PostHashtag>
{
    public void Configure(EntityTypeBuilder<PostHashtag> b)
    {
        b.ToTable("PostHashtags");
        b.HasKey(x => new { x.PostId, x.HashtagId });

        b.HasIndex(x => x.HashtagId).HasDatabaseName("IX_PostHashtags_HashtagId");

        b.HasOne(x => x.Post).WithMany().HasForeignKey(x => x.PostId).OnDelete(DeleteBehavior.Cascade);
        b.HasOne(x => x.Hashtag).WithMany().HasForeignKey(x => x.HashtagId).OnDelete(DeleteBehavior.Cascade);

        b.HasQueryFilter(x => x.Post!.DeletedAt == null && x.Post.Author!.DeletedAt == null);
    }
}

public sealed class MentionConfiguration : IEntityTypeConfiguration<Mention>
{
    public void Configure(EntityTypeBuilder<Mention> b)
    {
        b.ToTable("Mentions");
        b.HasKey(m => m.Id);

        b.Property(m => m.SourceType).HasConversion<int>();
        b.HasIndex(m => new { m.SourceType, m.SourceId }).HasDatabaseName("IX_Mentions_Source");
        b.HasIndex(m => m.MentionedUserId).HasDatabaseName("IX_Mentions_MentionedUserId");

        b.HasOne(m => m.MentionedUser).WithMany()
            .HasForeignKey(m => m.MentionedUserId)
            .OnDelete(DeleteBehavior.Cascade);

        b.HasQueryFilter(m => m.MentionedUser!.DeletedAt == null);
    }
}
