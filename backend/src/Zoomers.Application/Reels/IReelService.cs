using Zoomers.Application.Reels.Dtos;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Reels;

public interface IReelService
{
    /// <summary>Publishes a reel from a completed ReelVideo upload session.</summary>
    Task<Result<ReelView>> CreateAsync(Guid userId, CreateReelRequest request, CancellationToken ct = default);

    /// <summary>Vertical discovery feed of recent public reels (newest first).</summary>
    Task<Result<CursorPage<ReelView>>> GetFeedAsync(Guid userId, string? cursor, int limit, CancellationToken ct = default);

    /// <summary>Reels authored by a specific user (for their profile).</summary>
    Task<Result<CursorPage<ReelView>>> GetUserReelsAsync(Guid viewerId, Guid authorId, string? cursor, int limit, CancellationToken ct = default);

    Task<Result<ReelLikeState>> LikeAsync(Guid userId, Guid reelId, CancellationToken ct = default);
    Task<Result<ReelLikeState>> UnlikeAsync(Guid userId, Guid reelId, CancellationToken ct = default);

    /// <summary>Deletes the caller's own reel (soft delete).</summary>
    Task<Result> DeleteAsync(Guid userId, Guid reelId, CancellationToken ct = default);
}
