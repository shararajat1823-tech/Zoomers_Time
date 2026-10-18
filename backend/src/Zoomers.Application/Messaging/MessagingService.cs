using Microsoft.EntityFrameworkCore;
using Zoomers.Application.Abstractions;
using Zoomers.Application.Messaging.Dtos;
using Zoomers.Domain.Messaging;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Messaging;

public sealed class MessagingService : IMessagingService
{
    private const int MaxBodyLength = 2000;
    private const int PreviewLength = 120;

    private readonly IApplicationDbContext _db;
    private readonly IMediaStorage _storage;
    private readonly IClock _clock;
    private readonly IPushSender _push;

    public MessagingService(IApplicationDbContext db, IMediaStorage storage, IClock clock, IPushSender push)
    {
        _db = db;
        _storage = storage;
        _clock = clock;
        _push = push;
    }

    public async Task<Result<ConversationDto>> StartAsync(Guid userId, StartConversationRequest request, CancellationToken ct = default)
    {
        var otherId = request.RecipientUserId;
        if (otherId == userId)
            return Error.Validation("dm.self", "You can't message yourself.");

        var recipient = await _db.Users.FirstOrDefaultAsync(u => u.Id == otherId, ct);
        if (recipient is null || !recipient.IsActive)
            return Error.NotFound("dm.recipient_not_found", "That account isn't available.");

        if (await IsBlockedEitherWayAsync(userId, otherId, ct))
            return Error.Forbidden("dm.blocked", "You can't message this account.");

        var (a, b) = Conversation.Pair(userId, otherId);
        var conversation = await _db.Conversations
            .FirstOrDefaultAsync(c => c.ParticipantAId == a && c.ParticipantBId == b, ct);

        if (conversation is null)
        {
            var now = _clock.UtcNow;
            conversation = new Conversation
            {
                ParticipantAId = a,
                ParticipantBId = b,
                CreatedAt = now,
                LastMessageAt = now,
            };
            _db.Conversations.Add(conversation);
            await _db.SaveChangesAsync(ct);
        }

        return await BuildConversationDtoAsync(userId, conversation.Id, ct)
            is { } dto ? Result.Success(dto)
            : Error.NotFound("dm.not_found", "Conversation not found.");
    }

    public async Task<Result<CursorPage<ConversationDto>>> GetConversationsAsync(Guid userId, string? cursor, int limit, CancellationToken ct = default)
    {
        limit = PageLimit.Clamp(limit);

        var q = _db.Conversations.Where(c => c.ParticipantAId == userId || c.ParticipantBId == userId);
        if (KeysetCursor.TryParse(cursor, out var k))
            q = q.Where(c => c.LastMessageAt < k.CreatedAt || (c.LastMessageAt == k.CreatedAt && c.Id < k.Id));

        var rows = await q
            .OrderByDescending(c => c.LastMessageAt).ThenByDescending(c => c.Id)
            .Take(limit + 1)
            .Select(c => new ConvRow(
                c.Id,
                c.ParticipantAId,
                c.ParticipantBId,
                c.LastMessagePreview,
                c.LastMessageAt,
                _db.Messages.Count(m => m.ConversationId == c.Id && m.SenderId != userId && m.ReadAt == null)))
            .ToListAsync(ct);

        var hasMore = rows.Count > limit;
        var page = rows.Take(limit).ToList();

        var items = await BuildDtosAsync(userId, page, ct);

        var next = hasMore && page.Count > 0
            ? new KeysetCursor(page[^1].LastMessageAt, page[^1].Id).Encode()
            : null;

        return Result.Success(new CursorPage<ConversationDto>(items, next));
    }

    public async Task<Result<CursorPage<MessageDto>>> GetMessagesAsync(Guid userId, Guid conversationId, string? cursor, int limit, CancellationToken ct = default)
    {
        limit = PageLimit.Clamp(limit);

        var conversation = await _db.Conversations.FirstOrDefaultAsync(c => c.Id == conversationId, ct);
        if (conversation is null || !conversation.HasParticipant(userId))
            return Error.NotFound("dm.not_found", "Conversation not found.");

        var q = _db.Messages.Where(m => m.ConversationId == conversationId);
        if (KeysetCursor.TryParse(cursor, out var k))
            q = q.Where(m => m.CreatedAt < k.CreatedAt || (m.CreatedAt == k.CreatedAt && m.Id < k.Id));

        var rows = await q
            .OrderByDescending(m => m.CreatedAt).ThenByDescending(m => m.Id)
            .Take(limit + 1)
            .Select(m => new MessageDto(m.Id, m.ConversationId, m.SenderId, m.Body, m.CreatedAt, m.ReadAt, m.SenderId == userId))
            .ToListAsync(ct);

        var hasMore = rows.Count > limit;
        var page = rows.Take(limit).ToList();

        var next = hasMore && page.Count > 0
            ? new KeysetCursor(page[^1].CreatedAt, page[^1].Id).Encode()
            : null;

        return Result.Success(new CursorPage<MessageDto>(page, next));
    }

