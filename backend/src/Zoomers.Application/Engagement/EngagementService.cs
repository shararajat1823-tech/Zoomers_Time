using Microsoft.EntityFrameworkCore;
using Zoomers.Application.Abstractions;
using Zoomers.Application.Engagement.Dtos;
using Zoomers.Application.Posts;
using Zoomers.Application.Posts.Dtos;
using Zoomers.Domain.Engagement;
using Zoomers.Domain.Posts;
using Zoomers.Domain.Profiles;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Engagement;

public sealed class EngagementService : IEngagementService
{
    private readonly IApplicationDbContext _db;
    private readonly PostService _posts;
    private readonly IClock _clock;

    private static readonly Error PostNotFound = Error.NotFound("post.not_found", "Post not found.");

    public EngagementService(IApplicationDbContext db, PostService posts, IClock clock)
    {
        _db = db;
        _posts = posts;
        _clock = clock;
    }

    // ---- post likes ----

    public async Task<Result<LikeState>> LikePostAsync(Guid userId, Guid postId, CancellationToken ct = default)
    {
        var post = await LoadViewablePostAsync(userId, postId, ct);
        if (post is null) return PostNotFound;

        if (await _db.PostLikes.AnyAsync(l => l.PostId == postId && l.UserId == userId, ct))
            return Result.Success(new LikeState(true, post.LikeCount));

        try
        {
            await _db.ExecuteInTransactionAsync(async token =>
            {
                _db.PostLikes.Add(new PostLike { PostId = postId, UserId = userId, CreatedAt = _clock.UtcNow });
                post.LikeCount += 1;
                await _db.SaveChangesAsync(token);
            }, ct);
        }
        catch (DbUpdateException) // raced with a concurrent like; the unique PK won — treat as already liked.
        {
            var count = await _db.Posts.Where(p => p.Id == postId).Select(p => p.LikeCount).FirstOrDefaultAsync(ct);
            return Result.Success(new LikeState(true, count));
        }

        return Result.Success(new LikeState(true, post.LikeCount));
    }

    public async Task<Result<LikeState>> UnlikePostAsync(Guid userId, Guid postId, CancellationToken ct = default)
    {
        var post = await _db.Posts.FirstOrDefaultAsync(p => p.Id == postId, ct);
        if (post is null) return PostNotFound;

        var like = await _db.PostLikes.FirstOrDefaultAsync(l => l.PostId == postId && l.UserId == userId, ct);
        if (like is null)
            return Result.Success(new LikeState(false, post.LikeCount));

        await _db.ExecuteInTransactionAsync(async token =>
        {
            _db.PostLikes.Remove(like);
            post.LikeCount = Math.Max(0, post.LikeCount - 1);
            await _db.SaveChangesAsync(token);
        }, ct);

        return Result.Success(new LikeState(false, post.LikeCount));
    }

    // ---- saves ----

    public async Task<Result<SaveState>> SavePostAsync(Guid userId, Guid postId, CancellationToken ct = default)
    {
        var post = await LoadViewablePostAsync(userId, postId, ct);
        if (post is null) return PostNotFound;

        if (!await _db.SavedPosts.AnyAsync(s => s.UserId == userId && s.PostId == postId, ct))
        {
            try
            {
                _db.SavedPosts.Add(new SavedPost { UserId = userId, PostId = postId, CreatedAt = _clock.UtcNow });
                await _db.SaveChangesAsync(ct);
            }
            catch (DbUpdateException) { /* already saved (raced) */ }
        }
        return Result.Success(new SaveState(true));
    }

    public async Task<Result<SaveState>> UnsavePostAsync(Guid userId, Guid postId, CancellationToken ct = default)
    {
        var save = await _db.SavedPosts.FirstOrDefaultAsync(s => s.UserId == userId && s.PostId == postId, ct);
        if (save is not null)
        {
            _db.SavedPosts.Remove(save);
            await _db.SaveChangesAsync(ct);
        }
        return Result.Success(new SaveState(false));
    }

    // ---- comments ----

    public async Task<Result<CommentView>> AddCommentAsync(Guid userId, Guid postId, AddCommentRequest request, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(request.Body))
            return Error.Validation("comment.empty", "A comment cannot be empty.");

