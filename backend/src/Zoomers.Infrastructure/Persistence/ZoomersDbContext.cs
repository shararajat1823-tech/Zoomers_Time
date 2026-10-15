using Microsoft.EntityFrameworkCore;
using Zoomers.Application.Abstractions;
using Zoomers.Domain.Common;
using Zoomers.Domain.Engagement;
using Zoomers.Domain.Identity;
using Zoomers.Domain.Media;
using Zoomers.Domain.Posts;
using Zoomers.Domain.Profiles;
using Zoomers.Domain.Social;

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
    public DbSet<Follow> Follows => Set<Follow>();
    public DbSet<FollowRequest> FollowRequests => Set<FollowRequest>();
    public DbSet<UserBlock> UserBlocks => Set<UserBlock>();
    public DbSet<Post> Posts => Set<Post>();
    public DbSet<PostMedia> PostMedia => Set<PostMedia>();
    public DbSet<UploadSession> UploadSessions => Set<UploadSession>();
    public DbSet<PostLike> PostLikes => Set<PostLike>();
    public DbSet<SavedPost> SavedPosts => Set<SavedPost>();
    public DbSet<Comment> Comments => Set<Comment>();
    public DbSet<CommentLike> CommentLikes => Set<CommentLike>();

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

    public async Task ExecuteInTransactionAsync(Func<CancellationToken, Task> action, CancellationToken cancellationToken = default)
    {
        var strategy = Database.CreateExecutionStrategy();
        await strategy.ExecuteAsync(async () =>
        {
            await using var tx = await Database.BeginTransactionAsync(cancellationToken);
            await action(cancellationToken);
            await tx.CommitAsync(cancellationToken);
        });
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
