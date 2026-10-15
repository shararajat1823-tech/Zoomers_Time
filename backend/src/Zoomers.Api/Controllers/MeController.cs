using Asp.Versioning;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Zoomers.Api.Common;
using Zoomers.Application.Profiles;

namespace Zoomers.Api.Controllers;

[ApiController]
[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/users")]
[Authorize]
public sealed class MeController : ControllerBase
{
    private readonly IProfileService _profiles;
    private readonly ICurrentUser _currentUser;

    public MeController(IProfileService profiles, ICurrentUser currentUser)
    {
        _profiles = profiles;
        _currentUser = currentUser;
    }

    /// <summary>Returns the authenticated user's own profile.</summary>
    [HttpGet("me")]
    [ProducesResponseType(typeof(ProfileView), StatusCodes.Status200OK)]
    public async Task<IActionResult> Me(CancellationToken ct)
    {
        var userId = _currentUser.UserId;
        if (userId is null)
            return Unauthorized();

        return (await _profiles.GetMeAsync(userId.Value, ct)).ToResponse();
    }
}
