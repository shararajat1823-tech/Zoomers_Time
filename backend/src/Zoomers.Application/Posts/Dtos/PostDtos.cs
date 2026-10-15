using Zoomers.Domain.Posts;

namespace Zoomers.Application.Posts.Dtos;

public sealed record CreatePostMediaItem(Guid UploadSessionId, int? Width, int? Height, int? DurationMs);

public sealed record CreatePostRequest(
    string? Caption,
    PostVisibility Visibility,
    string? LocationName,
    string? AccessibilityText,
    IReadOnlyList<CreatePostMediaItem> Media);

public sealed record PostAuthor(Guid Id, string Username, string DisplayName, bool IsVerified);

public sealed record PostMediaView(
    Guid Id,
    MediaType Type,
    string Url,
    string? ThumbnailUrl,
    int? Width,
    int? Height,
    int? DurationMs,
    int OrderIndex);

public sealed record ViewerPostState(bool HasLiked, bool HasSaved);

public sealed record PostView(
    Guid Id,
    PostAuthor Author,
    PostType Type,
    PostVisibility Visibility,
    string? Caption,
    string? LocationName,
    string? AccessibilityText,
    DateTimeOffset CreatedAt,
    int LikeCount,
    int CommentCount,
    IReadOnlyList<PostMediaView> Media,
    ViewerPostState Viewer);
