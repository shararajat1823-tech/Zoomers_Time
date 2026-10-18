using System.Net;
using FluentAssertions;
using Zoomers.Application.Devices;
using Xunit;

namespace Zoomers.IntegrationTests;

[Collection("api")]
public sealed class PushDevicesTests : IAsyncLifetime
{
    private readonly ApiFixture _fixture;
    private readonly HttpClient _client;

    public PushDevicesTests(ApiFixture fixture)
    {
        _fixture = fixture;
        _client = fixture.Factory.CreateClient();
    }

    public async Task InitializeAsync() => await _fixture.Factory.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    [Fact]
    public async Task Register_refresh_and_remove_device_token()
    {
        var alice = await _client.RegisterUserAsync("alice");

        var register = await _client.PostAuthAsync("/api/v1/me/devices", alice.AccessToken,
            new RegisterDeviceRequest("fcm-token-123", "android"));
        register.StatusCode.Should().Be(HttpStatusCode.NoContent);

        // Re-registering the same token is idempotent (refresh), not an error.
        var again = await _client.PostAuthAsync("/api/v1/me/devices", alice.AccessToken,
            new RegisterDeviceRequest("fcm-token-123", "android"));
        again.StatusCode.Should().Be(HttpStatusCode.NoContent);

        var remove = await _client.PostAuthAsync("/api/v1/me/devices/remove", alice.AccessToken,
            new RemoveDeviceRequest("fcm-token-123"));
        remove.StatusCode.Should().Be(HttpStatusCode.NoContent);

        // Removing an unknown token is a no-op success.
        var removeAgain = await _client.PostAuthAsync("/api/v1/me/devices/remove", alice.AccessToken,
            new RemoveDeviceRequest("fcm-token-123"));
        removeAgain.StatusCode.Should().Be(HttpStatusCode.NoContent);
    }

    [Fact]
    public async Task Empty_token_is_rejected()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var res = await _client.PostAuthAsync("/api/v1/me/devices", alice.AccessToken,
            new RegisterDeviceRequest("   ", "android"));
        res.StatusCode.Should().Be(HttpStatusCode.BadRequest);
    }

    [Fact]
    public async Task Registering_a_device_requires_auth()
    {
        var res = await _client.PostAsync("/api/v1/me/devices",
            JsonContent());
        res.StatusCode.Should().Be(HttpStatusCode.Unauthorized);

        static HttpContent JsonContent() =>
            System.Net.Http.Json.JsonContent.Create(new RegisterDeviceRequest("tok", "android"));
    }
}
