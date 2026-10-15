using Microsoft.EntityFrameworkCore;
using Zoomers.Application.Abstractions;
using Zoomers.Application.Media;
using Zoomers.Application.Posts.Dtos;
using Zoomers.Application.Social;
using Zoomers.Domain.Media;
using Zoomers.Domain.Posts;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Posts;

public sealed class PostService : IPostService
{
    private readonly IApplicationDbContext _db;
    private readonly ISocialGraphService _social;
    private readonly IMediaStorage _storage;
    private readonly IClock _clock;

    public PostService(IApplicationDbContext db, ISocialGraphService social, IMediaStorage storage, IClock clock)
    {
        _db = db;
        _social = social;
        _storage = storage;
        _clock = clock;
    }

    public async Task<Result<PostView>> CreateAsync(Guid authorId, CreatePostRequest request, CancellationToken ct = default)
    {
        if (request.Media is null || request.Media.Count == 0)
            return Error.Validation("post.no_media", "A post must include at least one media item.");
        if (request.Media.Count > MediaPolicy.MaxCarouselItems)
            return Error.Validation("post.too_many_media", $"A post may include at most {MediaPolicy.MaxCarouselItems} items.");

        var sessionIds = request.Media.Select(m => m.UploadSessionId).ToList();
        var sessions = await _db.UploadSessions
            .Where(s => sessionIds.Contains(s.Id) && s.UserId == authorId
                        && s.Status == UploadStatus.Completed && s.Purpose == UploadPurpose.PostMedia)
            .ToDictionaryAsync(s => s.Id, ct);

        if (sessions.Count != sessionIds.Count)
            return Error.Validation("post.invalid_media", "One or more media uploads are missing or not completed.");

        var now = _clock.UtcNow;
        var post = new Post
        {
            AuthorId = authorId,
            Visibility = request.Visibility,
            Caption = Trim(request.Caption, 2200),
            LocationName = Trim(request.LocationName, 100),
            AccessibilityText = Trim(request.AccessibilityText, 1000),
            CreatedAt = now
        };

        var order = 0;
        foreach (var item in request.Media)
        {
            var session = sessions[item.UploadSessionId];
            post.Media.Add(new PostMedia
            {
                OrderIndex = order++,
                MediaType = MediaPolicy.MediaTypeFor(session.ContentType),
                Status = MediaStatus.Ready,
                StorageKey = session.StorageKey,
                Width = item.Width,
                Height = item.Height,
                DurationMs = item.DurationMs,
                CreatedAt = now
            });
        }

        post.Type = post.Media.Count > 1
            ? PostType.Carousel
            : post.Media.First().MediaType == MediaType.Video ? PostType.Video : PostType.Image;

        await _db.ExecuteInTransactionAsync(async token =>
        {
            _db.Posts.Add(post);
            var profile = await _db.UserProfiles.FirstOrDefaultAsync(p => p.UserId == authorId, token);
            if (profile is not null) profile.PostCount += 1;
            await _db.SaveChangesAsync(token);
        }, ct);

        return await GetAsync(authorId, post.Id, ct);
    }

    public async Task<Result<PostView>> GetAsync(Guid viewerId, Guid postId, CancellationToken ct = default)
    {
        var post = await _db.Posts
            .Include(p => p.Author!).ThenInclude(a => a.Profile)
            .Include(p => p.Media)
            .FirstOrDefaultAsync(p => p.Id == postId, ct);

        if (post is null)
            return Error.NotFound("post.not_found", "Post not found.");

        if (!await CanViewPostAsync(viewerId, post.AuthorId, post.Visibility, ct))
            return Error.NotFound("post.not_found", "Post not found.");

        var views = await BuildViewsAsync(new List<Post> { post }, viewerId, ct);
        return Result.Success(views[0]);
    }

    public async Task<Result> DeleteAsync(Guid userId, Guid postId, CancellationToken ct = default)
    {
        var post = await _db.Posts.FirstOrDefaultAsync(p => p.Id == postId, ct);
        if (post is null)
            return Error.NotFound("post.not_found", "Post not found.");
        if (post.AuthorId != userId)
            return Error.Forbidden("post.not_owner", "You can only delete your own posts.");

        await _db.ExecuteInTransactionAsync(async token =>
        {
            post.DeletedAt = _clock.UtcNow;
            var profile = await _db.UserProfiles.FirstOrDefaultAsync(p => p.UserId == userId, token);
            if (profile is not null) profile.PostCount = Math.Max(0, profile.PostCount - 1);
            await _db.SaveChangesAsync(token);
        }, ct);

        return Result.Success();
    }

    public async Task<Result<CursorPage<PostView>>> GetUserPostsAsync(Guid viewerId, Guid targetUserId, string? cursor, int limit, CancellationToken ct = default)
    {
        if (!await _db.Users.AnyAsync(u => u.Id == targetUserId, ct)
            || await _social.IsBlockedEitherWayAsync(viewerId, targetUserId, ct))
            return Error.NotFound("user.not_found", "User not found.");

        if (!await _social.CanViewContentAsync(viewerId, targetUserId, ct))
            return Error.Forbidden("profile.private", "This account is private.");

        limit = PageLimit.Clamp(limit);
        var isOwner = viewerId == targetUserId;
        var isFollower = isOwner || await _db.Follows.AnyAsync(f => f.FollowerId == viewerId && f.FolloweeId == targetUserId, ct);

        var q = _db.Posts.Where(p => p.AuthorId == targetUserId);
        if (!isOwner)
        {
            // Non-owners never see Private posts; Followers-only posts require following.
            q = isFollower
                ? q.Where(p => p.Visibility != PostVisibility.Private)
                : q.Where(p => p.Visibility == PostVisibility.Public);
        }

        return await PagePostsAsync(q, viewerId, cursor, limit, ct);
    }