        var post = await LoadViewablePostAsync(userId, postId, ct);
        if (post is null) return PostNotFound;

        if (!await CanCommentAsync(userId, post.AuthorId, ct))
            return Error.Forbidden("comment.not_allowed", "You cannot comment on this post.");

        if (request.ParentCommentId is { } parentId)
        {
            var parentOk = await _db.Comments.AnyAsync(c => c.Id == parentId && c.PostId == postId, ct);
            if (!parentOk)
                return Error.Validation("comment.invalid_parent", "The comment being replied to was not found.");
        }

        var comment = new Comment
        {
            PostId = postId,
            AuthorId = userId,
            ParentCommentId = request.ParentCommentId,
            Body = request.Body.Trim(),
            CreatedAt = _clock.UtcNow
        };

        await _db.ExecuteInTransactionAsync(async token =>
        {
            _db.Comments.Add(comment);
            post.CommentCount += 1;
            await _db.SaveChangesAsync(token);
        }, ct);

        var views = await MapCommentsAsync(new List<Comment> { comment }, userId, ct);
        return Result.Success(views[0]);
    }

    public async Task<Result> DeleteCommentAsync(Guid userId, Guid commentId, CancellationToken ct = default)
    {
        var comment = await _db.Comments.FirstOrDefaultAsync(c => c.Id == commentId, ct);
        if (comment is null)
            return Error.NotFound("comment.not_found", "Comment not found.");

        var postOwnerId = await _db.Posts.Where(p => p.Id == comment.PostId).Select(p => p.AuthorId).FirstOrDefaultAsync(ct);
        // Author of the comment OR the post owner may delete it.
        if (comment.AuthorId != userId && postOwnerId != userId)
            return Error.Forbidden("comment.not_allowed", "You cannot delete this comment.");

        await _db.ExecuteInTransactionAsync(async token =>
        {
            comment.DeletedAt = _clock.UtcNow;
            var post = await _db.Posts.FirstOrDefaultAsync(p => p.Id == comment.PostId, token);
            if (post is not null) post.CommentCount = Math.Max(0, post.CommentCount - 1);
            await _db.SaveChangesAsync(token);
        }, ct);

        return Result.Success();
    }

    public async Task<Result<CursorPage<CommentView>>> GetCommentsAsync(Guid viewerId, Guid postId, Guid? parentCommentId, string? cursor, int limit, CancellationToken ct = default)
    {
        var post = await LoadViewablePostAsync(viewerId, postId, ct);
        if (post is null) return PostNotFound;

        limit = PageLimit.Clamp(limit);
        var q = _db.Comments.Where(c => c.PostId == postId && c.ParentCommentId == parentCommentId);
        if (KeysetCursor.TryParse(cursor, out var k))
            q = q.Where(c => c.CreatedAt < k.CreatedAt || (c.CreatedAt == k.CreatedAt && c.Id < k.Id));

        var comments = await q
            .OrderByDescending(c => c.CreatedAt).ThenByDescending(c => c.Id)
            .Take(limit + 1)
            .Include(c => c.Author!).ThenInclude(a => a.Profile)
            .ToListAsync(ct);

        var hasMore = comments.Count > limit;
        var page = comments.Take(limit).ToList();
        var views = await MapCommentsAsync(page, viewerId, ct);

        string? next = hasMore && page.Count > 0
            ? new KeysetCursor(page[^1].CreatedAt, page[^1].Id).Encode()
            : null;

        return Result.Success(new CursorPage<CommentView>(views, next));
    }

    // ---- comment likes ----

    public async Task<Result<LikeState>> LikeCommentAsync(Guid userId, Guid commentId, CancellationToken ct = default)
    {
        var comment = await LoadViewableCommentAsync(userId, commentId, ct);
        if (comment is null) return Error.NotFound("comment.not_found", "Comment not found.");

        if (await _db.CommentLikes.AnyAsync(l => l.CommentId == commentId && l.UserId == userId, ct))
            return Result.Success(new LikeState(true, comment.LikeCount));

        try
        {
            await _db.ExecuteInTransactionAsync(async token =>
            {
                _db.CommentLikes.Add(new CommentLike { CommentId = commentId, UserId = userId, CreatedAt = _clock.UtcNow });
                comment.LikeCount += 1;
                await _db.SaveChangesAsync(token);
            }, ct);
        }
        catch (DbUpdateException)
        {
            var count = await _db.Comments.Where(c => c.Id == commentId).Select(c => c.LikeCount).FirstOrDefaultAsync(ct);
            return Result.Success(new LikeState(true, count));
        }

        return Result.Success(new LikeState(true, comment.LikeCount));
    }

    public async Task<Result<LikeState>> UnlikeCommentAsync(Guid userId, Guid commentId, CancellationToken ct = default)
    {
        var comment = await _db.Comments.FirstOrDefaultAsync(c => c.Id == commentId, ct);
        if (comment is null) return Error.NotFound("comment.not_found", "Comment not found.");

        var like = await _db.CommentLikes.FirstOrDefaultAsync(l => l.CommentId == commentId && l.UserId == userId, ct);
        if (like is null)
            return Result.Success(new LikeState(false, comment.LikeCount));

        await _db.ExecuteInTransactionAsync(async token =>
        {
            _db.CommentLikes.Remove(like);
            comment.LikeCount = Math.Max(0, comment.LikeCount - 1);
            await _db.SaveChangesAsync(token);
        }, ct);

        return Result.Success(new LikeState(false, comment.LikeCount));
    }

    // ---- helpers ----

    private async Task<Post?> LoadViewablePostAsync(Guid viewerId, Guid postId, CancellationToken ct)
    {
        var post = await _db.Posts.FirstOrDefaultAsync(p => p.Id == postId, ct);
        if (post is null) return null;
        return await _posts.CanViewPostAsync(viewerId, post.AuthorId, post.Visibility, ct) ? post : null;
    }

    private async Task<Comment?> LoadViewableCommentAsync(Guid viewerId, Guid commentId, CancellationToken ct)
    {
        var comment = await _db.Comments.FirstOrDefaultAsync(c => c.Id == commentId, ct);
        if (comment is null) return null;
        var post = await _db.Posts.FirstOrDefaultAsync(p => p.Id == comment.PostId, ct);
        if (post is null) return null;
        return await _posts.CanViewPostAsync(viewerId, post.AuthorId, post.Visibility, ct) ? comment : null;
    }

    private async Task<bool> CanCommentAsync(Guid userId, Guid authorId, CancellationToken ct)
    {
        if (userId == authorId) return true;
        var control = await _db.UserSettings.Where(s => s.UserId == authorId)
            .Select(s => (AudienceControl?)s.WhoCanComment).FirstOrDefaultAsync(ct) ?? AudienceControl.Everyone;

        return control switch
        {
            AudienceControl.Everyone => true,
            AudienceControl.FollowersOnly => await _db.Follows.AnyAsync(f => f.FollowerId == userId && f.FolloweeId == authorId, ct),
            _ => false
        };
    }

    private async Task<List<CommentView>> MapCommentsAsync(List<Comment> comments, Guid viewerId, CancellationToken ct)
    {
        if (comments.Count == 0) return new List<CommentView>();

        var ids = comments.Select(c => c.Id).ToList();
        var likedSet = (await _db.CommentLikes.Where(l => l.UserId == viewerId && ids.Contains(l.CommentId))
            .Select(l => l.CommentId).ToListAsync(ct)).ToHashSet();

        var replyCounts = (await _db.Comments.Where(c => c.ParentCommentId != null && ids.Contains(c.ParentCommentId!.Value))
            .GroupBy(c => c.ParentCommentId!.Value)
            .Select(g => new { ParentId = g.Key, Count = g.Count() })
            .ToListAsync(ct)).ToDictionary(x => x.ParentId, x => x.Count);

        return comments.Select(c =>
        {
            var profile = c.Author?.Profile;
            return new CommentView(
                c.Id,
                new PostAuthor(c.AuthorId, c.Author?.Username ?? string.Empty, profile?.DisplayName ?? string.Empty, profile?.IsVerified ?? false),
                c.Body,
                c.ParentCommentId,
                c.CreatedAt,
                c.LikeCount,
                replyCounts.TryGetValue(c.Id, out var rc) ? rc : 0,
                likedSet.Contains(c.Id));
        }).ToList();
    }
}
