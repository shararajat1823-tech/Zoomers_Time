using Microsoft.EntityFrameworkCore;
using Zoomers.Application.Abstractions;
using Zoomers.Domain.Push;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Devices;

public sealed class DeviceService : IDeviceService
{
    private readonly IApplicationDbContext _db;
    private readonly IClock _clock;

    public DeviceService(IApplicationDbContext db, IClock clock)
    {
        _db = db;
        _clock = clock;
    }

    public async Task<Result> RegisterAsync(Guid userId, RegisterDeviceRequest request, CancellationToken ct = default)
    {
        var token = request.Token?.Trim() ?? string.Empty;
        if (token.Length == 0)
            return Error.Validation("device.token_required", "A device token is required.");

        var now = _clock.UtcNow;
        var existing = await _db.DeviceTokens.FirstOrDefaultAsync(d => d.Token == token, ct);
        if (existing is null)
        {
            _db.DeviceTokens.Add(new DeviceToken
            {
                UserId = userId,
                Token = token,
                Platform = string.IsNullOrWhiteSpace(request.Platform) ? "android" : request.Platform.Trim(),
                CreatedAt = now,
                LastSeenAt = now,
            });
        }
        else
        {
            // Re-point the token at the current user (device re-used / re-login) and refresh.
            existing.UserId = userId;
            existing.Platform = string.IsNullOrWhiteSpace(request.Platform) ? existing.Platform : request.Platform.Trim();
            existing.LastSeenAt = now;
        }

        await _db.SaveChangesAsync(ct);
        return Result.Success();
    }

    public async Task<Result> RemoveAsync(Guid userId, RemoveDeviceRequest request, CancellationToken ct = default)
    {
        var token = request.Token?.Trim() ?? string.Empty;
        var row = await _db.DeviceTokens.FirstOrDefaultAsync(d => d.Token == token && d.UserId == userId, ct);
        if (row is not null)
        {
            _db.DeviceTokens.Remove(row);
            await _db.SaveChangesAsync(ct);
        }
        return Result.Success();
    }
}
