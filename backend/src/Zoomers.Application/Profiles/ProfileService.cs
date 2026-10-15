using Microsoft.EntityFrameworkCore;
using Zoomers.Application.Abstractions;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Profiles;

public sealed class ProfileService : IProfileService
{
    private readonly IApplicationDbContext _db;
    public ProfileService(IApplicationDbContext db) => _db = db;

    public async Task<Result<ProfileView>> GetMeAsync(Guid userId, CancellationToken ct = default)
    {
        var view = await _db.Users
            .Where(u => u.Id == userId)
            .Select(u => new ProfileView(
                u.Id,
                u.Username,
                u.Profile!.DisplayName,
                u.Profile.Bio,
                u.Profile.Website,
                u.Profile.IsPrivate,
                u.Profile.IsVerified,
                u.Profile.FollowerCount,
                u.Profile.FollowingCount,
                u.Profile.PostCount))
            .FirstOrDefaultAsync(ct);

        return view is null
            ? Error.NotFound("profile.not_found", "Profile not found.")
            : Result.Success(view);
    }
}
