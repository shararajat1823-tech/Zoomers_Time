namespace Zoomers.Application.Stories.Dtos;

public sealed record CreateStoryRequest(Guid UploadSessionId);

public sealed record StoryAuthorDto(
    Guid Id,
    string Username,
    string DisplayName,
    string? AvatarUrl,
    bool IsVerified);

public sealed record StoryItemDto(
    Guid Id,
    string MediaUrl,
    DateTimeOffset CreatedAt,
    bool Viewed);

/// <summary>One author's active stories, grouped for the stories rail/viewer.</summary>
public sealed record StoryTrayDto(
    StoryAuthorDto Author,
    IReadOnlyList<StoryItemDto> Stories,
    bool HasUnseen,
    bool IsSelf);
