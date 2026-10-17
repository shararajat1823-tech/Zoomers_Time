using Zoomers.Application.Notifications.Dtos;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Notifications;

public interface INotificationService
{
    /// <summary>The authenticated user's activity feed, newest first.</summary>
    Task<Result<CursorPage<NotificationView>>> GetAsync(Guid userId, string? cursor, int limit, CancellationToken ct = default);

    /// <summary>Marks all of the user's unread notifications as read.</summary>
    Task<Result> MarkAllReadAsync(Guid userId, CancellationToken ct = default);
}
