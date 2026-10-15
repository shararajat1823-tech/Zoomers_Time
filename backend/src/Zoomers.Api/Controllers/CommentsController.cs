using Asp.Versioning;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Zoomers.Api.Common;
using Zoomers.Application.Engagement;

namespace Zoomers.Api.Controllers;

[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/comments")]
[Authorize]
public sealed class CommentsController : ApiControllerBase
{
    private readonly IEngagementService _engagement;
    public CommentsController(IEngagementService engagement) => _engagement = engagement;

    [HttpDelete("{commentId:guid}")]
    public async Task<IActionResult> Delete(Guid commentId, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _engagement.DeleteCommentAsync(userId, commentId, ct)).ToResponse();
    }

    [HttpPost("{commentId:guid}/likes")]
    public async Task<IActionResult> Like(Guid commentId, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _engagement.LikeCommentAsync(userId, commentId, ct)).ToResponse();
    }

    [HttpDelete("{commentId:guid}/likes")]
    public async Task<IActionResult> Unlike(Guid commentId, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _engagement.UnlikeCommentAsync(userId, commentId, ct)).ToResponse();
    }
}
