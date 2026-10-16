using Npgsql;

namespace Zoomers.Api.Common;

/// <summary>
/// Converts a URL-style PostgreSQL connection string — the format handed out by
/// managed providers such as Neon, Render, Supabase, Railway and Heroku
/// (<c>postgres://user:pass@host:port/db?sslmode=require</c>) — into the
/// key/value form Npgsql expects. Hosting platforms expose their database as a
/// single <c>DATABASE_URL</c> environment variable in this shape, so supporting
/// it lets the same image run anywhere without reformatting secrets by hand.
/// </summary>
public static class DatabaseUrl
{
    public static bool LooksLikeUrl(string? value) =>
        !string.IsNullOrWhiteSpace(value) &&
        (value.StartsWith("postgres://", StringComparison.OrdinalIgnoreCase) ||
         value.StartsWith("postgresql://", StringComparison.OrdinalIgnoreCase));

    /// <summary>
    /// Parses a <c>postgres(ql)://</c> URL into an Npgsql connection string.
    /// SSL defaults to Require (managed Postgres mandates TLS) unless the URL's
    /// <c>sslmode</c> query parameter says otherwise. If the value is already in
    /// key/value form it is returned unchanged.
    /// </summary>
    public static string ToNpgsql(string value)
    {
        if (!LooksLikeUrl(value))
            return value; // already a key/value connection string

        var uri = new Uri(value);
        var userInfo = uri.UserInfo.Split(':', 2);
        var user = Uri.UnescapeDataString(userInfo[0]);
        var password = userInfo.Length > 1 ? Uri.UnescapeDataString(userInfo[1]) : string.Empty;
        var database = uri.AbsolutePath.Trim('/');

        var query = ParseQuery(uri.Query);

        var builder = new NpgsqlConnectionStringBuilder
        {
            Host = uri.Host,
            Port = uri.Port > 0 ? uri.Port : 5432,
            Database = database,
            Username = user,
            Password = password,
            // Require = always encrypt; Npgsql does not validate the chain in this
            // mode, which suits managed providers that use their own roots.
            SslMode = MapSslMode(query.GetValueOrDefault("sslmode")),
        };

        // Preserve a commonly-supplied pooling hint when present.
        if (bool.TryParse(query.GetValueOrDefault("pooling"), out var pooling))
            builder.Pooling = pooling;

        return builder.ConnectionString;
    }

    private static Dictionary<string, string> ParseQuery(string query)
    {
        var result = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var pair in query.TrimStart('?').Split('&', StringSplitOptions.RemoveEmptyEntries))
        {
            var kv = pair.Split('=', 2);
            var key = Uri.UnescapeDataString(kv[0]);
            var val = kv.Length > 1 ? Uri.UnescapeDataString(kv[1]) : string.Empty;
            result[key] = val;
        }
        return result;
    }

    private static SslMode MapSslMode(string? sslMode) => sslMode?.ToLowerInvariant() switch
    {
        "disable" => SslMode.Disable,
        "allow" => SslMode.Allow,
        "prefer" => SslMode.Prefer,
        "require" => SslMode.Require,
        "verify-ca" => SslMode.VerifyCA,
        "verify-full" => SslMode.VerifyFull,
        _ => SslMode.Require,
    };
}
