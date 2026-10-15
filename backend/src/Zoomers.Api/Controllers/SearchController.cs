using Asp.Versioning;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Zoomers.Api.Common;
using Zoomers.Application.Search;

namespace Zoomers.Api.Controllers;

[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/search")]
[Authorize]
public sealed class SearchController : ApiControllerBase
{
    private readonly ISearchService _search;
    public SearchController(ISearchService search) => _search = search;

    [HttpGet("users")]
    public async Task<IActionResult> Users([FromQuery] string q, [FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } viewerId) return Unauthorized();
        return (await _search.SearchUsersAsync(viewerId, q ?? string.Empty, cursor, limit, ct)).ToResponse();
    }

    [HttpGet("hashtags")]
    public async Task<IActionResult> Hashtags([FromQuery] string q, [FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is null) return Unauthorized();
        return (await _search.SearchHashtagsAsync(q ?? string.Empty, cursor, limit, ct)).ToResponse();
    }

    [HttpGet("posts")]
    public async Task<IActionResult> Posts([FromQuery] string q, [FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } viewerId) return Unauthorized();
        return (await _search.SearchPostsAsync(viewerId, q ?? string.Empty, cursor, limit, ct)).ToResponse();
    }

    [HttpGet("suggested-users")]
    public async Task<IActionResult> Suggested([FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } viewerId) return Unauthorized();
        return (await _search.SuggestedUsersAsync(viewerId, cursor, limit, ct)).ToResponse();
    }
}

[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/hashtags")]
[Authorize]
public sealed class HashtagsController : ApiControllerBase
{
    private readonly ISearchService _search;
    public HashtagsController(ISearchService search) => _search = search;

    /// <summary>Public posts tagged with a hashtag.</summary>
    [HttpGet("{tag}/posts")]
    public async Task<IActionResult> Posts(string tag, [FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } viewerId) return Unauthorized();
        return (await _search.GetHashtagPostsAsync(viewerId, tag, cursor, limit, ct)).ToResponse();
    }
}
