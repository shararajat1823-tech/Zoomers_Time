using Zoomers.Application.Messaging.Dtos;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Messaging;

public interface IMessagingService
{
    /// <summary>Gets or creates the 1:1 conversation between the caller and a recipient.</summary>
    Task<Result<ConversationDto>> StartAsync(Guid userId, StartConversationRequest request, CancellationToken ct = default);

    /// <summary>The caller's conversations, most-recently-active first.</summary>
    Task<Result<CursorPage<ConversationDto>>> GetConversationsAsync(Guid userId, string? cursor, int limit, CancellationToken ct = default);

    /// <summary>Messages in a conversation the caller belongs to, newest first.</summary>
    Task<Result<CursorPage<MessageDto>>> GetMessagesAsync(Guid userId, Guid conversationId, string? cursor, int limit, CancellationToken ct = default);

    /// <summary>Sends a message to a conversation the caller belongs to.</summary>
    Task<Result<MessageDto>> SendMessageAsync(Guid userId, Guid conversationId, SendMessageRequest request, CancellationToken ct = default);

    /// <summary>Marks every message the caller has received in a conversation as read.</summary>
    Task<Result> MarkReadAsync(Guid userId, Guid conversationId, CancellationToken ct = default);

    /// <summary>Total unread messages across all of the caller's conversations (inbox badge).</summary>
    Task<Result<UnreadCountDto>> GetUnreadCountAsync(Guid userId, CancellationToken ct = default);
}
