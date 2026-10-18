using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Zoomers.Domain.Reels;

namespace Zoomers.Infrastructure.Persistence.Configurations;

public sealed class ReelConfiguration : IEntityTypeConfiguration<Reel>
{
    public void Configure(EntityTypeBuilder<Reel> b)
    {
        b.ToTable("Reels");
        b.HasKey(r => r.Id);
        b.Property(r => r.MediaKey).IsRequired();
        b.Property(r => r.Caption).HasMaxLength(2200);

        b.HasQueryFilter(r => r.DeletedAt == null);

        b.HasOne(r => r.Author)
            .WithMany()
            .HasForeignKey(r => r.AuthorId)
            .OnDelete(DeleteBehavior.Cascade);

        // Feed/profile lookups, newest first.
        b.HasIndex(r => new { r.AuthorId, r.CreatedAt });
        b.HasIndex(r => r.CreatedAt);
    }
}

public sealed class ReelLikeConfiguration : IEntityTypeConfiguration<ReelLike>
{
    public void Configure(EntityTypeBuilder<ReelLike> b)
    {
        b.ToTable("ReelLikes");
        b.HasKey(l => new { l.ReelId, l.UserId });

        b.HasOne(l => l.Reel)
            .WithMany(r => r.Likes)
            .HasForeignKey(l => l.ReelId)
            .OnDelete(DeleteBehavior.Cascade);

        b.HasOne(l => l.User)
            .WithMany()
            .HasForeignKey(l => l.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        b.HasIndex(l => l.UserId);
    }
}
