using Microsoft.EntityFrameworkCore;
using Zoomers.Application.Abstractions;
using Zoomers.Application.Notifications.Dtos;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Notifications;

public sealed class NotificationService : INotificationService
{
    private readonly IApplicationDbContext _db;
    private readonly IMediaStorage _storage;
    private readonly IClock _clock;

    public NotificationService(IApplicationDbContext db, IMediaStorage storage, IClock clock)
    {
        _db = db;
        _storage = storage;
        _clock = clock;
    }

    public async Task<Result<CursorPage<NotificationView>>> GetAsync(Guid userId, string? cursor, int limit, CancellationToken ct = default)
    {
        limit = PageLimit.Clamp(limit);

        var q = _db.Notifications.Where(n => n.RecipientId == userId);
        if (KeysetCursor.TryParse(cursor, out var k))
            q = q.Where(n => n.CreatedAt < k.CreatedAt || (n.CreatedAt == k.CreatedAt && n.Id < k.Id));

        var rows = await q
            .OrderByDescending(n => n.CreatedAt).ThenByDescending(n => n.Id)
            .Take(limit + 1)
            .Select(n => new Row(
                n.Id,
                n.Type,
                n.CreatedAt,
                n.ReadAt,
                n.PostId,
                n.ActorId,
                n.Actor!.Username,
                n.Actor.Profile!.DisplayName,
                n.Actor.Profile.IsVerified,
                n.Actor.Profile.AvatarMediaKey,
                n.PostId == null
                    ? null
                    : _db.PostMedia.Where(m => m.PostId == n.PostId)
                        .OrderBy(m => m.OrderIndex)
                        .Select(m => m.ThumbnailKey ?? m.StorageKey)
                        .FirstOrDefault()))
            .ToListAsync(ct);

        var hasMore = rows.Count > limit;
        var page = rows.Take(limit).ToList();

        var views = new List<NotificationView>(page.Count);
        foreach (var r in page)
        {
            views.Add(new NotificationView(
                r.Id,
                r.Type,
                new NotificationActor(
                    r.ActorId, r.Username, r.DisplayName,
                    await ResolveUrlAsync(r.AvatarKey, ct),
                    r.IsVerified),
                r.PostId,
                await ResolveUrlAsync(r.PostThumbKey, ct),
                r.CreatedAt,
                r.ReadAt is not null));
        }

        var next = hasMore && page.Count > 0
            ? new KeysetCursor(page[^1].CreatedAt, page[^1].Id).Encode()
            : null;

        return Result.Success(new CursorPage<NotificationView>(views, next));
    }

    public async Task<Result> MarkAllReadAsync(Guid userId, CancellationToken ct = default)
    {
        var unread = await _db.Notifications
            .Where(n => n.RecipientId == userId && n.ReadAt == null)
            .ToListAsync(ct);
        var now = _clock.UtcNow;
        foreach (var n in unread) n.ReadAt = now;
        await _db.SaveChangesAsync(ct);
        return Result.Success();
    }

    private async Task<string?> ResolveUrlAsync(string? key, CancellationToken ct) =>
        string.IsNullOrEmpty(key) ? null : (await _storage.CreateDownloadUrlAsync(key, ct)).Url;

    private sealed record Row(
        Guid Id,
        Domain.Notifications.NotificationType Type,
        DateTimeOffset CreatedAt,
        DateTimeOffset? ReadAt,
        Guid? PostId,
        Guid ActorId,
        string Username,
        string DisplayName,
        bool IsVerified,
        string? AvatarKey,
        string? PostThumbKey);
}
