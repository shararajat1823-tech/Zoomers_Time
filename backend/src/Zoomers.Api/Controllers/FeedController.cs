using Asp.Versioning;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Zoomers.Api.Common;
using Zoomers.Application.Posts;

namespace Zoomers.Api.Controllers;

[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/feed")]
[Authorize]
public sealed class FeedController : ApiControllerBase
{
    private readonly IFeedService _feed;
    public FeedController(IFeedService feed) => _feed = feed;

    /// <summary>Reverse-chronological feed of posts from accounts the caller follows.</summary>
    [HttpGet("following")]
    public async Task<IActionResult> Following([FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _feed.GetFollowingFeedAsync(userId, cursor, limit, ct)).ToResponse();
    }
}
