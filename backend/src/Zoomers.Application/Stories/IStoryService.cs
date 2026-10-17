using Zoomers.Application.Stories.Dtos;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Stories;

public interface IStoryService
{
    /// <summary>Publishes a 24-hour story from a completed StoryMedia upload session.</summary>
    Task<Result> CreateAsync(Guid userId, CreateStoryRequest request, CancellationToken ct = default);

    /// <summary>Active stories from the caller and the accounts they follow, grouped by author.</summary>
    Task<Result<IReadOnlyList<StoryTrayDto>>> GetFeedAsync(Guid userId, CancellationToken ct = default);

    /// <summary>Marks a story as seen by the caller.</summary>
    Task<Result> MarkViewedAsync(Guid userId, Guid storyId, CancellationToken ct = default);
}
