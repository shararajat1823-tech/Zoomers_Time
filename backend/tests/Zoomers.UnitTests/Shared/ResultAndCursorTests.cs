using FluentAssertions;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;
using Xunit;

namespace Zoomers.UnitTests.Shared;

public class ResultTests
{
    [Fact]
    public void Success_result_exposes_value()
    {
        Result<int> r = 42;
        r.IsSuccess.Should().BeTrue();
        r.Value.Should().Be(42);
    }

    [Fact]
    public void Failure_result_carries_error_and_throws_on_value()
    {
        Result<int> r = Error.NotFound("x.not_found", "nope");
        r.IsFailure.Should().BeTrue();
        r.Error!.Kind.Should().Be(ErrorKind.NotFound);
        var act = () => r.Value;
        act.Should().Throw<InvalidOperationException>();
    }

    [Fact]
    public void Error_implicitly_converts_to_nongeneric_result()
    {
        Result r = Error.Validation("x.bad", "bad");
        r.IsFailure.Should().BeTrue();
        r.Error!.Code.Should().Be("x.bad");
    }
}

public class CursorTests
{
    [Theory]
    [InlineData("2026-01-01T00:00:00Z|abc")]
    [InlineData("simple")]
    [InlineData("has spaces and / + chars")]
    public void Encode_then_decode_roundtrips(string raw)
    {
        var encoded = Cursor.Encode(raw);
        encoded.Should().NotContain("+").And.NotContain("/").And.NotContain("=");

        Cursor.TryDecode(encoded, out var decoded).Should().BeTrue();
        decoded.Should().Be(raw);
    }

    [Fact]
    public void Decode_rejects_null_or_garbage()
    {
        Cursor.TryDecode(null, out _).Should().BeFalse();
        Cursor.TryDecode("", out _).Should().BeFalse();
    }

    [Fact]
    public void Empty_page_has_no_more()
    {
        var page = CursorPage<int>.Empty();
        page.HasMore.Should().BeFalse();
        page.Items.Should().BeEmpty();
    }
}
