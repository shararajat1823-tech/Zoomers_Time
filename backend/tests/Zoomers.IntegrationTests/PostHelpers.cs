using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using FluentAssertions;
using Zoomers.Application.Media.Dtos;
using Zoomers.Application.Posts.Dtos;
using Zoomers.Domain.Posts;

namespace Zoomers.IntegrationTests;

public static class PostHelpers
{
    /// <summary>Runs the full upload-session flow and returns a completed session id.</summary>
    public static async Task<Guid> UploadImageAsync(this HttpClient client, string token, string contentType = "image/jpeg")
    {
        var create = await AuthJsonAsync(client, HttpMethod.Post, "/api/v1/media/upload-sessions", token,
            new CreateUploadSessionRequest("PostMedia", contentType));
        create.StatusCode.Should().Be(HttpStatusCode.Created);
        var session = (await create.Content.ReadFromJsonAsync<UploadSessionResponse>())!;

        // Direct upload of some bytes to the dev storage endpoint.
        var put = new HttpRequestMessage(HttpMethod.Put, session.UploadUrl);
        put.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        var bytes = new byte[1024];
        Random.Shared.NextBytes(bytes);
        put.Content = new ByteArrayContent(bytes);
        put.Content.Headers.ContentType = new MediaTypeHeaderValue(contentType);
        (await client.SendAsync(put)).StatusCode.Should().Be(HttpStatusCode.NoContent);

        var complete = await client.PostAuthAsync($"/api/v1/media/upload-sessions/{session.SessionId}/complete", token);
        complete.StatusCode.Should().Be(HttpStatusCode.OK);

        return session.SessionId;
    }

    /// <summary>Creates a post with a single freshly-uploaded image.</summary>
    public static async Task<PostView> CreateImagePostAsync(this HttpClient client, string token,
        PostVisibility visibility = PostVisibility.Public, string? caption = "hello")
    {
        var sessionId = await client.UploadImageAsync(token);
        var req = new CreatePostRequest(caption, visibility, null, null,
            new[] { new CreatePostMediaItem(sessionId, 800, 600, null) });

        var res = await AuthJsonAsync(client, HttpMethod.Post, "/api/v1/posts", token, req);
        res.StatusCode.Should().Be(HttpStatusCode.Created);
        return (await res.Content.ReadFromJsonAsync<PostView>())!;
    }

    public static async Task<HttpResponseMessage> AuthJsonAsync<T>(HttpClient client, HttpMethod method, string url, string token, T body)
    {
        var req = new HttpRequestMessage(method, url) { Content = JsonContent.Create(body) };
        req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        return await client.SendAsync(req);
    }
}
