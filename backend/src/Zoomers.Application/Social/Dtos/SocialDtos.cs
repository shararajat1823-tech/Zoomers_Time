namespace Zoomers.Application.Social.Dtos;

/// <summary>Outcome of a follow attempt.</summary>
public enum FollowOutcome { Following, Requested, AlreadyFollowing, AlreadyRequested }

public sealed record FollowResult(FollowOutcome Outcome);

/// <summary>A user row in a followers/following list.</summary>
public sealed record UserListItem(
    Guid Id,
    string Username,
    string DisplayName,
    bool IsVerified,
    bool IsPrivate);

/// <summary>A pending follow request shown to the target account.</summary>
public sealed record FollowRequestItem(
    Guid RequestId,
    Guid RequesterId,
    string Username,
    string DisplayName,
    DateTimeOffset CreatedAt);

/// <summary>
/// The viewer's relationship to another user — drives button state in the UI
/// and is always computed server-side.
/// </summary>
public sealed record RelationshipState(
    bool IsSelf,
    bool IsFollowing,
    bool IsFollowedBy,
    bool HasOutgoingRequest,
    bool HasIncomingRequest,
    bool IsBlocking,
    bool IsBlockedBy);
