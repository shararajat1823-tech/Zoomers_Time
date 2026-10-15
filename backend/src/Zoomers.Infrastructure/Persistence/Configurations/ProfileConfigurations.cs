using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Zoomers.Domain.Profiles;

namespace Zoomers.Infrastructure.Persistence.Configurations;

public sealed class UserProfileConfiguration : IEntityTypeConfiguration<UserProfile>
{
    public void Configure(EntityTypeBuilder<UserProfile> b)
    {
        b.ToTable("UserProfiles");
        b.HasKey(p => p.Id);
        b.HasIndex(p => p.UserId).IsUnique();

        b.Property(p => p.DisplayName).HasMaxLength(50).IsRequired();
        // Trigram index for fuzzy display-name search.
        b.HasIndex(p => p.DisplayName).HasDatabaseName("IX_UserProfiles_DisplayName_Trgm")
            .HasMethod("gin").HasOperators("gin_trgm_ops");
        b.Property(p => p.Bio).HasMaxLength(300);
        b.Property(p => p.Website).HasMaxLength(200);
        b.Property(p => p.AvatarMediaKey).HasMaxLength(512);
        b.Property(p => p.CoverMediaKey).HasMaxLength(512);

        // Match the User soft-delete filter so a deleted user's profile is hidden too.
        b.HasQueryFilter(p => p.User!.DeletedAt == null);
    }
}

public sealed class UserSettingsConfiguration : IEntityTypeConfiguration<UserSettings>
{
    public void Configure(EntityTypeBuilder<UserSettings> b)
    {
        b.ToTable("UserSettings");
        b.HasKey(s => s.Id);
        b.HasIndex(s => s.UserId).IsUnique();

        b.Property(s => s.Theme).HasConversion<int>();
        b.Property(s => s.WhoCanComment).HasConversion<int>();
        b.Property(s => s.WhoCanMention).HasConversion<int>();
        b.Property(s => s.WhoCanMessage).HasConversion<int>();
        b.Property(s => s.Language).HasMaxLength(10).IsRequired();

        b.HasQueryFilter(s => s.User!.DeletedAt == null);
    }
}