    public async Task<Result<CursorPage<PostView>>> GetSavedAsync(Guid userId, string? cursor, int limit, CancellationToken ct = default)
    {
        limit = PageLimit.Clamp(limit);

        // Join saved rows to posts; keyset on the save timestamp.
        var q = _db.SavedPosts.Where(s => s.UserId == userId);
        if (KeysetCursor.TryParse(cursor, out var k))
            q = q.Where(s => s.CreatedAt < k.CreatedAt || (s.CreatedAt == k.CreatedAt && s.PostId < k.Id));

        var savedRows = await q
            .OrderByDescending(s => s.CreatedAt).ThenByDescending(s => s.PostId)
            .Take(limit + 1)
            .Select(s => new { s.PostId, s.CreatedAt })
            .ToListAsync(ct);

        var hasMore = savedRows.Count > limit;
        var pageRows = savedRows.Take(limit).ToList();
        var postIds = pageRows.Select(r => r.PostId).ToList();

        var posts = await _db.Posts
            .Include(p => p.Author!).ThenInclude(a => a.Profile)
            .Include(p => p.Media)
            .Where(p => postIds.Contains(p.Id))
            .ToListAsync(ct);

        // Preserve the saved order.
        var ordered = pageRows
            .Select(r => posts.FirstOrDefault(p => p.Id == r.PostId))
            .Where(p => p is not null)
            .Select(p => p!)
            .ToList();

        var views = await BuildViewsAsync(ordered, userId, ct);
        string? next = hasMore && pageRows.Count > 0
            ? new KeysetCursor(pageRows[^1].CreatedAt, pageRows[^1].PostId).Encode()
            : null;

        return Result.Success(new CursorPage<PostView>(views, next));
    }

    // ---- shared helpers (also used by the feed) ----

    internal async Task<bool> CanViewPostAsync(Guid viewerId, Guid authorId, PostVisibility visibility, CancellationToken ct)
    {
        if (viewerId == authorId) return true;
        if (!await _social.CanViewContentAsync(viewerId, authorId, ct)) return false;

        return visibility switch
        {
            PostVisibility.Public => true,
            PostVisibility.Followers => await _db.Follows.AnyAsync(f => f.FollowerId == viewerId && f.FolloweeId == authorId, ct),
            _ => false // Private: author only (handled above)
        };
    }

    internal async Task<Result<CursorPage<PostView>>> PagePostsAsync(IQueryable<Post> query, Guid viewerId, string? cursor, int limit, CancellationToken ct)
    {
        if (KeysetCursor.TryParse(cursor, out var k))
            query = query.Where(p => p.CreatedAt < k.CreatedAt || (p.CreatedAt == k.CreatedAt && p.Id < k.Id));

        var posts = await query
            .OrderByDescending(p => p.CreatedAt).ThenByDescending(p => p.Id)
            .Take(limit + 1)
            .Include(p => p.Author!).ThenInclude(a => a.Profile)
            .Include(p => p.Media)
            .ToListAsync(ct);

        var hasMore = posts.Count > limit;
        var page = posts.Take(limit).ToList();
        var views = await BuildViewsAsync(page, viewerId, ct);

        string? next = hasMore && page.Count > 0
            ? new KeysetCursor(page[^1].CreatedAt, page[^1].Id).Encode()
            : null;

        return Result.Success(new CursorPage<PostView>(views, next));
    }

    /// <summary>Maps posts to views, batch-loading the viewer's like/save state and building media URLs.</summary>
    internal async Task<List<PostView>> BuildViewsAsync(List<Post> posts, Guid viewerId, CancellationToken ct)
    {
        if (posts.Count == 0) return new List<PostView>();

        var ids = posts.Select(p => p.Id).ToList();
        var liked = await _db.PostLikes.Where(l => l.UserId == viewerId && ids.Contains(l.PostId))
            .Select(l => l.PostId).ToListAsync(ct);
        var saved = await _db.SavedPosts.Where(s => s.UserId == viewerId && ids.Contains(s.PostId))
            .Select(s => s.PostId).ToListAsync(ct);
        var likedSet = liked.ToHashSet();
        var savedSet = saved.ToHashSet();

        var result = new List<PostView>(posts.Count);
        foreach (var post in posts)
        {
            var media = new List<PostMediaView>();
            foreach (var m in post.Media.OrderBy(x => x.OrderIndex))
            {
                var url = (await _storage.CreateDownloadUrlAsync(m.StorageKey, ct)).Url;
                var thumb = m.ThumbnailKey is null ? null : (await _storage.CreateDownloadUrlAsync(m.ThumbnailKey, ct)).Url;
                media.Add(new PostMediaView(m.Id, m.MediaType, url, thumb, m.Width, m.Height, m.DurationMs, m.OrderIndex));
            }

            var profile = post.Author?.Profile;
            result.Add(new PostView(
                post.Id,
                new PostAuthor(post.AuthorId, post.Author?.Username ?? string.Empty, profile?.DisplayName ?? string.Empty, profile?.IsVerified ?? false),
                post.Type,
                post.Visibility,
                post.Caption,
                post.LocationName,
                post.AccessibilityText,
                post.CreatedAt,
                post.LikeCount,
                post.CommentCount,
                media,
                new ViewerPostState(likedSet.Contains(post.Id), savedSet.Contains(post.Id))));
        }

        return result;
    }

    private static string? Trim(string? value, int max) =>
        string.IsNullOrWhiteSpace(value) ? null : value.Trim()[..Math.Min(value.Trim().Length, max)];
}
