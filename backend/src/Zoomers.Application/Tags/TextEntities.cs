using System.Text.RegularExpressions;

namespace Zoomers.Application.Tags;

/// <summary>Parses #hashtags and @mentions out of free text (captions, comments).</summary>
public static partial class TextEntities
{
    [GeneratedRegex(@"(?<![\w])#(\w{1,100})", RegexOptions.Compiled)]
    private static partial Regex HashtagRegex();

    [GeneratedRegex(@"(?<![\w])@([a-zA-Z0-9._]{3,30})", RegexOptions.Compiled)]
    private static partial Regex MentionRegex();

    /// <summary>Distinct, lowercased hashtags (without '#').</summary>
    public static IReadOnlyList<string> ExtractHashtags(string? text)
    {
        if (string.IsNullOrWhiteSpace(text)) return Array.Empty<string>();
        return HashtagRegex().Matches(text)
            .Select(m => m.Groups[1].Value.ToLowerInvariant())
            .Distinct()
            .Take(30)
            .ToList();
    }

    /// <summary>Distinct, lowercased usernames mentioned (without '@').</summary>
    public static IReadOnlyList<string> ExtractMentions(string? text)
    {
        if (string.IsNullOrWhiteSpace(text)) return Array.Empty<string>();
        return MentionRegex().Matches(text)
            .Select(m => m.Groups[1].Value.ToLowerInvariant())
            .Distinct()
            .Take(30)
            .ToList();
    }
}
