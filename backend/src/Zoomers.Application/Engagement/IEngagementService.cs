using Zoomers.Application.Engagement.Dtos;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Engagement;

public interface IEngagementService
{
    Task<Result<LikeState>> LikePostAsync(Guid userId, Guid postId, CancellationToken ct = default);
    Task<Result<LikeState>> UnlikePostAsync(Guid userId, Guid postId, CancellationToken ct = default);

    Task<Result<SaveState>> SavePostAsync(Guid userId, Guid postId, CancellationToken ct = default);
    Task<Result<SaveState>> UnsavePostAsync(Guid userId, Guid postId, CancellationToken ct = default);

    Task<Result<CommentView>> AddCommentAsync(Guid userId, Guid postId, AddCommentRequest request, CancellationToken ct = default);
    Task<Result> DeleteCommentAsync(Guid userId, Guid commentId, CancellationToken ct = default);
    Task<Result<CursorPage<CommentView>>> GetCommentsAsync(Guid viewerId, Guid postId, Guid? parentCommentId, string? cursor, int limit, CancellationToken ct = default);

    Task<Result<LikeState>> LikeCommentAsync(Guid userId, Guid commentId, CancellationToken ct = default);
    Task<Result<LikeState>> UnlikeCommentAsync(Guid userId, Guid commentId, CancellationToken ct = default);
}
