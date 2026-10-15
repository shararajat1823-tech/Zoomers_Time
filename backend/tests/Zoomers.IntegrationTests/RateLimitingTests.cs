using System.Net;
using System.Net.Http.Json;
using FluentAssertions;
using Zoomers.Application.Identity.Dtos;
using Xunit;

namespace Zoomers.IntegrationTests;

/// <summary>
/// Uses its own app instance with a deliberately low auth limit so the fixed
/// window is exhausted deterministically, verifying abusive auth traffic is
/// throttled with HTTP 429.
/// </summary>
public sealed class RateLimitingTests : IAsyncLifetime
{
    private readonly ZoomersApiFactory _factory = new() { AuthPermitLimit = 3 };

    public async Task InitializeAsync()
    {
        await _factory.InitializeDatabaseAsync();
        await _factory.ResetAsync();
    }

    public async Task DisposeAsync() => await _factory.DisposeAsync();

    [Fact]
    public async Task Auth_endpoint_throttles_after_limit()
    {
        var client = _factory.CreateClient();
        var statuses = new List<HttpStatusCode>();

        for (var i = 0; i < 6; i++)
        {
            var res = await client.PostAsJsonAsync("/api/v1/auth/login",
                new LoginRequest("nobody", "whatever12"));
            statuses.Add(res.StatusCode);
        }

        // With a permit limit of 3, later requests in the same window must be rejected.
        statuses.Should().Contain(HttpStatusCode.TooManyRequests);
    }
}
