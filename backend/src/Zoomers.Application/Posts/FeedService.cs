using Microsoft.EntityFrameworkCore;
using Zoomers.Application.Abstractions;
using Zoomers.Application.Posts.Dtos;
using Zoomers.Domain.Posts;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Posts;

/// <summary>
/// Composes the following feed. Kept deliberately replaceable: the ranking is a
/// simple reverse-chronological strategy over followed authors now, with a clean
/// seam for recommendation/ranking strategies later.
/// </summary>
public sealed class FeedService : IFeedService
{
    private readonly IApplicationDbContext _db;
    private readonly PostService _posts;

    public FeedService(IApplicationDbContext db, PostService posts)
    {
        _db = db;
        _posts = posts;
    }

    public Task<Result<CursorPage<PostView>>> GetFollowingFeedAsync(Guid userId, string? cursor, int limit, CancellationToken ct = default)
    {
        limit = PageLimit.Clamp(limit);

        // Posts from accounts the viewer follows. A follow edge guarantees the viewer
        // may see the author's Public and Followers-only posts; Private is excluded.
        // Blocks remove the follow edge, so blocked authors never appear.
        var query = _db.Posts.Where(p =>
            p.Visibility != PostVisibility.Private &&
            _db.Follows.Any(f => f.FollowerId == userId && f.FolloweeId == p.AuthorId) &&
            // Posts from muted (posts) accounts are hidden from the feed.
            !_db.UserMutes.Any(m => m.MuterId == userId && m.MutedId == p.AuthorId && m.MutePosts));

        return _posts.PagePostsAsync(query, userId, cursor, limit, ct);
    }
}
