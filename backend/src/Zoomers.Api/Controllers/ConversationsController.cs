using Asp.Versioning;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Zoomers.Api.Common;
using Zoomers.Application.Messaging;
using Zoomers.Application.Messaging.Dtos;

namespace Zoomers.Api.Controllers;

[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/conversations")]
[Authorize]
public sealed class ConversationsController : ApiControllerBase
{
    private readonly IMessagingService _messaging;
    public ConversationsController(IMessagingService messaging) => _messaging = messaging;

    /// <summary>Gets or creates the 1:1 conversation with another user.</summary>
    [HttpPost]
    public async Task<IActionResult> Start(StartConversationRequest request, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _messaging.StartAsync(userId, request, ct)).ToResponse();
    }

    /// <summary>The caller's conversations, most-recently-active first.</summary>
    [HttpGet]
    public async Task<IActionResult> List([FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _messaging.GetConversationsAsync(userId, cursor, limit, ct)).ToResponse();
    }

    /// <summary>Total unread messages across all conversations (inbox badge).</summary>
    [HttpGet("unread-count")]
    public async Task<IActionResult> UnreadCount(CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _messaging.GetUnreadCountAsync(userId, ct)).ToResponse();
    }

    /// <summary>Messages in a conversation, newest first.</summary>
    [HttpGet("{conversationId:guid}/messages")]
    public async Task<IActionResult> Messages(Guid conversationId, [FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _messaging.GetMessagesAsync(userId, conversationId, cursor, limit, ct)).ToResponse();
    }

    /// <summary>Sends a message to a conversation.</summary>
    [HttpPost("{conversationId:guid}/messages")]
    public async Task<IActionResult> Send(Guid conversationId, SendMessageRequest request, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _messaging.SendMessageAsync(userId, conversationId, request, ct)).ToResponse();
    }

    /// <summary>Marks all received messages in a conversation as read.</summary>
    [HttpPost("{conversationId:guid}/read")]
    public async Task<IActionResult> Read(Guid conversationId, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _messaging.MarkReadAsync(userId, conversationId, ct)).ToResponse();
    }
}
