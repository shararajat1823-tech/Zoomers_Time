using System.Net;
using System.Net.Http.Json;
using FluentAssertions;
using Zoomers.Application.Engagement.Dtos;
using Zoomers.Application.Posts.Dtos;
using Zoomers.Application.Profiles;
using Zoomers.Domain.Posts;
using Zoomers.Shared.Pagination;
using Xunit;

namespace Zoomers.IntegrationTests;

[Collection("api")]
public sealed class PostsAndFeedTests : IAsyncLifetime
{
    private readonly ApiFixture _fixture;
    private readonly HttpClient _client;

    public PostsAndFeedTests(ApiFixture fixture)
    {
        _fixture = fixture;
        _client = fixture.Factory.CreateClient();
    }

    public async Task InitializeAsync() => await _fixture.Factory.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    [Fact]
    public async Task Full_upload_and_post_flow_works()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var post = await _client.CreateImagePostAsync(alice.AccessToken, PostVisibility.Public, "my first post");

        post.Caption.Should().Be("my first post");
        post.Type.Should().Be(PostType.Image);
        post.Media.Should().HaveCount(1);
        post.Media[0].Url.Should().NotBeNullOrWhiteSpace();

        // The post is retrievable and the author's post count incremented.
        var get = await _client.GetAuthAsync($"/api/v1/posts/{post.Id}", alice.AccessToken);
        get.StatusCode.Should().Be(HttpStatusCode.OK);

