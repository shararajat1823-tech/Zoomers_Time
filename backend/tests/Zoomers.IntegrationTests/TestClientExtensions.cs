using System.Net.Http.Headers;
using System.Net.Http.Json;
using Zoomers.Application.Identity.Dtos;

namespace Zoomers.IntegrationTests;

/// <summary>Helpers to register users and make authenticated requests in tests.</summary>
public static class TestClientExtensions
{
    public static async Task<AuthResponse> RegisterUserAsync(this HttpClient client, string username)
    {
        var res = await client.PostAsJsonAsync("/api/v1/auth/register",
            new RegisterRequest(username, $"{username}@example.com", "Sup3rSecret!", $"{username} Display"));
        res.EnsureSuccessStatusCode();
        return (await res.Content.ReadFromJsonAsync<AuthResponse>())!;
    }

    public static HttpRequestMessage WithAuth(this HttpRequestMessage req, string accessToken)
    {
        req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", accessToken);
        return req;
    }

    public static Task<HttpResponseMessage> PostAuthAsync(this HttpClient client, string url, string token)
    {
        var req = new HttpRequestMessage(HttpMethod.Post, url).WithAuth(token);
        return client.SendAsync(req);
    }

    public static Task<HttpResponseMessage> PostAuthAsync<T>(this HttpClient client, string url, string token, T body)
    {
        var req = new HttpRequestMessage(HttpMethod.Post, url).WithAuth(token);
        req.Content = JsonContent.Create(body);
        return client.SendAsync(req);
    }

    public static Task<HttpResponseMessage> DeleteAuthAsync(this HttpClient client, string url, string token)
    {
        var req = new HttpRequestMessage(HttpMethod.Delete, url).WithAuth(token);
        return client.SendAsync(req);
    }

    public static Task<HttpResponseMessage> GetAuthAsync(this HttpClient client, string url, string token)
    {
        var req = new HttpRequestMessage(HttpMethod.Get, url).WithAuth(token);
        return client.SendAsync(req);
    }

    public static async Task<HttpResponseMessage> PatchAuthAsync<T>(this HttpClient client, string url, string token, T body)
    {
        var req = new HttpRequestMessage(HttpMethod.Patch, url).WithAuth(token);
        req.Content = JsonContent.Create(body);
        return await client.SendAsync(req);
    }
}
