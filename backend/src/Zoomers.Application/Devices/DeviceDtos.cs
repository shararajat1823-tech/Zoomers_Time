namespace Zoomers.Application.Devices;

/// <summary>Registers (or refreshes) the caller's device for push notifications.</summary>
public sealed record RegisterDeviceRequest(string Token, string Platform = "android");

/// <summary>Removes a device token (e.g. on sign-out) so it stops receiving pushes.</summary>
public sealed record RemoveDeviceRequest(string Token);
