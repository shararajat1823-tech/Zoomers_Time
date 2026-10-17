using Zoomers.Domain.Notifications;

namespace Zoomers.Application.Notifications.Dtos;

public sealed record NotificationActor(
    Guid Id,
    string Username,
    string DisplayName,
    string? AvatarUrl,
    bool IsVerified);

public sealed record NotificationView(
    Guid Id,
    NotificationType Type,
    NotificationActor Actor,
    Guid? PostId,
    string? PostThumbnailUrl,
    DateTimeOffset CreatedAt,
    bool Read);
