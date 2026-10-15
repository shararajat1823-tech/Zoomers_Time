using Microsoft.EntityFrameworkCore;
using Zoomers.Application.Abstractions;
using Zoomers.Application.Media.Dtos;
using Zoomers.Application.Posts;
using Zoomers.Domain.Media;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Media;

public sealed class MediaService : IMediaService
{
    private static readonly TimeSpan SessionLifetime = TimeSpan.FromHours(1);

    private readonly IApplicationDbContext _db;
    private readonly IMediaStorage _storage;
    private readonly PostService _posts;
    private readonly IClock _clock;

    public MediaService(IApplicationDbContext db, IMediaStorage storage, PostService posts, IClock clock)
    {
        _db = db;
        _storage = storage;
        _posts = posts;
        _clock = clock;
    }

    public async Task<Result<UploadSessionResponse>> CreateUploadSessionAsync(Guid userId, CreateUploadSessionRequest request, CancellationToken ct = default)
    {
        if (!Enum.TryParse<UploadPurpose>(request.Purpose, ignoreCase: true, out var purpose))
            return Error.Validation("media.invalid_purpose", "Unknown upload purpose.");

        if (!MediaPolicy.IsAllowed(request.ContentType))
            return Error.Validation("media.unsupported_type", "That file type is not supported.");

        var maxBytes = MediaPolicy.MaxBytesFor(request.ContentType);
        var storageKey = $"{purpose.ToString().ToLowerInvariant()}/{userId:N}/{Guid.NewGuid():N}";
        var now = _clock.UtcNow;

        var session = new UploadSession
        {
            UserId = userId,
            Purpose = purpose,
            Status = UploadStatus.Pending,
            StorageKey = storageKey,
            ContentType = request.ContentType,
            MaxBytes = maxBytes,
            ExpiresAt = now.Add(SessionLifetime),
            CreatedAt = now
        };
        _db.UploadSessions.Add(session);
        await _db.SaveChangesAsync(ct);

        var signed = await _storage.CreateUploadUrlAsync(storageKey, request.ContentType, maxBytes, ct);

        return Result.Success(new UploadSessionResponse(
            session.Id, storageKey, signed.Url, session.ExpiresAt, session.Status, maxBytes));
    }

    public async Task<Result<UploadSessionResponse>> CompleteUploadSessionAsync(Guid userId, Guid sessionId, CancellationToken ct = default)
    {
        var session = await _db.UploadSessions.FirstOrDefaultAsync(s => s.Id == sessionId, ct);
        if (session is null || session.UserId != userId)
            return Error.NotFound("media.session_not_found", "Upload session not found.");

        if (session.Status == UploadStatus.Completed)
            return Result.Success(Map(session)); // idempotent

        if (!session.IsUsable(_clock.UtcNow))
            return Error.Validation("media.session_expired", "This upload session has expired.");

        // Server-side verification: the object must exist and be within the size limit.
        var size = await _storage.TryGetObjectSizeAsync(session.StorageKey, ct);
        if (size is null)
            return Error.Validation("media.not_uploaded", "No uploaded file was found for this session.");
        if (size > session.MaxBytes)
        {
            await _storage.DeleteAsync(session.StorageKey, ct);
            session.Status = UploadStatus.Abandoned;
            await _db.SaveChangesAsync(ct);
            return Error.Validation("media.too_large", "The uploaded file exceeds the size limit.");
        }

        session.VerifiedBytes = size;
        session.Status = UploadStatus.Completed;
        session.CompletedAt = _clock.UtcNow;
        await _db.SaveChangesAsync(ct);

        return Result.Success(Map(session));
    }

    public async Task<Result> AcceptUploadAsync(Guid userId, string storageKey, Stream content, string contentType, CancellationToken ct = default)
    {
        var session = await _db.UploadSessions.FirstOrDefaultAsync(s => s.StorageKey == storageKey, ct);
        if (session is null || session.UserId != userId)
            return Error.NotFound("media.session_not_found", "Upload session not found.");
        if (!session.IsUsable(_clock.UtcNow))
            return Error.Validation("media.session_expired", "This upload session has expired.");

        await _storage.SaveAsync(storageKey, content, contentType, ct);
        session.Status = UploadStatus.Uploaded;
        await _db.SaveChangesAsync(ct);
        return Result.Success();
    }

    public async Task<Result<MediaDownload>> OpenAuthorizedAsync(Guid viewerId, string storageKey, CancellationToken ct = default)
    {
        var notFound = Error.NotFound("media.not_found", "Media not found.");

        // Resolve the media object and the post it belongs to, then authorize against post visibility.
        var media = await _db.PostMedia
            .Where(m => m.StorageKey == storageKey)
            .Select(m => new { m.Post!.AuthorId, m.Post.Visibility })
            .FirstOrDefaultAsync(ct);

        if (media is null)
            return notFound;

        if (!await _posts.CanViewPostAsync(viewerId, media.AuthorId, media.Visibility, ct))
            return notFound; // do not reveal existence of media the viewer may not see

        var stream = await _storage.OpenReadAsync(storageKey, ct);
        if (stream is null)
            return notFound;

        var contentType = await _db.UploadSessions
            .Where(s => s.StorageKey == storageKey)
            .Select(s => s.ContentType)
            .FirstOrDefaultAsync(ct) ?? "application/octet-stream";

        return Result.Success(new MediaDownload(stream, contentType));
    }

    private static UploadSessionResponse Map(UploadSession s) =>
        new(s.Id, s.StorageKey, string.Empty, s.ExpiresAt, s.Status, s.MaxBytes);
}
