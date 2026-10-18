using Zoomers.Application.Posts.Dtos;
using Zoomers.Application.Search.Dtos;
using Zoomers.Application.Social.Dtos;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Search;

public interface ISearchService
{
    Task<Result<CursorPage<UserListItem>>> SearchUsersAsync(Guid viewerId, string query, string? cursor, int limit, CancellationToken ct = default);
    Task<Result<CursorPage<HashtagResult>>> SearchHashtagsAsync(string query, string? cursor, int limit, CancellationToken ct = default);
    Task<Result<CursorPage<PostView>>> SearchPostsAsync(Guid viewerId, string query, string? cursor, int limit, CancellationToken ct = default);

    /// <summary>Public posts tagged with a hashtag (newest first).</summary>
    Task<Result<CursorPage<PostView>>> GetHashtagPostsAsync(Guid viewerId, string tag, string? cursor, int limit, CancellationToken ct = default);

    /// <summary>Suggested accounts to follow (not followed, not blocked, popular first).</summary>
    Task<Result<CursorPage<UserListItem>>> SuggestedUsersAsync(Guid viewerId, string? cursor, int limit, CancellationToken ct = default);

    /// <summary>Discovery grid: recent public posts from across the platform (excludes the caller's own and muted authors).</summary>
    Task<Result<CursorPage<PostView>>> ExploreAsync(Guid viewerId, string? cursor, int limit, CancellationToken ct = default);
}
