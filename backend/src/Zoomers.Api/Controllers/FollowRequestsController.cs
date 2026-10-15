using Asp.Versioning;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Zoomers.Api.Common;
using Zoomers.Application.Social;

namespace Zoomers.Api.Controllers;

[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/follow-requests")]
[Authorize]
public sealed class FollowRequestsController : ApiControllerBase
{
    private readonly ISocialGraphService _social;
    public FollowRequestsController(ISocialGraphService social) => _social = social;

    /// <summary>Accepts a pending follow request (caller must be its target).</summary>
    [HttpPost("{requestId:guid}/accept")]
    public async Task<IActionResult> Accept(Guid requestId, CancellationToken ct)
    {
        if (CurrentUserId is not { } actorId) return Unauthorized();
        return (await _social.RespondToRequestAsync(actorId, requestId, accept: true, ct)).ToResponse();
    }

    /// <summary>Rejects a pending follow request (caller must be its target).</summary>
    [HttpPost("{requestId:guid}/reject")]
    public async Task<IActionResult> Reject(Guid requestId, CancellationToken ct)
    {
        if (CurrentUserId is not { } actorId) return Unauthorized();
        return (await _social.RespondToRequestAsync(actorId, requestId, accept: false, ct)).ToResponse();
    }
}
