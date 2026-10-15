using System.Globalization;
using Microsoft.EntityFrameworkCore;
using Zoomers.Application.Abstractions;
using Zoomers.Application.Posts;
using Zoomers.Application.Posts.Dtos;
using Zoomers.Application.Search.Dtos;
using Zoomers.Application.Social.Dtos;
using Zoomers.Domain.Posts;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Search;

public sealed class SearchService : ISearchService
{
    private readonly IApplicationDbContext _db;
    private readonly PostService _posts;

    public SearchService(IApplicationDbContext db, PostService posts)
    {
        _db = db;
        _posts = posts;
    }

    public async Task<Result<CursorPage<UserListItem>>> SearchUsersAsync(Guid viewerId, string query, string? cursor, int limit, CancellationToken ct = default)
    {
        var q = query.Trim();
        if (q.Length == 0) return Result.Success(CursorPage<UserListItem>.Empty());

        limit = PageLimit.Clamp(limit);
        var offset = DecodeOffset(cursor);
        var term = q.ToLowerInvariant();

        var rows = _db.Users
            .Where(u => u.Id != viewerId)
            .Where(u => u.Username.ToLower().StartsWith(term) || u.Profile!.DisplayName.ToLower().Contains(term))
            .Where(u => !_db.UserBlocks.Any(b =>
                (b.BlockerId == viewerId && b.BlockedId == u.Id) ||
                (b.BlockerId == u.Id && b.BlockedId == viewerId)))
            .OrderByDescending(u => u.Profile!.FollowerCount).ThenBy(u => u.Username)
            .Skip(offset).Take(limit + 1)
            .Select(u => new UserListItem(u.Id, u.Username, u.Profile!.DisplayName, u.Profile.IsVerified, u.Profile.IsPrivate));

        var list = await rows.ToListAsync(ct);
        return Result.Success(OffsetPage(list, offset, limit));
    }

    public async Task<Result<CursorPage<HashtagResult>>> SearchHashtagsAsync(string query, string? cursor, int limit, CancellationToken ct = default)
    {
        var q = query.Trim().TrimStart('#').ToLowerInvariant();
        if (q.Length == 0) return Result.Success(CursorPage<HashtagResult>.Empty());

        limit = PageLimit.Clamp(limit);
        var offset = DecodeOffset(cursor);

        var list = await _db.Hashtags
            .Where(h => h.Tag.Contains(q))
            .OrderByDescending(h => h.UsageCount).ThenBy(h => h.Tag)
            .Skip(offset).Take(limit + 1)
            .Select(h => new HashtagResult(h.Tag, h.UsageCount))
            .ToListAsync(ct);

        return Result.Success(OffsetPage(list, offset, limit));
    }

    public Task<Result<CursorPage<PostView>>> SearchPostsAsync(Guid viewerId, string query, string? cursor, int limit, CancellationToken ct = default)
    {
        var q = query.Trim();
        if (q.Length == 0) return Task.FromResult(Result.Success(CursorPage<PostView>.Empty()));

        limit = PageLimit.Clamp(limit);
        var term = q.ToLowerInvariant();

        // Only public posts on public accounts are searchable, and never from blocked users.
        var posts = PublicVisibleQuery(viewerId)
            .Where(p => p.Caption != null && p.Caption.ToLower().Contains(term));

        return _posts.PagePostsAsync(posts, viewerId, cursor, limit, ct);
    }

    public Task<Result<CursorPage<PostView>>> GetHashtagPostsAsync(Guid viewerId, string tag, string? cursor, int limit, CancellationToken ct = default)
    {
        var normalized = tag.Trim().TrimStart('#').ToLowerInvariant();
        if (normalized.Length == 0) return Task.FromResult(Result.Success(CursorPage<PostView>.Empty()));

        limit = PageLimit.Clamp(limit);
        var posts = PublicVisibleQuery(viewerId)
            .Where(p => _db.PostHashtags.Any(ph => ph.PostId == p.Id && ph.Hashtag!.Tag == normalized));

        return _posts.PagePostsAsync(posts, viewerId, cursor, limit, ct);
    }

    public async Task<Result<CursorPage<UserListItem>>> SuggestedUsersAsync(Guid viewerId, string? cursor, int limit, CancellationToken ct = default)
    {
        limit = PageLimit.Clamp(limit);
        var offset = DecodeOffset(cursor);

        var list = await _db.Users
            .Where(u => u.Id != viewerId)
            .Where(u => !_db.Follows.Any(f => f.FollowerId == viewerId && f.FolloweeId == u.Id))
            .Where(u => !_db.UserBlocks.Any(b =>
                (b.BlockerId == viewerId && b.BlockedId == u.Id) ||
                (b.BlockerId == u.Id && b.BlockedId == viewerId)))
            .OrderByDescending(u => u.Profile!.FollowerCount).ThenBy(u => u.Username)
            .Skip(offset).Take(limit + 1)
            .Select(u => new UserListItem(u.Id, u.Username, u.Profile!.DisplayName, u.Profile.IsVerified, u.Profile.IsPrivate))
            .ToListAsync(ct);

        return Result.Success(OffsetPage(list, offset, limit));
    }

    // ---- helpers ----

    /// <summary>Posts that are safe to surface in discovery: Public visibility, public account, not blocked.</summary>
    private IQueryable<Post> PublicVisibleQuery(Guid viewerId) =>
        _db.Posts.Where(p =>
            p.Visibility == PostVisibility.Public &&
            !p.Author!.Profile!.IsPrivate &&
            !_db.UserBlocks.Any(b =>
                (b.BlockerId == viewerId && b.BlockedId == p.AuthorId) ||
                (b.BlockerId == p.AuthorId && b.BlockedId == viewerId)));

    private static int DecodeOffset(string? cursor)
    {
        if (Cursor.TryDecode(cursor, out var raw) && int.TryParse(raw, NumberStyles.Integer, CultureInfo.InvariantCulture, out var n) && n >= 0)
            return n;
        return 0;
    }

    private static CursorPage<T> OffsetPage<T>(List<T> fetched, int offset, int limit)
    {
        var hasMore = fetched.Count > limit;
        var items = fetched.Take(limit).ToList();
        var next = hasMore ? Cursor.Encode((offset + limit).ToString(CultureInfo.InvariantCulture)) : null;
        return new CursorPage<T>(items, next);
    }
}
