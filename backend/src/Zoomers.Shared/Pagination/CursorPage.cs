using System.Text;

namespace Zoomers.Shared.Pagination;

/// <summary>
/// A page of results plus an opaque cursor pointing at the next page. Used for
/// every large collection (feed, comments, search, followers) so clients never
/// download an entire table and pagination is stable under inserts.
/// </summary>
public sealed record CursorPage<T>(IReadOnlyList<T> Items, string? NextCursor)
{
    public bool HasMore => NextCursor is not null;

    public static CursorPage<T> Empty() => new(Array.Empty<T>(), null);
}

/// <summary>
/// Encodes/decodes opaque pagination cursors. The cursor is a base64url string
/// so clients treat it as a token rather than depending on its internals,
/// which lets the server change the cursor shape without breaking clients.
/// </summary>
public static class Cursor
{
    public static string Encode(string raw)
    {
        var bytes = Encoding.UTF8.GetBytes(raw);
        return Convert.ToBase64String(bytes)
            .TrimEnd('=')
            .Replace('+', '-')
            .Replace('/', '_');
    }

    public static bool TryDecode(string? cursor, out string raw)
    {
        raw = string.Empty;
        if (string.IsNullOrWhiteSpace(cursor))
            return false;

        try
        {
            var normalized = cursor.Replace('-', '+').Replace('_', '/');
            switch (normalized.Length % 4)
            {
                case 2: normalized += "=="; break;
                case 3: normalized += "="; break;
            }

            var bytes = Convert.FromBase64String(normalized);
            raw = Encoding.UTF8.GetString(bytes);
            return true;
        }
        catch (FormatException)
        {
            return false;
        }
    }
}
