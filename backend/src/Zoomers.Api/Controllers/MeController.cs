using Asp.Versioning;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Zoomers.Api.Common;
using Zoomers.Application.Posts;
using Zoomers.Application.Profiles;
using Zoomers.Application.Profiles.Dtos;
using Zoomers.Application.Social;

namespace Zoomers.Api.Controllers;

[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/users/me")]
[Authorize]
public sealed class MeController : ApiControllerBase
{
    private readonly IProfileService _profiles;
    private readonly ISocialGraphService _social;
    private readonly IPostService _posts;

    public MeController(IProfileService profiles, ISocialGraphService social, IPostService posts)
    {
        _profiles = profiles;
        _social = social;
        _posts = posts;
    }

    /// <summary>Returns the authenticated user's own profile.</summary>
    [HttpGet]
    [ProducesResponseType(typeof(ProfileView), StatusCodes.Status200OK)]
    public async Task<IActionResult> Get(CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _profiles.GetMeAsync(userId, ct)).ToResponse();
    }

    /// <summary>Edits the authenticated user's profile (display name, bio, website, privacy).</summary>
    [HttpPatch]
    [ProducesResponseType(typeof(ProfileView), StatusCodes.Status200OK)]
    public async Task<IActionResult> Update(UpdateProfileRequest request, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _profiles.UpdateMeAsync(userId, request, ct)).ToResponse();
    }

    /// <summary>Lists the authenticated user's pending incoming follow requests.</summary>
    [HttpGet("follow-requests")]
    public async Task<IActionResult> IncomingRequests([FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _social.GetIncomingRequestsAsync(userId, cursor, limit, ct)).ToResponse();
    }

    /// <summary>Lists the authenticated user's saved posts.</summary>
    [HttpGet("saved")]
    public async Task<IActionResult> Saved([FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _posts.GetSavedAsync(userId, cursor, limit, ct)).ToResponse();
    }
}
