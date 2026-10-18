using Zoomers.Domain.Common;
using Zoomers.Domain.Identity;

namespace Zoomers.Domain.Push;

/// <summary>
/// A push-notification target: one registered device (FCM registration token)
/// belonging to a user. A user may have several (phone, tablet). Tokens are
/// unique; re-registering an existing token just re-points it at the current
/// user and refreshes <see cref="LastSeenAt"/>.
/// </summary>
public class DeviceToken : Entity
{
    public Guid UserId { get; set; }
    public User? User { get; set; }

    /// <summary>The FCM registration token for this device/app install.</summary>
    public string Token { get; set; } = string.Empty;

    /// <summary>Platform hint, e.g. "android" or "ios".</summary>
    public string Platform { get; set; } = "android";

    public DateTimeOffset LastSeenAt { get; set; }
}
