using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Zoomers.Domain.Social;

namespace Zoomers.Infrastructure.Persistence.Configurations;

public sealed class FollowConfiguration : IEntityTypeConfiguration<Follow>
{
    public void Configure(EntityTypeBuilder<Follow> b)
    {
        b.ToTable("Follows", t =>
            t.HasCheckConstraint("CK_Follows_NoSelfFollow", "\"FollowerId\" <> \"FolloweeId\""));

        // Composite PK prevents duplicate follow relationships at the DB level.
        b.HasKey(f => new { f.FollowerId, f.FolloweeId });

        b.HasIndex(f => f.FollowerId).HasDatabaseName("IX_Follows_FollowerId");
        b.HasIndex(f => f.FolloweeId).HasDatabaseName("IX_Follows_FolloweeId");

        b.HasOne(f => f.Follower).WithMany()
            .HasForeignKey(f => f.FollowerId)
            .OnDelete(DeleteBehavior.Cascade);

        b.HasOne(f => f.Followee).WithMany()
            .HasForeignKey(f => f.FolloweeId)
            .OnDelete(DeleteBehavior.Cascade);

        // Hide edges involving a soft-deleted user (matches the User filter).
        b.HasQueryFilter(f => f.Follower!.DeletedAt == null && f.Followee!.DeletedAt == null);
    }
}

public sealed class FollowRequestConfiguration : IEntityTypeConfiguration<FollowRequest>
{
    public void Configure(EntityTypeBuilder<FollowRequest> b)
    {
        b.ToTable("FollowRequests", t =>
            t.HasCheckConstraint("CK_FollowRequests_NoSelf", "\"RequesterId\" <> \"TargetId\""));

        b.HasKey(r => r.Id);
        b.Property(r => r.Status).HasConversion<int>();

        // At most one pending request per (requester, target).
        b.HasIndex(r => new { r.RequesterId, r.TargetId })
            .IsUnique()
            .HasFilter("\"Status\" = 0")
            .HasDatabaseName("UQ_FollowRequests_Pending");

        b.HasIndex(r => new { r.TargetId, r.Status }).HasDatabaseName("IX_FollowRequests_Target_Status");

        b.HasOne(r => r.Requester).WithMany()
            .HasForeignKey(r => r.RequesterId)
            .OnDelete(DeleteBehavior.Cascade);

        b.HasOne(r => r.Target).WithMany()
            .HasForeignKey(r => r.TargetId)
            .OnDelete(DeleteBehavior.Cascade);

        b.HasQueryFilter(r => r.Requester!.DeletedAt == null && r.Target!.DeletedAt == null);
    }
}

public sealed class UserBlockConfiguration : IEntityTypeConfiguration<UserBlock>
{
    public void Configure(EntityTypeBuilder<UserBlock> b)
    {
        b.ToTable("UserBlocks", t =>
            t.HasCheckConstraint("CK_UserBlocks_NoSelf", "\"BlockerId\" <> \"BlockedId\""));

        b.HasKey(x => new { x.BlockerId, x.BlockedId });

        b.HasIndex(x => x.BlockedId).HasDatabaseName("IX_UserBlocks_BlockedId");

        b.HasOne(x => x.Blocker).WithMany()
            .HasForeignKey(x => x.BlockerId)
            .OnDelete(DeleteBehavior.Cascade);

        b.HasOne(x => x.Blocked).WithMany()
            .HasForeignKey(x => x.BlockedId)
            .OnDelete(DeleteBehavior.Cascade);

        b.HasQueryFilter(x => x.Blocker!.DeletedAt == null && x.Blocked!.DeletedAt == null);
    }
}
