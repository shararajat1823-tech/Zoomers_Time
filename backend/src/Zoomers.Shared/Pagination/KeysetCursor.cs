using System.Globalization;

namespace Zoomers.Shared.Pagination;

/// <summary>
/// A keyset pagination cursor over a (timestamp, id) ordering. Keyset (seek)
/// pagination is stable under inserts and avoids OFFSET scans on large tables.
/// </summary>
public readonly record struct KeysetCursor(DateTimeOffset CreatedAt, Guid Id)
{
    public string Encode() =>
        Cursor.Encode($"{CreatedAt.UtcTicks.ToString(CultureInfo.InvariantCulture)}|{Id:N}");

    public static bool TryParse(string? cursor, out KeysetCursor value)
    {
        value = default;
        if (!Cursor.TryDecode(cursor, out var raw))
            return false;

        var parts = raw.Split('|');
        if (parts.Length != 2) return false;
        if (!long.TryParse(parts[0], NumberStyles.Integer, CultureInfo.InvariantCulture, out var ticks)) return false;
        if (!Guid.TryParse(parts[1], out var id)) return false;

        value = new KeysetCursor(new DateTimeOffset(ticks, TimeSpan.Zero), id);
        return true;
    }
}

public static class PageLimit
{
    public const int Default = 20;
    public const int Max = 50;

    public static int Clamp(int requested) => requested switch
    {
        <= 0 => Default,
        > Max => Max,
        _ => requested
    };
}
