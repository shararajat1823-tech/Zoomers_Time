using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Zoomers.Domain.Identity;
using Zoomers.Domain.Profiles;

namespace Zoomers.Infrastructure.Persistence.Configurations;

public sealed class UserConfiguration : IEntityTypeConfiguration<User>
{
    public void Configure(EntityTypeBuilder<User> b)
    {
        b.ToTable("Users");
        b.HasKey(u => u.Id);

        b.Property(u => u.Username).HasColumnType("citext").HasMaxLength(30).IsRequired();
        b.HasIndex(u => u.Username).IsUnique().HasDatabaseName("UQ_Users_Username");

        b.Property(u => u.Email).HasColumnType("citext").HasMaxLength(254);
        b.HasIndex(u => u.Email).IsUnique().HasDatabaseName("UQ_Users_Email")
            .HasFilter("\"Email\" IS NOT NULL");

        b.Property(u => u.PhoneNumber).HasMaxLength(20);
        b.HasIndex(u => u.PhoneNumber).IsUnique().HasDatabaseName("UQ_Users_Phone")
            .HasFilter("\"PhoneNumber\" IS NOT NULL");

        b.Property(u => u.PasswordHash).IsRequired();
        b.Property(u => u.Status).HasConversion<int>();
        b.HasIndex(u => u.Status).HasDatabaseName("IX_Users_Status");

        // Soft delete: hide deleted users by default.
        b.HasQueryFilter(u => u.DeletedAt == null);

        b.HasOne(u => u.Profile).WithOne(p => p.User!)
            .HasForeignKey<UserProfile>(p => p.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        b.HasOne(u => u.Settings).WithOne(s => s.User!)
            .HasForeignKey<UserSettings>(s => s.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        b.HasMany(u => u.RefreshTokens).WithOne(t => t.User!)
            .HasForeignKey(t => t.UserId)
            .OnDelete(DeleteBehavior.Cascade);
    }
}
