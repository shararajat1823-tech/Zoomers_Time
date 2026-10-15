using Zoomers.Application.Social.Dtos;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Social;

/// <summary>
/// Owns the following graph and block relationships. Enforces private-account
/// rules (a pending request grants no access) and block rules (mutual
/// invisibility) server-side. Counters are kept consistent inside transactions.
/// </summary>
public interface ISocialGraphService
{
    Task<Result<FollowResult>> FollowAsync(Guid actorId, Guid targetUserId, CancellationToken ct = default);
    Task<Result> UnfollowAsync(Guid actorId, Guid targetUserId, CancellationToken ct = default);
    Task<Result> CancelFollowRequestAsync(Guid actorId, Guid targetUserId, CancellationToken ct = default);

    /// <summary>Accept or reject a pending request where the actor is the target.</summary>
    Task<Result> RespondToRequestAsync(Guid actorId, Guid requestId, bool accept, CancellationToken ct = default);

    Task<Result> RemoveFollowerAsync(Guid actorId, Guid followerUserId, CancellationToken ct = default);

    Task<Result<CursorPage<UserListItem>>> GetFollowersAsync(Guid viewerId, Guid targetUserId, string? cursor, int limit, CancellationToken ct = default);
    Task<Result<CursorPage<UserListItem>>> GetFollowingAsync(Guid viewerId, Guid targetUserId, string? cursor, int limit, CancellationToken ct = default);
    Task<Result<CursorPage<FollowRequestItem>>> GetIncomingRequestsAsync(Guid actorId, string? cursor, int limit, CancellationToken ct = default);

    Task<Result> BlockAsync(Guid actorId, Guid targetUserId, CancellationToken ct = default);
    Task<Result> UnblockAsync(Guid actorId, Guid targetUserId, CancellationToken ct = default);

    /// <summary>Computes the viewer's relationship to a target (server-side, for UI state).</summary>
    Task<RelationshipState> GetRelationshipAsync(Guid viewerId, Guid targetUserId, CancellationToken ct = default);

    /// <summary>True if a block exists in either direction between the two users.</summary>
    Task<bool> IsBlockedEitherWayAsync(Guid a, Guid b, CancellationToken ct = default);

    /// <summary>True if the viewer may see the target's protected content (own, public, or accepted follower).</summary>
    Task<bool> CanViewContentAsync(Guid viewerId, Guid targetUserId, CancellationToken ct = default);

    /// <summary>When an account switches to public, approve all its pending incoming requests as follows.</summary>
    Task ConvertPendingRequestsToFollowsAsync(Guid targetId, CancellationToken ct = default);

    Task<Result> MuteAsync(Guid muterId, Guid mutedId, bool mutePosts, bool muteStories, CancellationToken ct = default);
    Task<Result> UnmuteAsync(Guid muterId, Guid mutedId, CancellationToken ct = default);
}
