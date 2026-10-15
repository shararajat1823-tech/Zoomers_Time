using System.Net;
using System.Net.Http.Json;
using FluentAssertions;
using Zoomers.Application.Identity.Dtos;
using Zoomers.Application.Profiles.Dtos;
using Zoomers.Application.Social.Dtos;
using Zoomers.Shared.Pagination;
using Xunit;

namespace Zoomers.IntegrationTests;

[Collection("api")]
public sealed class SocialGraphTests : IAsyncLifetime
{
    private readonly ApiFixture _fixture;
    private readonly HttpClient _client;

    public SocialGraphTests(ApiFixture fixture)
    {
        _fixture = fixture;
        _client = fixture.Factory.CreateClient();
    }

    public async Task InitializeAsync() => await _fixture.Factory.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    private async Task SetPrivate(AuthResponse user, bool isPrivate)
    {
        var res = await _client.PatchAuthAsync("/api/v1/users/me", user.AccessToken,
            new UpdateProfileRequest(null, null, null, isPrivate));
        res.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task Follow_public_account_is_instant_and_updates_counts()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");

        var follow = await _client.PostAuthAsync($"/api/v1/users/{bob.User.Id}/follow", alice.AccessToken);
        follow.StatusCode.Should().Be(HttpStatusCode.OK);
        (await follow.Content.ReadFromJsonAsync<FollowResult>())!.Outcome.Should().Be(FollowOutcome.Following);

        // Bob's profile should now show the relationship + follower count via Alice's view.
        var view = await _client.GetAuthAsync("/api/v1/users/bob", alice.AccessToken);
        var profile = (await view.Content.ReadFromJsonAsync<PublicProfileView>())!;
        profile.FollowerCount.Should().Be(1);
        profile.Relationship.IsFollowing.Should().BeTrue();
        profile.CanViewPosts.Should().BeTrue();
    }

    [Fact]
    public async Task Duplicate_follow_is_idempotent_not_an_error()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");

        await _client.PostAuthAsync($"/api/v1/users/{bob.User.Id}/follow", alice.AccessToken);
        var again = await _client.PostAuthAsync($"/api/v1/users/{bob.User.Id}/follow", alice.AccessToken);
        again.StatusCode.Should().Be(HttpStatusCode.OK);
        (await again.Content.ReadFromJsonAsync<FollowResult>())!.Outcome.Should().Be(FollowOutcome.AlreadyFollowing);

