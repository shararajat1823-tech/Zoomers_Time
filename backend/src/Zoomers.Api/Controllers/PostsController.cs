using Asp.Versioning;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Zoomers.Api.Common;
using Zoomers.Application.Engagement;
using Zoomers.Application.Engagement.Dtos;
using Zoomers.Application.Posts;
using Zoomers.Application.Posts.Dtos;

namespace Zoomers.Api.Controllers;

[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/posts")]
[Authorize]
public sealed class PostsController : ApiControllerBase
{
    private readonly IPostService _posts;
    private readonly IEngagementService _engagement;

    public PostsController(IPostService posts, IEngagementService engagement)
    {
        _posts = posts;
        _engagement = engagement;
    }

    [HttpPost]
    [ProducesResponseType(typeof(PostView), StatusCodes.Status201Created)]
    public async Task<IActionResult> Create(CreatePostRequest request, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _posts.CreateAsync(userId, request, ct)).ToResponse(StatusCodes.Status201Created);
    }

    [HttpGet("{postId:guid}")]
    [ProducesResponseType(typeof(PostView), StatusCodes.Status200OK)]
    public async Task<IActionResult> Get(Guid postId, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _posts.GetAsync(userId, postId, ct)).ToResponse();
    }

    [HttpDelete("{postId:guid}")]
    public async Task<IActionResult> Delete(Guid postId, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _posts.DeleteAsync(userId, postId, ct)).ToResponse();
    }

    // ---- engagement ----

    [HttpPost("{postId:guid}/likes")]
    public async Task<IActionResult> Like(Guid postId, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _engagement.LikePostAsync(userId, postId, ct)).ToResponse();
    }

    [HttpDelete("{postId:guid}/likes")]
    public async Task<IActionResult> Unlike(Guid postId, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _engagement.UnlikePostAsync(userId, postId, ct)).ToResponse();
    }

    [HttpPost("{postId:guid}/save")]
    public async Task<IActionResult> Save(Guid postId, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _engagement.SavePostAsync(userId, postId, ct)).ToResponse();
    }

    [HttpDelete("{postId:guid}/save")]
    public async Task<IActionResult> Unsave(Guid postId, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _engagement.UnsavePostAsync(userId, postId, ct)).ToResponse();
    }

    // ---- comments ----

    [HttpGet("{postId:guid}/comments")]
    public async Task<IActionResult> Comments(Guid postId, [FromQuery] Guid? parentCommentId, [FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _engagement.GetCommentsAsync(userId, postId, parentCommentId, cursor, limit, ct)).ToResponse();
    }

    [HttpPost("{postId:guid}/comments")]
    [ProducesResponseType(typeof(CommentView), StatusCodes.Status201Created)]
    public async Task<IActionResult> AddComment(Guid postId, AddCommentRequest request, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _engagement.AddCommentAsync(userId, postId, request, ct)).ToResponse(StatusCodes.Status201Created);
    }
}
