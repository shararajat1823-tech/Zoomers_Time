using Zoomers.Application.Posts.Dtos;

namespace Zoomers.Application.Engagement.Dtos;

public sealed record LikeState(bool Liked, int LikeCount);
public sealed record SaveState(bool Saved);

public sealed record AddCommentRequest(string Body, Guid? ParentCommentId);

public sealed record CommentView(
    Guid Id,
    PostAuthor Author,
    string Body,
    Guid? ParentCommentId,
    DateTimeOffset CreatedAt,
    int LikeCount,
    int ReplyCount,
    bool ViewerHasLiked);
