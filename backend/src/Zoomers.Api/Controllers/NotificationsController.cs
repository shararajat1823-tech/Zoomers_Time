using Asp.Versioning;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Zoomers.Api.Common;
using Zoomers.Application.Notifications;

namespace Zoomers.Api.Controllers;

[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/notifications")]
[Authorize]
public sealed class NotificationsController : ApiControllerBase
{
    private readonly INotificationService _notifications;
    public NotificationsController(INotificationService notifications) => _notifications = notifications;

    /// <summary>The caller's activity feed (likes, comments, follows), newest first.</summary>
    [HttpGet]
    public async Task<IActionResult> Get([FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _notifications.GetAsync(userId, cursor, limit, ct)).ToResponse();
    }

    /// <summary>Marks all of the caller's notifications as read.</summary>
    [HttpPost("read")]
    public async Task<IActionResult> MarkRead(CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _notifications.MarkAllReadAsync(userId, ct)).ToResponse();
    }
}
