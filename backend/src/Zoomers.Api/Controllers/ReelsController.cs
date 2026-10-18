using Asp.Versioning;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Zoomers.Api.Common;
using Zoomers.Application.Reels;
using Zoomers.Application.Reels.Dtos;

namespace Zoomers.Api.Controllers;

[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/reels")]
[Authorize]
public sealed class ReelsController : ApiControllerBase
{
    private readonly IReelService _reels;
    public ReelsController(IReelService reels) => _reels = reels;

    /// <summary>Publishes a reel from a completed ReelVideo upload session.</summary>
    [HttpPost]
    public async Task<IActionResult> Create(CreateReelRequest request, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _reels.CreateAsync(userId, request, ct)).ToResponse(StatusCodes.Status201Created);
    }

    /// <summary>Vertical discovery feed of recent public reels.</summary>
    [HttpGet("feed")]
    public async Task<IActionResult> Feed([FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _reels.GetFeedAsync(userId, cursor, limit, ct)).ToResponse();
    }

    /// <summary>Reels authored by a user (their profile).</summary>
    [HttpGet("~/api/v{version:apiVersion}/users/{userId:guid}/reels")]
    public async Task<IActionResult> UserReels(Guid userId, [FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } viewerId) return Unauthorized();
        return (await _reels.GetUserReelsAsync(viewerId, userId, cursor, limit, ct)).ToResponse();
    }

    [HttpPost("{reelId:guid}/likes")]
    public async Task<IActionResult> Like(Guid reelId, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _reels.LikeAsync(userId, reelId, ct)).ToResponse();
    }

    [HttpDelete("{reelId:guid}/likes")]
    public async Task<IActionResult> Unlike(Guid reelId, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _reels.UnlikeAsync(userId, reelId, ct)).ToResponse();
    }

    /// <summary>Deletes the caller's own reel.</summary>
    [HttpDelete("{reelId:guid}")]
    public async Task<IActionResult> Delete(Guid reelId, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _reels.DeleteAsync(userId, reelId, ct)).ToResponse();
    }
}
