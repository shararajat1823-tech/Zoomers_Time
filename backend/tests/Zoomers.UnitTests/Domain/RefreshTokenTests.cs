using FluentAssertions;
using Zoomers.Domain.Identity;
using Xunit;

namespace Zoomers.UnitTests.Domain;

public class RefreshTokenTests
{
    private static readonly DateTimeOffset Now = new(2026, 1, 1, 0, 0, 0, TimeSpan.Zero);

    [Fact]
    public void Active_token_is_active()
    {
        var token = new RefreshToken { ExpiresAt = Now.AddDays(1) };
        token.IsActive(Now).Should().BeTrue();
        token.IsExpired(Now).Should().BeFalse();
        token.IsRevoked.Should().BeFalse();
    }

    [Fact]
    public void Expired_token_is_not_active()
    {
        var token = new RefreshToken { ExpiresAt = Now.AddSeconds(-1) };
        token.IsActive(Now).Should().BeFalse();
        token.IsExpired(Now).Should().BeTrue();
    }

    [Fact]
    public void Revoked_token_is_not_active_even_before_expiry()
    {
        var token = new RefreshToken { ExpiresAt = Now.AddDays(1), RevokedAt = Now };
        token.IsActive(Now).Should().BeFalse();
        token.IsRevoked.Should().BeTrue();
    }
}
