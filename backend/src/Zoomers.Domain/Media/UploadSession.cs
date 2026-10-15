using Zoomers.Domain.Common;
using Zoomers.Domain.Identity;

namespace Zoomers.Domain.Media;

public enum UploadPurpose { PostMedia = 0, StoryMedia = 1, Avatar = 2, MessageAttachment = 3 }

public enum UploadStatus { Pending = 0, Uploaded = 1, Verified = 2, Completed = 3, Abandoned = 4 }

/// <summary>
/// A short-lived, scoped upload session. The client uploads directly to object
/// storage using an authorized URL; the server then verifies the object (size,
/// type) before the media is attached to a post. Abandoned sessions are swept.
/// </summary>
public class UploadSession : Entity
{
    public Guid UserId { get; set; }
    public User? User { get; set; }

    public UploadPurpose Purpose { get; set; }
    public UploadStatus Status { get; set; } = UploadStatus.Pending;

    public string StorageKey { get; set; } = string.Empty;
    public string ContentType { get; set; } = string.Empty;
    public long MaxBytes { get; set; }
    public long? VerifiedBytes { get; set; }

    public DateTimeOffset ExpiresAt { get; set; }
    public DateTimeOffset? CompletedAt { get; set; }

    public bool IsUsable(DateTimeOffset now) =>
        Status is UploadStatus.Pending or UploadStatus.Uploaded or UploadStatus.Verified && now < ExpiresAt;
}
