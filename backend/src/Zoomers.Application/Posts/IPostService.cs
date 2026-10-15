using Zoomers.Application.Posts.Dtos;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Posts;

public interface IPostService
{
    Task<Result<PostView>> CreateAsync(Guid authorId, CreatePostRequest request, CancellationToken ct = default);
    Task<Result<PostView>> GetAsync(Guid viewerId, Guid postId, CancellationToken ct = default);
    Task<Result> DeleteAsync(Guid userId, Guid postId, CancellationToken ct = default);

    /// <summary>A user's post grid, gated by privacy/blocks.</summary>
    Task<Result<CursorPage<PostView>>> GetUserPostsAsync(Guid viewerId, Guid targetUserId, string? cursor, int limit, CancellationToken ct = default);

    /// <summary>The caller's saved posts.</summary>
    Task<Result<CursorPage<PostView>>> GetSavedAsync(Guid userId, string? cursor, int limit, CancellationToken ct = default);
}
