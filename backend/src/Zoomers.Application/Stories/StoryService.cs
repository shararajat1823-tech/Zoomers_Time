using Microsoft.EntityFrameworkCore;
using Zoomers.Application.Abstractions;
using Zoomers.Application.Stories.Dtos;
using Zoomers.Domain.Media;
using Zoomers.Domain.Stories;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Stories;

public sealed class StoryService : IStoryService
{
    private static readonly TimeSpan StoryLifetime = TimeSpan.FromHours(24);

    private readonly IApplicationDbContext _db;
    private readonly IMediaStorage _storage;
    private readonly IClock _clock;

    public StoryService(IApplicationDbContext db, IMediaStorage storage, IClock clock)
    {
        _db = db;
        _storage = storage;
        _clock = clock;
    }

    public async Task<Result> CreateAsync(Guid userId, CreateStoryRequest request, CancellationToken ct = default)
    {
        var session = await _db.UploadSessions.FirstOrDefaultAsync(s => s.Id == request.UploadSessionId, ct);
        if (session is null || session.UserId != userId || session.Purpose != UploadPurpose.StoryMedia)
            return Error.Validation("story.invalid_media", "That story upload could not be found.");
        if (session.Status != UploadStatus.Completed)
            return Error.Validation("story.media_incomplete", "The story upload is not complete.");

        var now = _clock.UtcNow;
        _db.Stories.Add(new Story
        {
            AuthorId = userId,
            MediaKey = session.StorageKey,
            CreatedAt = now,
            ExpiresAt = now.Add(StoryLifetime),
        });
        await _db.SaveChangesAsync(ct);
        return Result.Success();
    }

    public async Task<Result<IReadOnlyList<StoryTrayDto>>> GetFeedAsync(Guid userId, CancellationToken ct = default)
    {
        var now = _clock.UtcNow;

        var followedIds = await _db.Follows
            .Where(f => f.FollowerId == userId)
            .Select(f => f.FolloweeId)
            .ToListAsync(ct);

        var authorIds = followedIds.Append(userId).Distinct().ToList();

        var rows = await _db.Stories
            .Where(s => authorIds.Contains(s.AuthorId) && s.ExpiresAt > now)
            .OrderBy(s => s.CreatedAt)
            .Select(s => new Row(
                s.Id,
                s.AuthorId,
                s.MediaKey,
                s.CreatedAt,
                s.Author!.Username,
                s.Author.Profile!.DisplayName,
                s.Author.Profile.IsVerified,
                s.Author.Profile.AvatarMediaKey,
                _db.StoryViews.Any(v => v.StoryId == s.Id && v.ViewerId == userId)))
            .ToListAsync(ct);

        var trays = new List<StoryTrayDto>();
        foreach (var group in rows.GroupBy(r => r.AuthorId))
        {
            var first = group.First();
            var items = new List<StoryItemDto>(group.Count());
            foreach (var r in group)
            {
                var url = (await _storage.CreateDownloadUrlAsync(r.MediaKey, ct)).Url;
                items.Add(new StoryItemDto(r.Id, url, r.CreatedAt, r.Viewed));
            }

            trays.Add(new StoryTrayDto(
                new StoryAuthorDto(
                    first.AuthorId, first.Username, first.DisplayName,
                    first.AvatarKey is null ? null : (await _storage.CreateDownloadUrlAsync(first.AvatarKey, ct)).Url,
                    first.IsVerified),
                items,
                HasUnseen: items.Any(i => !i.Viewed),
                IsSelf: first.AuthorId == userId));
        }

        // Own tray first, then unseen, then most-recent story.
        var ordered = trays
            .OrderByDescending(t => t.IsSelf)
            .ThenByDescending(t => t.HasUnseen)
            .ThenByDescending(t => t.Stories.Max(s => s.CreatedAt))
            .ToList();

        return Result.Success<IReadOnlyList<StoryTrayDto>>(ordered);
    }

    public async Task<Result> MarkViewedAsync(Guid userId, Guid storyId, CancellationToken ct = default)
    {
        var exists = await _db.Stories.AnyAsync(s => s.Id == storyId, ct);
        if (!exists)
            return Error.NotFound("story.not_found", "Story not found.");

        if (await _db.StoryViews.AnyAsync(v => v.StoryId == storyId && v.ViewerId == userId, ct))
            return Result.Success();

        try
        {
            _db.StoryViews.Add(new StoryView { StoryId = storyId, ViewerId = userId, CreatedAt = _clock.UtcNow });
            await _db.SaveChangesAsync(ct);
        }
        catch (DbUpdateException)
        {
            // Raced with a concurrent view; the unique index won — treat as viewed.
        }
        return Result.Success();
    }

    private sealed record Row(
        Guid Id,
        Guid AuthorId,
        string MediaKey,
        DateTimeOffset CreatedAt,
        string Username,
        string DisplayName,
        bool IsVerified,
        string? AvatarKey,
        bool Viewed);
}
