using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Zoomers.Domain.Push;

namespace Zoomers.Infrastructure.Persistence.Configurations;

public sealed class DeviceTokenConfiguration : IEntityTypeConfiguration<DeviceToken>
{
    public void Configure(EntityTypeBuilder<DeviceToken> b)
    {
        b.ToTable("DeviceTokens");
        b.HasKey(d => d.Id);
        b.Property(d => d.Token).IsRequired().HasMaxLength(512);
        b.Property(d => d.Platform).IsRequired().HasMaxLength(16);

        b.HasOne(d => d.User)
            .WithMany()
            .HasForeignKey(d => d.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // A registration token is globally unique (one device/app install).
        b.HasIndex(d => d.Token).IsUnique();
        // Fan-out lookup: all tokens for a user.
        b.HasIndex(d => d.UserId);
    }
}
