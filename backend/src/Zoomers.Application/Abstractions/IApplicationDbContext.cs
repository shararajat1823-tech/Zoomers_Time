using Microsoft.EntityFrameworkCore;
using Zoomers.Domain.Identity;
using Zoomers.Domain.Profiles;
using Zoomers.Domain.Social;

namespace Zoomers.Application.Abstractions;

/// <summary>
/// The persistence surface the application layer depends on. Implemented by the
/// EF Core context in Infrastructure. Keeping this interface here lets services
/// and tests depend on an abstraction rather than a concrete DbContext.
/// </summary>
public interface IApplicationDbContext
{
    DbSet<User> Users { get; }
    DbSet<UserProfile> UserProfiles { get; }
    DbSet<UserSettings> UserSettings { get; }
    DbSet<RefreshToken> RefreshTokens { get; }
    DbSet<PasswordResetToken> PasswordResetTokens { get; }

    DbSet<Follow> Follows { get; }
    DbSet<FollowRequest> FollowRequests { get; }
    DbSet<UserBlock> UserBlocks { get; }

    Task<int> SaveChangesAsync(CancellationToken cancellationToken = default);

    /// <summary>Executes work inside a database transaction (all-or-nothing).</summary>
    Task ExecuteInTransactionAsync(Func<CancellationToken, Task> action, CancellationToken cancellationToken = default);
}
