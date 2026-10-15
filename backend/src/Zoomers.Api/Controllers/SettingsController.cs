using Asp.Versioning;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Zoomers.Api.Common;
using Zoomers.Application.Settings;
using Zoomers.Application.Settings.Dtos;

namespace Zoomers.Api.Controllers;

[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/settings")]
[Authorize]
public sealed class SettingsController : ApiControllerBase
{
    private readonly ISettingsService _settings;
    public SettingsController(ISettingsService settings) => _settings = settings;

    [HttpGet]
    [ProducesResponseType(typeof(SettingsView), StatusCodes.Status200OK)]
    public async Task<IActionResult> Get(CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _settings.GetAsync(userId, ct)).ToResponse();
    }

    [HttpPatch]
    [ProducesResponseType(typeof(SettingsView), StatusCodes.Status200OK)]
    public async Task<IActionResult> Update(UpdateSettingsRequest request, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _settings.UpdateAsync(userId, request, ct)).ToResponse();
    }

    [HttpGet("blocked")]
    public async Task<IActionResult> Blocked([FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _settings.GetBlockedAsync(userId, cursor, limit, ct)).ToResponse();
    }

    [HttpGet("muted")]
    public async Task<IActionResult> Muted([FromQuery] string? cursor, [FromQuery] int limit, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _settings.GetMutedAsync(userId, cursor, limit, ct)).ToResponse();
    }
}
