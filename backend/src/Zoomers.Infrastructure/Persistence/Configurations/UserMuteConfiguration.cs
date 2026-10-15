using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Zoomers.Domain.Social;

namespace Zoomers.Infrastructure.Persistence.Configurations;

public sealed class UserMuteConfiguration : IEntityTypeConfiguration<UserMute>
{
    public void Configure(EntityTypeBuilder<UserMute> b)
    {
        b.ToTable("UserMutes", t =>
            t.HasCheckConstraint("CK_UserMutes_NoSelf", "\"MuterId\" <> \"MutedId\""));

        b.HasKey(x => new { x.MuterId, x.MutedId });
        b.HasIndex(x => x.MutedId).HasDatabaseName("IX_UserMutes_MutedId");

        b.HasOne(x => x.Muter).WithMany().HasForeignKey(x => x.MuterId).OnDelete(DeleteBehavior.Cascade);
        b.HasOne(x => x.Muted).WithMany().HasForeignKey(x => x.MutedId).OnDelete(DeleteBehavior.Cascade);

        b.HasQueryFilter(x => x.Muter!.DeletedAt == null && x.Muted!.DeletedAt == null);
    }
}
