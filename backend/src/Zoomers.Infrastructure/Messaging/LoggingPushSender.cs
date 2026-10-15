using Microsoft.Extensions.Logging;
using Zoomers.Application.Abstractions;

namespace Zoomers.Infrastructure.Messaging;

/// <summary>
/// Development push sender that logs instead of delivering (no FCM account
/// required). Production swaps in an FCM-backed implementation. Sensitive
/// content is never included in the payload.
/// </summary>
public sealed class LoggingPushSender : IPushSender
{
    private readonly ILogger<LoggingPushSender> _logger;
    public LoggingPushSender(ILogger<LoggingPushSender> logger) => _logger = logger;

    public Task SendAsync(Guid userId, PushMessage message, CancellationToken ct = default)
    {
        _logger.LogInformation("Push to {UserId}: {Title}", userId, message.Title);
        return Task.CompletedTask;
    }
}
