using Zoomers.Domain.Profiles;

namespace Zoomers.Application.Settings.Dtos;

public sealed record SettingsView(
    ThemePreference Theme,
    string Language,
    AudienceControl WhoCanComment,
    AudienceControl WhoCanMention,
    AudienceControl WhoCanMessage,
    bool ShowOnlineStatus,
    bool PushLikes,
    bool PushComments,
    bool PushFollows,
    bool PushMessages);

/// <summary>Partial settings update. Null fields are left unchanged.</summary>
public sealed record UpdateSettingsRequest(
    ThemePreference? Theme,
    string? Language,
    AudienceControl? WhoCanComment,
    AudienceControl? WhoCanMention,
    AudienceControl? WhoCanMessage,
    bool? ShowOnlineStatus,
    bool? PushLikes,
    bool? PushComments,
    bool? PushFollows,
    bool? PushMessages);
