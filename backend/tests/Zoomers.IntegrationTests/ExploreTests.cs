using System.Net;
using System.Net.Http.Json;
using FluentAssertions;
using Zoomers.Application.Posts.Dtos;
using Zoomers.Application.Profiles.Dtos;
using Zoomers.Domain.Posts;
using Zoomers.Shared.Pagination;
using Xunit;

namespace Zoomers.IntegrationTests;

[Collection("api")]
public sealed class ExploreTests : IAsyncLifetime
{
    private readonly ApiFixture _fixture;
    private readonly HttpClient _client;

    public ExploreTests(ApiFixture fixture)
    {
        _fixture = fixture;
        _client = fixture.Factory.CreateClient();
    }

    public async Task InitializeAsync() => await _fixture.Factory.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    private async Task<CursorPage<PostView>> ExploreAsync(string token)
    {
        var res = await _client.GetAuthAsync("/api/v1/search/explore", token);
        res.StatusCode.Should().Be(HttpStatusCode.OK);
        return (await res.Content.ReadFromJsonAsync<CursorPage<PostView>>())!;
    }

    [Fact]
    public async Task Explore_shows_others_public_posts_but_not_your_own()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");

        await _client.CreateImagePostAsync(bob.AccessToken, PostVisibility.Public, "bob's post");
        await _client.CreateImagePostAsync(alice.AccessToken, PostVisibility.Public, "alice's own");

        var page = await ExploreAsync(alice.AccessToken);
        page.Items.Select(p => p.Author.Username).Should().Contain("bob");
        page.Items.Select(p => p.Author.Username).Should().NotContain("alice");
    }

    [Fact]
    public async Task Explore_excludes_private_accounts()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var dave = await _client.RegisterUserAsync("dave");

        // Dave goes private, then posts.
        var setPrivate = await _client.PatchAuthAsync("/api/v1/users/me", dave.AccessToken,
            new UpdateProfileRequest(null, null, null, true));
        setPrivate.StatusCode.Should().Be(HttpStatusCode.OK);
        await _client.CreateImagePostAsync(dave.AccessToken, PostVisibility.Public, "secret");

        var page = await ExploreAsync(alice.AccessToken);
        page.Items.Select(p => p.Author.Username).Should().NotContain("dave");
    }

    [Fact]
    public async Task Explore_excludes_blocked_accounts()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var erin = await _client.RegisterUserAsync("erin");
        await _client.CreateImagePostAsync(erin.AccessToken, PostVisibility.Public, "erin's post");

        // Alice blocks erin -> erin's posts must not surface in Alice's explore.
        (await _client.PostAuthAsync($"/api/v1/users/{erin.User.Id}/block", alice.AccessToken))
            .StatusCode.Should().Be(HttpStatusCode.NoContent);

        var page = await ExploreAsync(alice.AccessToken);
        page.Items.Select(p => p.Author.Username).Should().NotContain("erin");
    }
}
