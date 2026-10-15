using FluentAssertions;
using Microsoft.Extensions.Options;
using Zoomers.Application.Abstractions;
using Zoomers.Domain.Identity;
using Zoomers.Infrastructure.Security;
using Zoomers.UnitTests.TestSupport;
using Xunit;

namespace Zoomers.UnitTests.Security;

public class PasswordHasherAdapterTests
{
    private readonly PasswordHasherAdapter _hasher = new();

    [Fact]
    public void Hash_is_not_plaintext_and_verifies()
    {
        var hash = _hasher.Hash("Sup3rSecret!");
        hash.Should().NotBe("Sup3rSecret!");
        _hasher.Verify(hash, "Sup3rSecret!").Should().Be(PasswordVerificationResult.Success);
    }

    [Fact]
    public void Wrong_password_fails()
    {
        var hash = _hasher.Hash("correct horse");
        _hasher.Verify(hash, "battery staple").Should().Be(PasswordVerificationResult.Failed);
    }
}

public class JwtTokenServiceTests
{
    private static JwtTokenService CreateService(FakeClock clock)
    {
        var options = Options.Create(new JwtOptions
        {
            Issuer = "zoomers",
            Audience = "zoomers.clients",
            SigningKey = "unit-test-signing-key-must-be-at-least-32-bytes-long!!",
            AccessTokenMinutes = 15,
            RefreshTokenDays = 30
        });
        return new JwtTokenService(options, clock);
    }

    [Fact]
    public void Access_token_has_expected_expiry_and_is_nonempty()
    {
        var now = new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero);
        var svc = CreateService(new FakeClock(now));
        var user = new User { Id = Guid.NewGuid(), Username = "ada" };

        var token = svc.CreateAccessToken(user);

        token.Value.Should().NotBeNullOrWhiteSpace();
        token.ExpiresAt.Should().Be(now.AddMinutes(15));
    }

    [Fact]
    public void Refresh_token_raw_is_not_stored_hash_but_hashes_consistently()
    {
        var svc = CreateService(new FakeClock(DateTimeOffset.UtcNow));
        var issued = svc.CreateRefreshToken();

        issued.RawValue.Should().NotBe(issued.Hash);
        svc.HashRefreshToken(issued.RawValue).Should().Be(issued.Hash);
    }

    [Fact]
    public void Two_refresh_tokens_are_unique()
    {
        var svc = CreateService(new FakeClock(DateTimeOffset.UtcNow));
        svc.CreateRefreshToken().RawValue.Should().NotBe(svc.CreateRefreshToken().RawValue);
    }
}
