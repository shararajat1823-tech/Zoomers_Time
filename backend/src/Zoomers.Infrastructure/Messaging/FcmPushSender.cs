using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Zoomers.Application.Abstractions;

namespace Zoomers.Infrastructure.Messaging;

/// <summary>
/// Delivers pushes through Firebase Cloud Messaging (HTTP v1). Access tokens are
/// minted from the service-account key via a signed JWT (no external SDK), cached
/// until expiry. Honors the recipient's per-category push settings, prunes device
/// tokens FCM reports as stale, and never throws — a delivery failure must not
/// break the triggering operation.
/// </summary>
public sealed class FcmPushSender : IPushSender
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(15) };
    private static readonly SemaphoreSlim TokenLock = new(1, 1);
    private static (string token, DateTimeOffset expiresAt)? _cachedToken;

    private readonly IApplicationDbContext _db;
    private readonly IClock _clock;
    private readonly FcmOptions _options;
    private readonly ILogger<FcmPushSender> _logger;

    public FcmPushSender(IApplicationDbContext db, IClock clock, IOptions<FcmOptions> options, ILogger<FcmPushSender> logger)
    {
        _db = db;
        _clock = clock;
        _options = options.Value;
        _logger = logger;
    }

    public async Task SendAsync(Guid userId, PushMessage message, CancellationToken ct = default)
    {
        try
        {
            if (!_options.Enabled) return;
            if (!await CategoryAllowedAsync(userId, message.Category, ct)) return;

            var tokens = await _db.DeviceTokens.Where(d => d.UserId == userId).ToListAsync(ct);
            if (tokens.Count == 0) return;

            using var account = JsonDocument.Parse(_options.ServiceAccountJson);
            var root = account.RootElement;
            var projectId = root.GetProperty("project_id").GetString();
            if (string.IsNullOrEmpty(projectId)) return;

            var accessToken = await GetAccessTokenAsync(root, ct);
            if (accessToken is null) return;

            var url = $"https://fcm.googleapis.com/v1/projects/{projectId}/messages:send";
            foreach (var device in tokens)
            {
                var pruned = await SendOneAsync(url, accessToken, device.Token, message, ct);
                if (pruned) _db.DeviceTokens.Remove(device);
            }
            await _db.SaveChangesAsync(ct);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Push delivery to {UserId} failed (non-fatal).", userId);
        }
    }

    private async Task<bool> CategoryAllowedAsync(Guid userId, PushCategory category, CancellationToken ct)
    {
        if (category == PushCategory.Generic) return true;
        var s = await _db.UserSettings.FirstOrDefaultAsync(x => x.UserId == userId, ct);
        if (s is null) return true; // default-allow if no settings row yet
        return category switch
        {
            PushCategory.Like => s.PushLikes,
            PushCategory.Comment => s.PushComments,
            PushCategory.Follow => s.PushFollows,
            PushCategory.Message => s.PushMessages,
            _ => true,
        };
    }

    /// <summary>Sends to one token. Returns true if FCM says the token is dead and should be removed.</summary>
    private async Task<bool> SendOneAsync(string url, string accessToken, string deviceToken, PushMessage message, CancellationToken ct)
    {
        var payload = new
        {
            message = new
            {
                token = deviceToken,
                notification = new { title = message.Title, body = message.Body },
                data = message.Data ?? new Dictionary<string, string>(),
                android = new { priority = "high" },
            },
        };

        using var req = new HttpRequestMessage(HttpMethod.Post, url)
        {
            Content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json"),
        };
        req.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", accessToken);

        using var res = await Http.SendAsync(req, ct);
        if (res.IsSuccessStatusCode) return false;

        var body = await res.Content.ReadAsStringAsync(ct);
        // 404 / UNREGISTERED / INVALID_ARGUMENT for the token means the registration is dead.
        var dead = res.StatusCode == HttpStatusCode.NotFound
                   || body.Contains("UNREGISTERED", StringComparison.OrdinalIgnoreCase)
                   || body.Contains("registration-token-not-registered", StringComparison.OrdinalIgnoreCase);
        if (!dead)
            _logger.LogWarning("FCM send failed ({Status}): {Body}", (int)res.StatusCode, body);
        return dead;
    }

    private async Task<string?> GetAccessTokenAsync(JsonElement serviceAccount, CancellationToken ct)
    {
        var now = _clock.UtcNow;
        if (_cachedToken is { } c && c.expiresAt > now.AddSeconds(60))
            return c.token;

        await TokenLock.WaitAsync(ct);
        try
        {
            if (_cachedToken is { } again && again.expiresAt > now.AddSeconds(60))
                return again.token;

            var clientEmail = serviceAccount.GetProperty("client_email").GetString()!;
            var privateKey = serviceAccount.GetProperty("private_key").GetString()!;
            var tokenUri = serviceAccount.TryGetProperty("token_uri", out var t) ? t.GetString()! : "https://oauth2.googleapis.com/token";

            var iat = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
            var exp = iat + 3600;
            var header = Base64Url(JsonSerializer.SerializeToUtf8Bytes(new { alg = "RS256", typ = "JWT" }));
            var claims = Base64Url(JsonSerializer.SerializeToUtf8Bytes(new
            {
                iss = clientEmail,
                scope = "https://www.googleapis.com/auth/firebase.messaging",
                aud = tokenUri,
                iat,
                exp,
            }));
            var signingInput = $"{header}.{claims}";

            using var rsa = RSA.Create();
            rsa.ImportFromPem(privateKey);
            var signature = rsa.SignData(Encoding.ASCII.GetBytes(signingInput), HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
            var jwt = $"{signingInput}.{Base64Url(signature)}";

            using var form = new FormUrlEncodedContent(new Dictionary<string, string>
            {
                ["grant_type"] = "urn:ietf:params:oauth:grant-type:jwt-bearer",
                ["assertion"] = jwt,
            });
            using var res = await Http.PostAsync(tokenUri, form, ct);
            if (!res.IsSuccessStatusCode)
            {
                _logger.LogWarning("FCM token exchange failed ({Status}).", (int)res.StatusCode);
                return null;
            }

            using var doc = JsonDocument.Parse(await res.Content.ReadAsStringAsync(ct));
            var accessToken = doc.RootElement.GetProperty("access_token").GetString()!;
            var expiresIn = doc.RootElement.TryGetProperty("expires_in", out var e) ? e.GetInt32() : 3600;
            _cachedToken = (accessToken, now.AddSeconds(expiresIn));
            return accessToken;
        }
        finally
        {
            TokenLock.Release();
        }
    }

    private static string Base64Url(byte[] bytes) =>
        Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');
}
