namespace Zoomers.Application.Reels.Dtos;

/// <summary>
/// Publishes a reel from a completed ReelVideo upload. An optional poster image
/// (a ReelThumbnail upload) improves the grid/first-paint; width/height/duration
/// come from the client's probe of the picked video.
/// </summary>
public sealed record CreateReelRequest(
    Guid UploadSessionId,
    Guid? ThumbnailUploadSessionId,
    string? Caption,
    int Width,
    int Height,
    int DurationMs);

public sealed record ReelAuthorDto(
    Guid Id,
    string Username,
    string DisplayName,
    string? AvatarUrl,
    bool IsVerified);

public sealed record ReelView(
    Guid Id,
    ReelAuthorDto Author,
    string MediaUrl,
    string? ThumbnailUrl,
    string? Caption,
    int Width,
    int Height,
    int DurationMs,
    int LikeCount,
    bool ViewerHasLiked,
    DateTimeOffset CreatedAt);

public sealed record ReelLikeState(bool Liked, int LikeCount);
