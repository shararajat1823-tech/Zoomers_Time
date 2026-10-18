using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using FluentAssertions;
using Zoomers.Application.Media.Dtos;
using Zoomers.Application.Profiles.Dtos;
using Zoomers.Application.Reels.Dtos;
using Zoomers.Shared.Pagination;
using Xunit;

namespace Zoomers.IntegrationTests;

[Collection("api")]
public sealed class ReelsTests : IAsyncLifetime
{
    private readonly ApiFixture _fixture;
    private readonly HttpClient _client;

    public ReelsTests(ApiFixture fixture)
    {
        _fixture = fixture;
        _client = fixture.Factory.CreateClient();
    }

    public async Task InitializeAsync() => await _fixture.Factory.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    /// <summary>Runs the upload-session flow for a short video and returns the completed session id.</summary>
    private async Task<Guid> UploadVideoAsync(string token)
    {
        var create = await PostHelpers.AuthJsonAsync(_client, HttpMethod.Post, "/api/v1/media/upload-sessions", token,
            new CreateUploadSessionRequest("ReelVideo", "video/mp4"));
        create.StatusCode.Should().Be(HttpStatusCode.Created);
        var session = (await create.Content.ReadFromJsonAsync<UploadSessionResponse>())!;

        var put = new HttpRequestMessage(HttpMethod.Put, session.UploadUrl);
        put.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        var bytes = new byte[4096];
        Random.Shared.NextBytes(bytes);
        put.Content = new ByteArrayContent(bytes);
        put.Content.Headers.ContentType = new MediaTypeHeaderValue("video/mp4");
        (await _client.SendAsync(put)).StatusCode.Should().Be(HttpStatusCode.NoContent);

        (await _client.PostAuthAsync($"/api/v1/media/upload-sessions/{session.SessionId}/complete", token))
            .StatusCode.Should().Be(HttpStatusCode.OK);
        return session.SessionId;
    }

    private async Task<ReelView> CreateReelAsync(string token, string? caption = "my reel")
    {
        var sessionId = await UploadVideoAsync(token);
        var res = await PostHelpers.AuthJsonAsync(_client, HttpMethod.Post, "/api/v1/reels", token,
            new CreateReelRequest(sessionId, null, caption, 1080, 1920, 8000));
        res.StatusCode.Should().Be(HttpStatusCode.Created);
        return (await res.Content.ReadFromJsonAsync<ReelView>())!;
    }

    private async Task<CursorPage<ReelView>> FeedAsync(string token)
    {
        var res = await _client.GetAuthAsync("/api/v1/reels/feed", token);
        res.StatusCode.Should().Be(HttpStatusCode.OK);
        return (await res.Content.ReadFromJsonAsync<CursorPage<ReelView>>())!;
    }

    [Fact]
    public async Task Create_reel_from_video_upload()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var reel = await CreateReelAsync(alice.AccessToken, "hello reel");
        reel.Author.Username.Should().Be("alice");
        reel.Caption.Should().Be("hello reel");
        reel.MediaUrl.Should().NotBeNullOrEmpty();
        reel.DurationMs.Should().Be(8000);
    }

    [Fact]
    public async Task Feed_shows_public_reels()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        await CreateReelAsync(bob.AccessToken, "bob reel");

        var feed = await FeedAsync(alice.AccessToken);
        feed.Items.Select(r => r.Author.Username).Should().Contain("bob");
    }

    [Fact]
    public async Task Like_and_unlike_updates_count_and_state()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        var reel = await CreateReelAsync(bob.AccessToken);

        var like = await _client.PostAuthAsync($"/api/v1/reels/{reel.Id}/likes", alice.AccessToken);
        var liked = (await like.Content.ReadFromJsonAsync<ReelLikeState>())!;
        liked.Liked.Should().BeTrue();
        liked.LikeCount.Should().Be(1);

        // Idempotent.
        await _client.PostAuthAsync($"/api/v1/reels/{reel.Id}/likes", alice.AccessToken);

        var unlike = await _client.DeleteAuthAsync($"/api/v1/reels/{reel.Id}/likes", alice.AccessToken);
        var unliked = (await unlike.Content.ReadFromJsonAsync<ReelLikeState>())!;
        unliked.Liked.Should().BeFalse();
        unliked.LikeCount.Should().Be(0);
    }

    [Fact]
    public async Task Only_owner_can_delete_a_reel()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        var reel = await CreateReelAsync(bob.AccessToken);

        var byOther = await _client.DeleteAuthAsync($"/api/v1/reels/{reel.Id}", alice.AccessToken);
        byOther.StatusCode.Should().Be(HttpStatusCode.Forbidden);

        var byOwner = await _client.DeleteAuthAsync($"/api/v1/reels/{reel.Id}", bob.AccessToken);
        byOwner.StatusCode.Should().Be(HttpStatusCode.NoContent);

        var feed = await FeedAsync(alice.AccessToken);
        feed.Items.Should().NotContain(r => r.Id == reel.Id);
    }

    [Fact]
    public async Task Private_authors_reels_are_hidden_from_others_feed()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var dave = await _client.RegisterUserAsync("dave");
        (await _client.PatchAuthAsync("/api/v1/users/me", dave.AccessToken,
            new UpdateProfileRequest(null, null, null, true))).StatusCode.Should().Be(HttpStatusCode.OK);
        await CreateReelAsync(dave.AccessToken, "private reel");

        var feed = await FeedAsync(alice.AccessToken);
        feed.Items.Select(r => r.Author.Username).Should().NotContain("dave");
    }
}
