using System.Net;
using System.Net.Http.Json;
using FluentAssertions;
using Zoomers.Application.Posts.Dtos;
using Zoomers.Application.Search.Dtos;
using Zoomers.Application.Settings.Dtos;
using Zoomers.Application.Social.Dtos;
using Zoomers.Domain.Posts;
using Zoomers.Domain.Profiles;
using Zoomers.Shared.Pagination;
using Xunit;

namespace Zoomers.IntegrationTests;

[Collection("api")]
public sealed class SearchAndSettingsTests : IAsyncLifetime
{
    private readonly ApiFixture _fixture;
    private readonly HttpClient _client;

    public SearchAndSettingsTests(ApiFixture fixture)
    {
        _fixture = fixture;
        _client = fixture.Factory.CreateClient();
    }

    public async Task InitializeAsync() => await _fixture.Factory.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    [Fact]
    public async Task User_search_finds_by_username_and_excludes_blocked()
    {
        var finder = await _client.RegisterUserAsync("finder");
        var ada = await _client.RegisterUserAsync("adalovelace");
        var blocked = await _client.RegisterUserAsync("adaboop");

        // 'blocked' blocks the finder → must not appear in finder's results.
        await _client.PostAuthAsync($"/api/v1/users/{finder.User.Id}/block", blocked.AccessToken);

        var res = await _client.GetAuthAsync("/api/v1/search/users?q=ada", finder.AccessToken);
        var page = (await res.Content.ReadFromJsonAsync<CursorPage<UserListItem>>())!;

        page.Items.Should().Contain(u => u.Id == ada.User.Id);
        page.Items.Should().NotContain(u => u.Id == blocked.User.Id);
    }

    [Fact]
    public async Task Hashtags_are_extracted_on_post_create_and_searchable()
    {
        var alice = await _client.RegisterUserAsync("alice");
        await CreatePostWithCaption(alice.AccessToken, "sunset vibes #travel #Sunset");

        var search = await _client.GetAuthAsync("/api/v1/search/hashtags?q=sun", alice.AccessToken);
        var tags = (await search.Content.ReadFromJsonAsync<CursorPage<HashtagResult>>())!;
        tags.Items.Should().Contain(t => t.Tag == "sunset");

        // Hashtag feed returns the post.
        var hashtagPosts = await _client.GetAuthAsync("/api/v1/hashtags/travel/posts", alice.AccessToken);
        var posts = (await hashtagPosts.Content.ReadFromJsonAsync<CursorPage<PostView>>())!;
        posts.Items.Should().ContainSingle();
    }

    [Fact]
    public async Task Post_search_only_returns_public_posts_on_public_accounts()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");

        await CreatePostWithCaption(alice.AccessToken, "a public #galaxy photo", PostVisibility.Public);
        await CreatePostWithCaption(alice.AccessToken, "a private #galaxy photo", PostVisibility.Private);

        var res = await _client.GetAuthAsync("/api/v1/search/posts?q=galaxy", bob.AccessToken);
        var page = (await res.Content.ReadFromJsonAsync<CursorPage<PostView>>())!;

        page.Items.Should().ContainSingle();
        page.Items[0].Visibility.Should().Be(PostVisibility.Public);
    }

    [Fact]
    public async Task Settings_can_be_read_and_updated()
    {
        var alice = await _client.RegisterUserAsync("alice");

        var get = await _client.GetAuthAsync("/api/v1/settings", alice.AccessToken);
        var settings = (await get.Content.ReadFromJsonAsync<SettingsView>())!;
        settings.Theme.Should().Be(ThemePreference.System);

        var update = await _client.PatchAuthAsync("/api/v1/settings", alice.AccessToken,
            new UpdateSettingsRequest(ThemePreference.Dark, "fr", AudienceControl.FollowersOnly, null, null, false, null, null, null, null));
        update.StatusCode.Should().Be(HttpStatusCode.OK);
        var updated = (await update.Content.ReadFromJsonAsync<SettingsView>())!;
        updated.Theme.Should().Be(ThemePreference.Dark);
        updated.Language.Should().Be("fr");
        updated.WhoCanComment.Should().Be(AudienceControl.FollowersOnly);
        updated.ShowOnlineStatus.Should().BeFalse();
    }

    [Fact]
    public async Task Comment_control_followers_only_blocks_non_followers()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        await _client.PatchAuthAsync("/api/v1/settings", alice.AccessToken,
            new UpdateSettingsRequest(null, null, AudienceControl.FollowersOnly, null, null, null, null, null, null, null));

        var post = await _client.CreateImagePostAsync(alice.AccessToken);

        // Bob (not a follower) cannot comment.
        var denied = await PostHelpers.AuthJsonAsync(_client, HttpMethod.Post, $"/api/v1/posts/{post.Id}/comments",
            bob.AccessToken, new Application.Engagement.Dtos.AddCommentRequest("hi", null));
        denied.StatusCode.Should().Be(HttpStatusCode.Forbidden);
    }

    [Fact]
    public async Task Muting_hides_posts_from_the_following_feed()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        await _client.CreateImagePostAsync(bob.AccessToken);
        await _client.PostAuthAsync($"/api/v1/users/{bob.User.Id}/follow", alice.AccessToken);

        // Before muting, Bob's post is in Alice's feed.
        var before = (await (await _client.GetAuthAsync("/api/v1/feed/following", alice.AccessToken))
            .Content.ReadFromJsonAsync<CursorPage<PostView>>())!;
        before.Items.Should().ContainSingle();

        // After muting Bob, the feed is empty, and Bob appears in the muted list.
        (await _client.PostAuthAsync($"/api/v1/users/{bob.User.Id}/mute", alice.AccessToken)).StatusCode
            .Should().Be(HttpStatusCode.NoContent);

        var after = (await (await _client.GetAuthAsync("/api/v1/feed/following", alice.AccessToken))
            .Content.ReadFromJsonAsync<CursorPage<PostView>>())!;
        after.Items.Should().BeEmpty();

        var muted = (await (await _client.GetAuthAsync("/api/v1/settings/muted", alice.AccessToken))
            .Content.ReadFromJsonAsync<CursorPage<UserListItem>>())!;
        muted.Items.Should().ContainSingle(u => u.Id == bob.User.Id);
    }

    [Fact]
    public async Task Suggested_users_excludes_already_followed()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        var carol = await _client.RegisterUserAsync("carol");

        await _client.PostAuthAsync($"/api/v1/users/{bob.User.Id}/follow", alice.AccessToken);

        var res = await _client.GetAuthAsync("/api/v1/search/suggested-users", alice.AccessToken);
        var page = (await res.Content.ReadFromJsonAsync<CursorPage<UserListItem>>())!;

        page.Items.Should().Contain(u => u.Id == carol.User.Id);
        page.Items.Should().NotContain(u => u.Id == bob.User.Id);  // already followed
        page.Items.Should().NotContain(u => u.Id == alice.User.Id); // self
    }

    private async Task<PostView> CreatePostWithCaption(string token, string caption, PostVisibility visibility = PostVisibility.Public)
    {
        var sessionId = await _client.UploadImageAsync(token);
        var req = new CreatePostRequest(caption, visibility, null, null,
            new[] { new CreatePostMediaItem(sessionId, 800, 600, null) });
        var res = await PostHelpers.AuthJsonAsync(_client, HttpMethod.Post, "/api/v1/posts", token, req);
        res.StatusCode.Should().Be(HttpStatusCode.Created);
        return (await res.Content.ReadFromJsonAsync<PostView>())!;
    }
}
