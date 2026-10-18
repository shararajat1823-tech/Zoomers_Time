using Asp.Versioning;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Zoomers.Api.Common;
using Zoomers.Application.Devices;

namespace Zoomers.Api.Controllers;

[ApiVersion("1.0")]
[Route("api/v{version:apiVersion}/me/devices")]
[Authorize]
public sealed class DevicesController : ApiControllerBase
{
    private readonly IDeviceService _devices;
    public DevicesController(IDeviceService devices) => _devices = devices;

    /// <summary>Registers or refreshes this device's push token for the caller.</summary>
    [HttpPost]
    public async Task<IActionResult> Register(RegisterDeviceRequest request, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _devices.RegisterAsync(userId, request, ct)).ToResponse();
    }

    /// <summary>Removes a device token (e.g. on sign-out) so it stops receiving pushes.</summary>
    [HttpPost("remove")]
    public async Task<IActionResult> Remove(RemoveDeviceRequest request, CancellationToken ct)
    {
        if (CurrentUserId is not { } userId) return Unauthorized();
        return (await _devices.RemoveAsync(userId, request, ct)).ToResponse();
    }
}
