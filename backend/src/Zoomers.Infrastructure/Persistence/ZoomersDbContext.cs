using Microsoft.EntityFrameworkCore;
using Zoomers.Application.Abstractions;
using Zoomers.Domain.Common;
using Zoomers.Domain.Identity;
using Zoomers.Domain.Profiles;

namespace Zoomers.Infrastructure.Persistence;

/// <summary>
/// The single EF Core context for the modular monolith. Entity configurations
/// live per-module under <c>Persistence/Configurations</c> and are applied via
/// assembly scan. Implements <see cref="IApplicationDbContext"/> so the
/// application layer depends on an abstraction.
/// </summary>
public sealed class ZoomersDbContext : DbContext, IApplicationDbContext
{
    private readonly IClock _clock;

    public ZoomersDbContext(DbContextOptions<ZoomersDbContext> options, IClock clock)
        : base(options) => _clock = clock;

    public DbSet<User> Users => Set<User>();
    public DbSet<UserProfile> UserProfiles => Set<UserProfile>();
    public DbSet<UserSettings> UserSettings => Set<UserSettings>();
    public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>();
    public DbSet<PasswordResetToken> PasswordResetTokens => Set<PasswordResetToken>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        // Case-insensitive text for usernames/emails/tags.
        modelBuilder.HasPostgresExtension("citext");
        modelBuilder.ApplyConfigurationsFromAssembly(typeof(ZoomersDbContext).Assembly);
        base.OnModelCreating(modelBuilder);
    }

    public override Task<int> SaveChangesAsync(CancellationToken cancellationToken = default)
    {
        StampTimestamps();
        return base.SaveChangesAsync(cancellationToken);
    }

    /// <summary>Sets CreatedAt/UpdatedAt consistently so services don't repeat it.</summary>
    private void StampTimestamps()
    {
        var now = _clock.UtcNow;
        foreach (var entry in ChangeTracker.Entries<Entity>())
        {
            if (entry.State == EntityState.Added && entry.Entity.CreatedAt == default)
                entry.Entity.CreatedAt = now;
        }

        foreach (var entry in ChangeTracker.Entries<AuditableEntity>())
        {
            if (entry.State == EntityState.Modified)
                entry.Entity.UpdatedAt = now;
        }
    }
}