    public async Task<Result<MessageDto>> SendMessageAsync(Guid userId, Guid conversationId, SendMessageRequest request, CancellationToken ct = default)
    {
        var body = (request.Body ?? string.Empty).Trim();
        if (body.Length == 0)
            return Error.Validation("dm.empty", "Message can't be empty.");
        if (body.Length > MaxBodyLength)
            return Error.Validation("dm.too_long", $"Messages are limited to {MaxBodyLength} characters.");

        var conversation = await _db.Conversations.FirstOrDefaultAsync(c => c.Id == conversationId, ct);
        if (conversation is null || !conversation.HasParticipant(userId))
            return Error.NotFound("dm.not_found", "Conversation not found.");

        var otherId = conversation.OtherParticipant(userId);
        if (await IsBlockedEitherWayAsync(userId, otherId, ct))
            return Error.Forbidden("dm.blocked", "You can't message this account.");

        var now = _clock.UtcNow;
        var message = new Message
        {
            ConversationId = conversationId,
            SenderId = userId,
            Body = body,
            CreatedAt = now,
        };
        _db.Messages.Add(message);

        conversation.LastMessageAt = now;
        conversation.LastMessagePreview = body.Length > PreviewLength ? body[..PreviewLength] : body;

        await _db.SaveChangesAsync(ct);

        // Best-effort push to the recipient (settings-honored, never throws).
        var senderName = await _db.Users.Where(u => u.Id == userId).Select(u => u.Username).FirstOrDefaultAsync(ct) ?? "Someone";
        var preview = body.Length > PreviewLength ? body[..PreviewLength] : body;
        var data = new Dictionary<string, string> { ["type"] = "message", ["conversationId"] = conversationId.ToString(), ["senderId"] = userId.ToString() };
        await _push.SendAsync(otherId, new PushMessage(senderName, preview, PushCategory.Message, data), ct);

        return Result.Success(new MessageDto(message.Id, conversationId, userId, body, now, null, true));
    }

    public async Task<Result> MarkReadAsync(Guid userId, Guid conversationId, CancellationToken ct = default)
    {
        var conversation = await _db.Conversations.FirstOrDefaultAsync(c => c.Id == conversationId, ct);
        if (conversation is null || !conversation.HasParticipant(userId))
            return Error.NotFound("dm.not_found", "Conversation not found.");

        var unread = await _db.Messages
            .Where(m => m.ConversationId == conversationId && m.SenderId != userId && m.ReadAt == null)
            .ToListAsync(ct);

        if (unread.Count == 0) return Result.Success();

        var now = _clock.UtcNow;
        foreach (var m in unread) m.ReadAt = now;
        await _db.SaveChangesAsync(ct);
        return Result.Success();
    }

    public async Task<Result<UnreadCountDto>> GetUnreadCountAsync(Guid userId, CancellationToken ct = default)
    {
        var count = await _db.Messages.CountAsync(
            m => m.SenderId != userId && m.ReadAt == null &&
                 (m.Conversation!.ParticipantAId == userId || m.Conversation.ParticipantBId == userId),
            ct);
        return Result.Success(new UnreadCountDto(count));
    }

    private Task<bool> IsBlockedEitherWayAsync(Guid userId, Guid otherId, CancellationToken ct) =>
        _db.UserBlocks.AnyAsync(
            b => (b.BlockerId == userId && b.BlockedId == otherId) ||
                 (b.BlockerId == otherId && b.BlockedId == userId),
            ct);

    private async Task<ConversationDto?> BuildConversationDtoAsync(Guid userId, Guid conversationId, CancellationToken ct)
    {
        var row = await _db.Conversations
            .Where(c => c.Id == conversationId)
            .Select(c => new ConvRow(
                c.Id,
                c.ParticipantAId,
                c.ParticipantBId,
                c.LastMessagePreview,
                c.LastMessageAt,
                _db.Messages.Count(m => m.ConversationId == c.Id && m.SenderId != userId && m.ReadAt == null)))
            .FirstOrDefaultAsync(ct);
        if (row is null) return null;

        var dtos = await BuildDtosAsync(userId, new List<ConvRow> { row }, ct);
        return dtos.Count > 0 ? dtos[0] : null;
    }

    /// <summary>
    /// Resolves the "other participant" profile for a page of conversation rows
    /// in a single lookup, then assembles DTOs (and presigned avatar URLs). Done
    /// in memory rather than via a conditional navigation in SQL, which EF cannot
    /// translate alongside the users' soft-delete query filter.
    /// </summary>
    private async Task<List<ConversationDto>> BuildDtosAsync(Guid userId, List<ConvRow> rows, CancellationToken ct)
    {
        if (rows.Count == 0) return new List<ConversationDto>();

        var otherIds = rows.Select(r => r.AId == userId ? r.BId : r.AId).Distinct().ToList();
        var users = await _db.Users
            .Where(u => otherIds.Contains(u.Id))
            .Select(u => new UserRow(u.Id, u.Username, u.Profile!.DisplayName, u.Profile.IsVerified, u.Profile.AvatarMediaKey))
            .ToListAsync(ct);
        var byId = users.ToDictionary(u => u.Id);

        var result = new List<ConversationDto>(rows.Count);
        foreach (var r in rows)
        {
            var otherId = r.AId == userId ? r.BId : r.AId;
            if (!byId.TryGetValue(otherId, out var u)) continue;

            var avatarUrl = string.IsNullOrEmpty(u.AvatarKey)
                ? null
                : (await _storage.CreateDownloadUrlAsync(u.AvatarKey, ct)).Url;

            result.Add(new ConversationDto(
                r.Id,
                new ChatUserDto(u.Id, u.Username, u.DisplayName, avatarUrl, u.IsVerified),
                r.LastMessagePreview,
                r.LastMessageAt,
                r.UnreadCount));
        }
        return result;
    }

    private sealed record ConvRow(
        Guid Id,
        Guid AId,
        Guid BId,
        string? LastMessagePreview,
        DateTimeOffset LastMessageAt,
        int UnreadCount);

    private sealed record UserRow(
        Guid Id,
        string Username,
        string DisplayName,
        bool IsVerified,
        string? AvatarKey);
}
