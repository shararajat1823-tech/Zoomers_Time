using Microsoft.EntityFrameworkCore;
using Zoomers.Application.Abstractions;
using Zoomers.Application.Settings.Dtos;
using Zoomers.Application.Social.Dtos;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Settings;

public sealed class SettingsService : ISettingsService
{
    private readonly IApplicationDbContext _db;
    private readonly IClock _clock;

    public SettingsService(IApplicationDbContext db, IClock clock)
    {
        _db = db;
        _clock = clock;
    }

    public async Task<Result<SettingsView>> GetAsync(Guid userId, CancellationToken ct = default)
    {
        var s = await _db.UserSettings.FirstOrDefaultAsync(x => x.UserId == userId, ct);
        return s is null
            ? Error.NotFound("settings.not_found", "Settings not found.")
            : Result.Success(Map(s));
    }

    public async Task<Result<SettingsView>> UpdateAsync(Guid userId, UpdateSettingsRequest r, CancellationToken ct = default)
    {
        var s = await _db.UserSettings.FirstOrDefaultAsync(x => x.UserId == userId, ct);
        if (s is null)
            return Error.NotFound("settings.not_found", "Settings not found.");

        if (r.Theme is not null) s.Theme = r.Theme.Value;
        if (!string.IsNullOrWhiteSpace(r.Language)) s.Language = r.Language.Trim();
        if (r.WhoCanComment is not null) s.WhoCanComment = r.WhoCanComment.Value;
        if (r.WhoCanMention is not null) s.WhoCanMention = r.WhoCanMention.Value;
        if (r.WhoCanMessage is not null) s.WhoCanMessage = r.WhoCanMessage.Value;
        if (r.ShowOnlineStatus is not null) s.ShowOnlineStatus = r.ShowOnlineStatus.Value;
        if (r.PushLikes is not null) s.PushLikes = r.PushLikes.Value;
        if (r.PushComments is not null) s.PushComments = r.PushComments.Value;
        if (r.PushFollows is not null) s.PushFollows = r.PushFollows.Value;
        if (r.PushMessages is not null) s.PushMessages = r.PushMessages.Value;

        s.UpdatedAt = _clock.UtcNow;
        await _db.SaveChangesAsync(ct);
        return Result.Success(Map(s));
    }

    public async Task<Result<CursorPage<UserListItem>>> GetBlockedAsync(Guid userId, string? cursor, int limit, CancellationToken ct = default)
    {
        limit = PageLimit.Clamp(limit);
        var q = _db.UserBlocks.Where(b => b.BlockerId == userId);
        if (KeysetCursor.TryParse(cursor, out var k))
            q = q.Where(b => b.CreatedAt < k.CreatedAt || (b.CreatedAt == k.CreatedAt && b.BlockedId < k.Id));

        var page = await q
            .OrderByDescending(b => b.CreatedAt).ThenByDescending(b => b.BlockedId)
            .Take(limit + 1)
            .Select(b => new { b.CreatedAt, Item = new UserListItem(b.Blocked!.Id, b.Blocked.Username, b.Blocked.Profile!.DisplayName, b.Blocked.Profile.IsVerified, b.Blocked.Profile.IsPrivate) })
            .ToListAsync(ct);

        return Result.Success(ToPage(page.Select(x => (x.CreatedAt, x.Item.Id, x.Item)).ToList(), limit));
    }

    public async Task<Result<CursorPage<UserListItem>>> GetMutedAsync(Guid userId, string? cursor, int limit, CancellationToken ct = default)
    {
        limit = PageLimit.Clamp(limit);
        var q = _db.UserMutes.Where(m => m.MuterId == userId);
        if (KeysetCursor.TryParse(cursor, out var k))
            q = q.Where(m => m.CreatedAt < k.CreatedAt || (m.CreatedAt == k.CreatedAt && m.MutedId < k.Id));

        var page = await q
            .OrderByDescending(m => m.CreatedAt).ThenByDescending(m => m.MutedId)
            .Take(limit + 1)
            .Select(m => new { m.CreatedAt, Item = new UserListItem(m.Muted!.Id, m.Muted.Username, m.Muted.Profile!.DisplayName, m.Muted.Profile.IsVerified, m.Muted.Profile.IsPrivate) })
            .ToListAsync(ct);

        return Result.Success(ToPage(page.Select(x => (x.CreatedAt, x.Item.Id, x.Item)).ToList(), limit));
    }

    private static CursorPage<UserListItem> ToPage(List<(DateTimeOffset CreatedAt, Guid Id, UserListItem Item)> rows, int limit)
    {
        var hasMore = rows.Count > limit;
        var items = rows.Take(limit).Select(r => r.Item).ToList();
        string? next = hasMore ? new KeysetCursor(rows[limit - 1].CreatedAt, rows[limit - 1].Id).Encode() : null;
        return new CursorPage<UserListItem>(items, next);
    }

    private static SettingsView Map(Domain.Profiles.UserSettings s) => new(
        s.Theme, s.Language, s.WhoCanComment, s.WhoCanMention, s.WhoCanMessage,
        s.ShowOnlineStatus, s.PushLikes, s.PushComments, s.PushFollows, s.PushMessages);
}
