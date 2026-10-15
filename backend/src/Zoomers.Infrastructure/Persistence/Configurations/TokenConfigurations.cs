using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Zoomers.Domain.Identity;

namespace Zoomers.Infrastructure.Persistence.Configurations;

public sealed class RefreshTokenConfiguration : IEntityTypeConfiguration<RefreshToken>
{
    public void Configure(EntityTypeBuilder<RefreshToken> b)
    {
        b.ToTable("RefreshTokens");
        b.HasKey(t => t.Id);

        b.Property(t => t.TokenHash).HasMaxLength(128).IsRequired();
        b.HasIndex(t => t.TokenHash).IsUnique().HasDatabaseName("UQ_RefreshTokens_TokenHash");
        b.HasIndex(t => t.UserId).HasDatabaseName("IX_RefreshTokens_UserId");

        b.Property(t => t.CreatedByIp).HasMaxLength(64);
        b.Property(t => t.UserAgent).HasMaxLength(512);

        // Tokens of a soft-deleted user are not honored.
        b.HasQueryFilter(t => t.User!.DeletedAt == null);
    }
}

public sealed class PasswordResetTokenConfiguration : IEntityTypeConfiguration<PasswordResetToken>
{
    public void Configure(EntityTypeBuilder<PasswordResetToken> b)
    {
        b.ToTable("PasswordResetTokens");
        b.HasKey(t => t.Id);

        b.Property(t => t.TokenHash).HasMaxLength(128).IsRequired();
        b.HasIndex(t => new { t.UserId, t.TokenHash }).HasDatabaseName("IX_PasswordResetTokens_User_Hash");

        b.HasOne(t => t.User).WithMany()
            .HasForeignKey(t => t.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        b.HasQueryFilter(t => t.User!.DeletedAt == null);
    }
}