        var me = await (await _client.GetAuthAsync("/api/v1/users/me", alice.AccessToken))
            .Content.ReadFromJsonAsync<ProfileView>();
        me!.PostCount.Should().Be(1);
    }

    [Fact]
    public async Task Create_post_with_incomplete_media_is_rejected()
    {
        var alice = await _client.RegisterUserAsync("alice");

        // Create a session but never upload/complete it.
        var create = await PostHelpers.AuthJsonAsync(_client, HttpMethod.Post, "/api/v1/media/upload-sessions",
            alice.AccessToken, new Application.Media.Dtos.CreateUploadSessionRequest("PostMedia", "image/jpeg"));
        var session = (await create.Content.ReadFromJsonAsync<Application.Media.Dtos.UploadSessionResponse>())!;

        var req = new CreatePostRequest("x", PostVisibility.Public, null, null,
            new[] { new CreatePostMediaItem(session.SessionId, null, null, null) });
        var res = await PostHelpers.AuthJsonAsync(_client, HttpMethod.Post, "/api/v1/posts", alice.AccessToken, req);
        res.StatusCode.Should().Be(HttpStatusCode.BadRequest);
    }

    [Fact]
    public async Task Private_post_and_its_media_are_hidden_from_others()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        var post = await _client.CreateImagePostAsync(alice.AccessToken, PostVisibility.Private);

        // Bob cannot fetch the private post.
        var get = await _client.GetAuthAsync($"/api/v1/posts/{post.Id}", bob.AccessToken);
        get.StatusCode.Should().Be(HttpStatusCode.NotFound);

        // Bob cannot download its media either (unauthorized media access).
        var mediaPath = new Uri(post.Media[0].Url, UriKind.RelativeOrAbsolute).ToString();
        var media = await _client.GetAuthAsync(mediaPath, bob.AccessToken);
        media.StatusCode.Should().Be(HttpStatusCode.NotFound);

        // The owner can download their own media.
        var ownerMedia = await _client.GetAuthAsync(mediaPath, alice.AccessToken);
        ownerMedia.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task Following_feed_shows_followed_authors_only()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        var carol = await _client.RegisterUserAsync("carol");

        var bobPost = await _client.CreateImagePostAsync(bob.AccessToken, PostVisibility.Public, "bob post");
        await _client.CreateImagePostAsync(carol.AccessToken, PostVisibility.Public, "carol post");

        // Alice follows only Bob.
        await _client.PostAuthAsync($"/api/v1/users/{bob.User.Id}/follow", alice.AccessToken);

        var feed = await (await _client.GetAuthAsync("/api/v1/feed/following", alice.AccessToken))
            .Content.ReadFromJsonAsync<CursorPage<PostView>>();

        feed!.Items.Should().ContainSingle();
        feed.Items[0].Id.Should().Be(bobPost.Id);
    }

    [Fact]
    public async Task Followers_only_post_is_hidden_from_non_followers()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        var post = await _client.CreateImagePostAsync(alice.AccessToken, PostVisibility.Followers);

        // Non-follower Bob cannot see it.
        (await _client.GetAuthAsync($"/api/v1/posts/{post.Id}", bob.AccessToken)).StatusCode
            .Should().Be(HttpStatusCode.NotFound);

        // After following, Bob can.
        await _client.PostAuthAsync($"/api/v1/users/{alice.User.Id}/follow", bob.AccessToken);
        (await _client.GetAuthAsync($"/api/v1/posts/{post.Id}", bob.AccessToken)).StatusCode
            .Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task Like_is_idempotent_and_count_is_consistent()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        var post = await _client.CreateImagePostAsync(alice.AccessToken);

        var first = await _client.PostAuthAsync($"/api/v1/posts/{post.Id}/likes", bob.AccessToken);
        var state1 = (await first.Content.ReadFromJsonAsync<LikeState>())!;
        state1.Liked.Should().BeTrue();
        state1.LikeCount.Should().Be(1);

        // Liking again must not double the count.
        var again = await _client.PostAuthAsync($"/api/v1/posts/{post.Id}/likes", bob.AccessToken);
        (await again.Content.ReadFromJsonAsync<LikeState>())!.LikeCount.Should().Be(1);

        // Unlike returns to 0.
        var unlike = await _client.DeleteAuthAsync($"/api/v1/posts/{post.Id}/likes", bob.AccessToken);
        var state2 = (await unlike.Content.ReadFromJsonAsync<LikeState>())!;
        state2.Liked.Should().BeFalse();
        state2.LikeCount.Should().Be(0);
    }

    [Fact]
    public async Task Save_and_unsave_round_trip_appears_in_saved_list()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        var post = await _client.CreateImagePostAsync(alice.AccessToken);

        await _client.PostAuthAsync($"/api/v1/posts/{post.Id}/save", bob.AccessToken);
        var saved = await (await _client.GetAuthAsync("/api/v1/users/me/saved", bob.AccessToken))
            .Content.ReadFromJsonAsync<CursorPage<PostView>>();
        saved!.Items.Should().ContainSingle(p => p.Id == post.Id);

        await _client.DeleteAuthAsync($"/api/v1/posts/{post.Id}/save", bob.AccessToken);
        var afterUnsave = await (await _client.GetAuthAsync("/api/v1/users/me/saved", bob.AccessToken))
            .Content.ReadFromJsonAsync<CursorPage<PostView>>();
        afterUnsave!.Items.Should().BeEmpty();
    }

    [Fact]
    public async Task Comments_can_be_added_listed_and_only_author_or_post_owner_can_delete()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        var mallory = await _client.RegisterUserAsync("mallory");
        var post = await _client.CreateImagePostAsync(alice.AccessToken);

        var add = await PostHelpers.AuthJsonAsync(_client, HttpMethod.Post, $"/api/v1/posts/{post.Id}/comments",
            bob.AccessToken, new AddCommentRequest("nice shot!", null));
        add.StatusCode.Should().Be(HttpStatusCode.Created);
        var comment = (await add.Content.ReadFromJsonAsync<CommentView>())!;

        var list = await (await _client.GetAuthAsync($"/api/v1/posts/{post.Id}/comments", alice.AccessToken))
            .Content.ReadFromJsonAsync<CursorPage<CommentView>>();
        list!.Items.Should().ContainSingle(c => c.Id == comment.Id);

        // A third party cannot delete someone else's comment on someone else's post.
        (await _client.DeleteAuthAsync($"/api/v1/comments/{comment.Id}", mallory.AccessToken)).StatusCode
            .Should().Be(HttpStatusCode.Forbidden);

        // The post owner can moderate (delete) the comment.
        (await _client.DeleteAuthAsync($"/api/v1/comments/{comment.Id}", alice.AccessToken)).StatusCode
            .Should().Be(HttpStatusCode.NoContent);
    }

    [Fact]
    public async Task Deleting_a_post_removes_it_from_view()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var post = await _client.CreateImagePostAsync(alice.AccessToken);

        (await _client.DeleteAuthAsync($"/api/v1/posts/{post.Id}", alice.AccessToken)).StatusCode
            .Should().Be(HttpStatusCode.NoContent);

        (await _client.GetAuthAsync($"/api/v1/posts/{post.Id}", alice.AccessToken)).StatusCode
            .Should().Be(HttpStatusCode.NotFound);
    }

    [Fact]
    public async Task Non_owner_cannot_delete_a_post()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        var post = await _client.CreateImagePostAsync(alice.AccessToken);

        (await _client.DeleteAuthAsync($"/api/v1/posts/{post.Id}", bob.AccessToken)).StatusCode
            .Should().Be(HttpStatusCode.Forbidden);
    }
}
