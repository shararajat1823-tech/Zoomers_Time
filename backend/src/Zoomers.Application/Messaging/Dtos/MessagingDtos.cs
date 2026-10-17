namespace Zoomers.Application.Messaging.Dtos;

/// <summary>The other person in a conversation, for the inbox and chat header.</summary>
public sealed record ChatUserDto(
    Guid Id,
    string Username,
    string DisplayName,
    string? AvatarUrl,
    bool IsVerified);

/// <summary>One inbox row: the other participant, a preview and the unread count.</summary>
public sealed record ConversationDto(
    Guid Id,
    ChatUserDto OtherUser,
    string? LastMessagePreview,
    DateTimeOffset LastMessageAt,
    int UnreadCount);

/// <summary>A single message in a thread. <paramref name="IsMine"/> drives bubble alignment.</summary>
public sealed record MessageDto(
    Guid Id,
    Guid ConversationId,
    Guid SenderId,
    string Body,
    DateTimeOffset CreatedAt,
    DateTimeOffset? ReadAt,
    bool IsMine);

public sealed record StartConversationRequest(Guid RecipientUserId);

public sealed record SendMessageRequest(string Body);

public sealed record UnreadCountDto(int Count);
