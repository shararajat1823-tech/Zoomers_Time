using Asp.Versioning;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Zoomers.Api.Common;
using Zoomers.Application.Stories;
using Zoomers.Application.Stories.Dtos;

namespace Zoomers.Api.Controllers;

[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/stories")]
[Authorize]
public sealed class StoriesController : ApiControllerBase
{
    private readonly IStoryService _stories;
    public StoriesController(IStoryService stories) => _stories = stories;

    /// <summary>Publishes a 24-hour story from a completed StoryMedia upload session.</summary>
    [HttpPost]
    public async Task<IActionResult> Create(CreateStoryRequest request, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _stories.CreateAsync(userId, request, ct)).ToResponse();
    }

    /// <summary>Active stories from the caller and the accounts they follow.</summary>
    [HttpGet("feed")]
    public async Task<IActionResult> Feed(CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _stories.GetFeedAsync(userId, ct)).ToResponse();
    }

    /// <summary>Marks a story as seen by the caller.</summary>
    [HttpPost("{storyId:guid}/view")]
    public async Task<IActionResult> View(Guid storyId, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _stories.MarkViewedAsync(userId, storyId, ct)).ToResponse();
    }
}
