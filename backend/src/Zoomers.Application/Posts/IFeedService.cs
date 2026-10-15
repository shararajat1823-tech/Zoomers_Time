using Zoomers.Application.Posts.Dtos;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Posts;

public interface IFeedService
{
    /// <summary>Chronological feed of posts from accounts the user follows (cursor-paginated).</summary>
    Task<Result<CursorPage<PostView>>> GetFollowingFeedAsync(Guid userId, string? cursor, int limit, CancellationToken ct = default);
}
