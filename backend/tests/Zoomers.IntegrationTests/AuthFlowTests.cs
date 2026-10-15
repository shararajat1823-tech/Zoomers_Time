using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using FluentAssertions;
using Zoomers.Application.Identity.Dtos;
using Zoomers.Application.Profiles;
using Xunit;

namespace Zoomers.IntegrationTests;

[Collection("api")]
public sealed class AuthFlowTests : IAsyncLifetime
{
    private readonly ApiFixture _fixture;
    private readonly HttpClient _client;

    public AuthFlowTests(ApiFixture fixture)
    {
        _fixture = fixture;
        _client = fixture.Factory.CreateClient();
    }

    public async Task InitializeAsync() => await _fixture.Factory.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    private static RegisterRequest NewUser(string username) =>
        new(username, $"{username}@example.com", "Sup3rSecret!", $"{username} Display");

    private async Task<AuthResponse> RegisterAsync(string username)
    {
        var res = await _client.PostAsJsonAsync("/api/v1/auth/register", NewUser(username));
        res.StatusCode.Should().Be(HttpStatusCode.Created);
        return (await res.Content.ReadFromJsonAsync<AuthResponse>())!;
    }

    [Fact]
    public async Task Register_creates_account_and_returns_tokens()
    {
        var auth = await RegisterAsync("ada");

        auth.AccessToken.Should().NotBeNullOrWhiteSpace();
        auth.RefreshToken.Should().NotBeNullOrWhiteSpace();
        auth.User.Username.Should().Be("ada");
        auth.AccessTokenExpiresAt.Should().BeAfter(DateTimeOffset.UtcNow);
    }

    [Fact]
    public async Task Register_duplicate_username_conflicts()
    {
        await RegisterAsync("grace");
        var dup = await _client.PostAsJsonAsync("/api/v1/auth/register",
            new RegisterRequest("grace", "other@example.com", "Sup3rSecret!", "Grace Two"));
        dup.StatusCode.Should().Be(HttpStatusCode.Conflict);
    }

    [Fact]
    public async Task Register_invalid_password_is_rejected()
    {
        var res = await _client.PostAsJsonAsync("/api/v1/auth/register",
            new RegisterRequest("shorty", "shorty@example.com", "123", "Shorty"));
        res.StatusCode.Should().Be(HttpStatusCode.BadRequest);
    }

    [Fact]
    public async Task Login_succeeds_with_correct_password()
    {
        await RegisterAsync("linus");
        var res = await _client.PostAsJsonAsync("/api/v1/auth/login",
            new LoginRequest("linus", "Sup3rSecret!"));
        res.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task Login_with_wrong_password_is_generic_unauthorized()
    {
        await RegisterAsync("katherine");
        var res = await _client.PostAsJsonAsync("/api/v1/auth/login",
            new LoginRequest("katherine", "wrong-password"));
        res.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Fact]
    public async Task Login_unknown_user_is_also_generic_unauthorized()
    {
        var res = await _client.PostAsJsonAsync("/api/v1/auth/login",
            new LoginRequest("nobody-here", "whatever12"));
        res.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Fact]
    public async Task Me_requires_authentication()
    {
        var res = await _client.GetAsync("/api/v1/users/me");
        res.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Fact]
    public async Task Me_returns_profile_with_valid_token()
    {
        var auth = await RegisterAsync("margaret");
        var req = new HttpRequestMessage(HttpMethod.Get, "/api/v1/users/me");
        req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", auth.AccessToken);

        var res = await _client.SendAsync(req);
        res.StatusCode.Should().Be(HttpStatusCode.OK);

        var profile = await res.Content.ReadFromJsonAsync<ProfileView>();
        profile!.Username.Should().Be("margaret");
        profile.FollowerCount.Should().Be(0);
    }

    [Fact]
    public async Task Refresh_rotates_token_and_old_token_is_rejected()
    {
        var auth = await RegisterAsync("alan");

        // First refresh: should succeed and return a new refresh token.
        var first = await _client.PostAsJsonAsync("/api/v1/auth/refresh", new RefreshRequest(auth.RefreshToken));
        first.StatusCode.Should().Be(HttpStatusCode.OK);
        var rotated = (await first.Content.ReadFromJsonAsync<AuthResponse>())!;
        rotated.RefreshToken.Should().NotBe(auth.RefreshToken);

        // Reusing the ORIGINAL (now revoked) refresh token must fail...
        var reuse = await _client.PostAsJsonAsync("/api/v1/auth/refresh", new RefreshRequest(auth.RefreshToken));
        reuse.StatusCode.Should().Be(HttpStatusCode.Unauthorized);

        // ...and must have revoked the whole chain, so the rotated token is dead too.
        var afterBreach = await _client.PostAsJsonAsync("/api/v1/auth/refresh", new RefreshRequest(rotated.RefreshToken));
        afterBreach.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Fact]
    public async Task Logout_revokes_refresh_token()
    {
        var auth = await RegisterAsync("dennis");

        var logout = await _client.PostAsJsonAsync("/api/v1/auth/logout", new LogoutRequest(auth.RefreshToken));
        logout.StatusCode.Should().Be(HttpStatusCode.NoContent);

        var refresh = await _client.PostAsJsonAsync("/api/v1/auth/refresh", new RefreshRequest(auth.RefreshToken));
        refresh.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Fact]
    public async Task Forgot_password_always_succeeds_even_for_unknown_email()
    {
        var res = await _client.PostAsJsonAsync("/api/v1/auth/forgot-password",
            new ForgotPasswordRequest("does-not-exist@example.com"));
        res.StatusCode.Should().Be(HttpStatusCode.NoContent);
    }

    [Fact]
    public async Task Reset_password_with_invalid_token_is_rejected()
    {
        await RegisterAsync("edsger");
        var res = await _client.PostAsJsonAsync("/api/v1/auth/reset-password",
            new ResetPasswordRequest("edsger@example.com", "not-a-real-token", "BrandNewPass1"));
        res.StatusCode.Should().Be(HttpStatusCode.BadRequest);
    }

    [Fact]
    public async Task Tampered_access_token_is_rejected()
    {
        var auth = await RegisterAsync("barbara");
        var req = new HttpRequestMessage(HttpMethod.Get, "/api/v1/users/me");
        req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", auth.AccessToken + "x");

        var res = await _client.SendAsync(req);
        res.StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }
}
