using Asp.Versioning;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Zoomers.Api.Common;
using Zoomers.Application.Profiles;
using Zoomers.Application.Profiles.Dtos;
using Zoomers.Application.Social;

namespace Zoomers.Api.Controllers;

[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/users")]
[Authorize]
public sealed class UsersController : ApiControllerBase
{
    private readonly IProfileService _profiles;
    private readonly ISocialGraphService _social;

    public UsersController(IProfileService profiles, ISocialGraphService social)
    {
        _profiles = profiles;
        _social = social;
    }

    /// <summary>Returns a public profile by username (with viewer relationship and visibility).</summary>
    [HttpGet("{username}")]
    [ProducesResponseType(typeof(PublicProfileView), StatusCodes.Status200OK)]
    public async Task<IActionResult> GetByUsername(string username, CancellationToken ct)
    {
        if (CurrentUserId is not { } viewerId) return Unauthorized();
        return (await _profiles.GetByUsernameAsync(viewerId, username, ct)).ToResponse();
    }

    /// <summary>Follows a user (instant for public accounts, request for private).</summary>
    [HttpPost("{userId:guid}/follow")]
    public async Task<IActionResult> Follow(Guid userId, CancellationToken ct)
    {
        if (CurrentUserId is not { } actorId) return Unauthorized();
        return (await _social.FollowAsync(actorId, userId, ct)).ToResponse();
    }

    /// <summary>Unfollows a user (or cancels a pending request via the cancel endpoint).</summary>
    [HttpDelete("{userId:guid}/follow")]
    public async Task<IActionResult> Unfollow(Guid userId, CancellationToken ct)
    {
        if (CurrentUserId is not { } actorId) return Unauthorized();
        return (await _social.UnfollowAsync(actorId, userId, ct)).ToResponse();
    }

    /// <summary>Cancels the caller's pending follow request to a private account.</summary>
    [HttpDelete("{userId:guid}/follow-request")]
    public async Task<IActionResult> CancelRequest(Guid userId, CancellationToken ct)
    {
        if (CurrentUserId is not { } actorId) return Unauthorized();
        return (await _social.CancelFollowRequestAsync(actorId, userId, ct)).ToResponse();
    }

    /// <summary>Removes one of the caller's followers.</summary>
    [HttpDelete("{userId:guid}/follower")]
    public async Task<IActionResult> RemoveFollower(Guid userId, CancellationToken ct)
    {
        if (CurrentUserId is not { } actorId) return Unauthorized();
        return (await _social.RemoveFollowerAsync(actorId, userId, ct)).ToResponse();
    }

    /// <summary>Lists a user's followers (respecting privacy and blocks).</summary>
    [HttpGet("{userId:guid}/followers")]
    public async Task<IActionResult> Followers(Guid userId, [FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } viewerId) return Unauthorized();
        return (await _social.GetFollowersAsync(viewerId, userId, cursor, limit, ct)).ToResponse();
    }

    /// <summary>Lists who a user is following (respecting privacy and blocks).</summary>
    [HttpGet("{userId:guid}/following")]
    public async Task<IActionResult> Following(Guid userId, [FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } viewerId) return Unauthorized();
        return (await _social.GetFollowingAsync(viewerId, userId, cursor, limit, ct)).ToResponse();
    }

    /// <summary>Blocks a user (removes follow edges both ways and pending requests).</summary>
    [HttpPost("{userId:guid}/block")]
    public async Task<IActionResult> Block(Guid userId, CancellationToken ct)
    {
        if (CurrentUserId is not { } actorId) return Unauthorized();
        return (await _social.BlockAsync(actorId, userId, ct)).ToResponse();
    }

    /// <summary>Unblocks a user.</summary>
    [HttpDelete("{userId:guid}/block")]
    public async Task<IActionResult> Unblock(Guid userId, CancellationToken ct)
    {
        if (CurrentUserId is not { } actorId) return Unauthorized();
        return (await _social.UnblockAsync(actorId, userId, ct)).ToResponse();
    }
}
