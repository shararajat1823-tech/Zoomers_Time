using Zoomers.Domain.Common;
using Zoomers.Domain.Identity;

namespace Zoomers.Domain.Profiles;

public enum ThemePreference { System = 0, Light = 1, Dark = 2 }

/// <summary>Audience that may perform an interaction (comment, mention, message).</summary>
public enum AudienceControl { Everyone = 0, FollowersOnly = 1, NoOne = 2 }

/// <summary>User-controlled settings (1:1 with <see cref="User"/>), enforced server-side.</summary>
public class UserSettings : AuditableEntity
{
    public Guid UserId { get; set; }
    public User? User { get; set; }

    public ThemePreference Theme { get; set; } = ThemePreference.System;
    public string Language { get; set; } = "en";

    public AudienceControl WhoCanComment { get; set; } = AudienceControl.Everyone;
    public AudienceControl WhoCanMention { get; set; } = AudienceControl.Everyone;
    public AudienceControl WhoCanMessage { get; set; } = AudienceControl.Everyone;

    public bool ShowOnlineStatus { get; set; } = true;

    public bool PushLikes { get; set; } = true;
    public bool PushComments { get; set; } = true;
    public bool PushFollows { get; set; } = true;
    public bool PushMessages { get; set; } = true;
}
