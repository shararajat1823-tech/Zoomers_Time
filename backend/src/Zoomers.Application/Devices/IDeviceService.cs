using Zoomers.Shared.Results;

namespace Zoomers.Application.Devices;

public interface IDeviceService
{
    /// <summary>Registers or refreshes a device token for the caller (idempotent).</summary>
    Task<Result> RegisterAsync(Guid userId, RegisterDeviceRequest request, CancellationToken ct = default);

    /// <summary>Removes a device token so it no longer receives pushes (e.g. on sign-out).</summary>
    Task<Result> RemoveAsync(Guid userId, RemoveDeviceRequest request, CancellationToken ct = default);
}
