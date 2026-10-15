using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Zoomers.Domain.Media;

namespace Zoomers.Infrastructure.Persistence.Configurations;

public sealed class UploadSessionConfiguration : IEntityTypeConfiguration<UploadSession>
{
    public void Configure(EntityTypeBuilder<UploadSession> b)
    {
        b.ToTable("UploadSessions");
        b.HasKey(s => s.Id);

        b.Property(s => s.Purpose).HasConversion<int>();
        b.Property(s => s.Status).HasConversion<int>();
        b.Property(s => s.StorageKey).HasMaxLength(512).IsRequired();
        b.Property(s => s.ContentType).HasMaxLength(128).IsRequired();

        b.HasIndex(s => new { s.UserId, s.Status }).HasDatabaseName("IX_UploadSessions_User_Status");
        b.HasIndex(s => s.ExpiresAt).HasDatabaseName("IX_UploadSessions_ExpiresAt");

        b.HasOne(s => s.User).WithMany()
            .HasForeignKey(s => s.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        b.HasQueryFilter(s => s.User!.DeletedAt == null);
    }
}
