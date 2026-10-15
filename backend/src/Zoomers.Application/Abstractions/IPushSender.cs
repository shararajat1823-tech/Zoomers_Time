namespace Zoomers.Application.Abstractions;

public sealed record PushMessage(string Title, string Body, IReadOnlyDictionary<string, string>? Data = null);

/// <summary>
/// Sends push notifications to a user's registered device tokens. The first
/// implementation logs only (no external account required); production swaps in
/// FCM. Sensitive message content is never included unless settings allow it.
/// </summary>
public interface IPushSender
{
    Task SendAsync(Guid userId, PushMessage message, CancellationToken ct = default);
}
