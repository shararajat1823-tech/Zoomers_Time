namespace Zoomers.Application.Abstractions;

/// <summary>Kind of push, mapped to the recipient's per-category push settings.</summary>
public enum PushCategory
{
    Generic,
    Like,
    Comment,
    Follow,
    Message
}

public sealed record PushMessage(
    string Title,
    string Body,
    PushCategory Category = PushCategory.Generic,
    IReadOnlyDictionary<string, string>? Data = null);

/// <summary>
/// Sends push notifications to a user's registered device tokens. The default
/// implementation logs only (no external account required); production swaps in
/// FCM. Implementations honor the recipient's per-category push settings and
/// never throw — a push failure must not break the operation that triggered it.
/// </summary>
public interface IPushSender
{
    Task SendAsync(Guid userId, PushMessage message, CancellationToken ct = default);
}
