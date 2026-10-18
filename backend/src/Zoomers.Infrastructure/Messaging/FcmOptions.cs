namespace Zoomers.Infrastructure.Messaging;

/// <summary>
/// Firebase Cloud Messaging configuration. <see cref="ServiceAccountJson"/> is the
/// full service-account key JSON (from the Firebase console) and is supplied via a
/// secret/env var — never committed. When it is empty, push is disabled and the
/// app falls back to the logging sender, so the backend runs fine without Firebase.
/// </summary>
public sealed class FcmOptions
{
    public const string SectionName = "Fcm";

    /// <summary>The service-account key JSON used to mint FCM access tokens.</summary>
    public string ServiceAccountJson { get; set; } = string.Empty;

    public bool Enabled => !string.IsNullOrWhiteSpace(ServiceAccountJson);
}
