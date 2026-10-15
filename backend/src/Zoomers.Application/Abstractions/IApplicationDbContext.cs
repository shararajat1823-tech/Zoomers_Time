using Microsoft.EntityFrameworkCore;
using Zoomers.Domain.Identity;
using Zoomers.Domain.Profiles;

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

    Task<int> SaveChangesAsync(CancellationToken cancellationToken = default);
}