        // Count must not double.
        var view = await _client.GetAuthAsync("/api/v1/users/bob", alice.AccessToken);
        (await view.Content.ReadFromJsonAsync<PublicProfileView>())!.FollowerCount.Should().Be(1);
    }

    [Fact]
    public async Task Cannot_follow_self()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var res = await _client.PostAuthAsync($"/api/v1/users/{alice.User.Id}/follow", alice.AccessToken);
        res.StatusCode.Should().Be(HttpStatusCode.BadRequest);
    }

    [Fact]
    public async Task Follow_private_account_creates_request_and_grants_no_access()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        await SetPrivate(bob, true);

        var follow = await _client.PostAuthAsync($"/api/v1/users/{bob.User.Id}/follow", alice.AccessToken);
        (await follow.Content.ReadFromJsonAsync<FollowResult>())!.Outcome.Should().Be(FollowOutcome.Requested);

        // Pending request must NOT grant content access.
        var view = await _client.GetAuthAsync("/api/v1/users/bob", alice.AccessToken);
        var profile = (await view.Content.ReadFromJsonAsync<PublicProfileView>())!;
        profile.CanViewPosts.Should().BeFalse();
        profile.Relationship.HasOutgoingRequest.Should().BeTrue();
        profile.Relationship.IsFollowing.Should().BeFalse();

        // Followers list of a private account is forbidden to a non-follower.
        var followers = await _client.GetAuthAsync($"/api/v1/users/{bob.User.Id}/followers", alice.AccessToken);
        followers.StatusCode.Should().Be(HttpStatusCode.Forbidden);
    }

    [Fact]
    public async Task Accepting_request_creates_follow_and_grants_access()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        await SetPrivate(bob, true);
        await _client.PostAuthAsync($"/api/v1/users/{bob.User.Id}/follow", alice.AccessToken);

        // Bob sees the incoming request and accepts it.
        var reqList = await _client.GetAuthAsync("/api/v1/users/me/follow-requests", bob.AccessToken);
        var page = (await reqList.Content.ReadFromJsonAsync<CursorPage<FollowRequestItem>>())!;
        page.Items.Should().HaveCount(1);
        var requestId = page.Items[0].RequestId;

        var accept = await _client.PostAuthAsync($"/api/v1/follow-requests/{requestId}/accept", bob.AccessToken);
        accept.StatusCode.Should().Be(HttpStatusCode.NoContent);

        // Alice is now an accepted follower with access.
        var view = await _client.GetAuthAsync("/api/v1/users/bob", alice.AccessToken);
        var profile = (await view.Content.ReadFromJsonAsync<PublicProfileView>())!;
        profile.Relationship.IsFollowing.Should().BeTrue();
        profile.CanViewPosts.Should().BeTrue();
        profile.FollowerCount.Should().Be(1);
    }

    [Fact]
    public async Task Only_target_can_accept_a_request()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        var mallory = await _client.RegisterUserAsync("mallory");
        await SetPrivate(bob, true);
        await _client.PostAuthAsync($"/api/v1/users/{bob.User.Id}/follow", alice.AccessToken);

        var page = (await (await _client.GetAuthAsync("/api/v1/users/me/follow-requests", bob.AccessToken))
            .Content.ReadFromJsonAsync<CursorPage<FollowRequestItem>>())!;
        var requestId = page.Items[0].RequestId;

        // A third party cannot accept someone else's request.
        var attempt = await _client.PostAuthAsync($"/api/v1/follow-requests/{requestId}/accept", mallory.AccessToken);
        attempt.StatusCode.Should().Be(HttpStatusCode.NotFound);
    }

    [Fact]
    public async Task Switching_to_public_auto_approves_pending_requests()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        await SetPrivate(bob, true);
        await _client.PostAuthAsync($"/api/v1/users/{bob.User.Id}/follow", alice.AccessToken);

        await SetPrivate(bob, false); // go public

        var view = await _client.GetAuthAsync("/api/v1/users/bob", alice.AccessToken);
        var profile = (await view.Content.ReadFromJsonAsync<PublicProfileView>())!;
        profile.Relationship.IsFollowing.Should().BeTrue();
        profile.FollowerCount.Should().Be(1);
    }

    [Fact]
    public async Task Blocking_makes_users_mutually_invisible_and_removes_edges()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        await _client.PostAuthAsync($"/api/v1/users/{bob.User.Id}/follow", alice.AccessToken);

        // Bob blocks Alice.
        var block = await _client.PostAuthAsync($"/api/v1/users/{alice.User.Id}/block", bob.AccessToken);
        block.StatusCode.Should().Be(HttpStatusCode.NoContent);

        // Alice can no longer see Bob's profile (appears not found).
        var aliceView = await _client.GetAuthAsync("/api/v1/users/bob", alice.AccessToken);
        aliceView.StatusCode.Should().Be(HttpStatusCode.NotFound);

        // Bob cannot see Alice either.
        var bobView = await _client.GetAuthAsync("/api/v1/users/alice", bob.AccessToken);
        bobView.StatusCode.Should().Be(HttpStatusCode.NotFound);

        // The follow edge was removed.
        await _client.DeleteAuthAsync($"/api/v1/users/{alice.User.Id}/block", bob.AccessToken); // unblock to inspect
        var afterUnblock = await _client.GetAuthAsync("/api/v1/users/bob", alice.AccessToken);
        var profile = (await afterUnblock.Content.ReadFromJsonAsync<PublicProfileView>())!;
        profile.FollowerCount.Should().Be(0);
        profile.Relationship.IsFollowing.Should().BeFalse();
    }

    [Fact]
    public async Task Blocked_user_cannot_follow()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        await _client.PostAuthAsync($"/api/v1/users/{alice.User.Id}/block", bob.AccessToken);

        var follow = await _client.PostAuthAsync($"/api/v1/users/{bob.User.Id}/follow", alice.AccessToken);
        follow.StatusCode.Should().Be(HttpStatusCode.NotFound);
    }

    [Fact]
    public async Task Followers_list_pages_with_cursor()
    {
        var target = await _client.RegisterUserAsync("target");
        for (var i = 0; i < 3; i++)
        {
            var follower = await _client.RegisterUserAsync($"follower{i}");
            var r = await _client.PostAuthAsync($"/api/v1/users/{target.User.Id}/follow", follower.AccessToken);
            r.StatusCode.Should().Be(HttpStatusCode.OK);
        }

        var firstPage = await _client.GetAuthAsync($"/api/v1/users/{target.User.Id}/followers?limit=2", target.AccessToken);
        var page1 = (await firstPage.Content.ReadFromJsonAsync<CursorPage<UserListItem>>())!;
        page1.Items.Should().HaveCount(2);
        page1.HasMore.Should().BeTrue();

        var secondPage = await _client.GetAuthAsync(
            $"/api/v1/users/{target.User.Id}/followers?limit=2&cursor={Uri.EscapeDataString(page1.NextCursor!)}",
            target.AccessToken);
        var page2 = (await secondPage.Content.ReadFromJsonAsync<CursorPage<UserListItem>>())!;
        page2.Items.Should().HaveCount(1);
        page2.HasMore.Should().BeFalse();
    }

    [Fact]
    public async Task Remove_follower_drops_the_edge()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        await _client.PostAuthAsync($"/api/v1/users/{bob.User.Id}/follow", alice.AccessToken);

        // Bob removes Alice as a follower.
        var remove = await _client.DeleteAuthAsync($"/api/v1/users/{alice.User.Id}/follower", bob.AccessToken);
        remove.StatusCode.Should().Be(HttpStatusCode.NoContent);

        var view = await _client.GetAuthAsync("/api/v1/users/bob", alice.AccessToken);
        (await view.Content.ReadFromJsonAsync<PublicProfileView>())!.FollowerCount.Should().Be(0);
    }

    [Fact]
    public async Task Unknown_username_is_not_found()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var res = await _client.GetAuthAsync("/api/v1/users/ghost", alice.AccessToken);
        res.StatusCode.Should().Be(HttpStatusCode.NotFound);
    }
}
