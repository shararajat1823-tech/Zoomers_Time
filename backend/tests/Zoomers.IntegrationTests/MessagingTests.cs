using System.Net;
using System.Net.Http.Json;
using FluentAssertions;
using Zoomers.Application.Messaging.Dtos;
using Zoomers.Shared.Pagination;
using Xunit;

namespace Zoomers.IntegrationTests;

[Collection("api")]
public sealed class MessagingTests : IAsyncLifetime
{
    private readonly ApiFixture _fixture;
    private readonly HttpClient _client;

    public MessagingTests(ApiFixture fixture)
    {
        _fixture = fixture;
        _client = fixture.Factory.CreateClient();
    }

    public async Task InitializeAsync() => await _fixture.Factory.ResetAsync();
    public Task DisposeAsync() => Task.CompletedTask;

    private async Task<ConversationDto> StartAsync(string token, Guid recipientId)
    {
        var res = await _client.PostAuthAsync("/api/v1/conversations", token, new StartConversationRequest(recipientId));
        res.StatusCode.Should().Be(HttpStatusCode.OK);
        return (await res.Content.ReadFromJsonAsync<ConversationDto>())!;
    }

    [Fact]
    public async Task Start_creates_conversation_and_is_idempotent()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");

        var first = await StartAsync(alice.AccessToken, bob.User.Id);
        first.OtherUser.Id.Should().Be(bob.User.Id);
        first.OtherUser.Username.Should().Be("bob");
        first.UnreadCount.Should().Be(0);

        // Same pair, started from either side, maps to one conversation.
        var again = await StartAsync(alice.AccessToken, bob.User.Id);
        again.Id.Should().Be(first.Id);

        var fromBob = await StartAsync(bob.AccessToken, alice.User.Id);
        fromBob.Id.Should().Be(first.Id);
    }

    [Fact]
    public async Task Send_and_list_messages_roundtrip()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        var conv = await StartAsync(alice.AccessToken, bob.User.Id);

        var send = await _client.PostAuthAsync($"/api/v1/conversations/{conv.Id}/messages", alice.AccessToken, new SendMessageRequest("hey bob"));
        send.StatusCode.Should().Be(HttpStatusCode.OK);
        var sent = (await send.Content.ReadFromJsonAsync<MessageDto>())!;
        sent.Body.Should().Be("hey bob");
        sent.IsMine.Should().BeTrue();

        // Bob sees the conversation in his inbox with an unread message + preview.
        var inboxRes = await _client.GetAuthAsync("/api/v1/conversations", bob.AccessToken);
        var inbox = (await inboxRes.Content.ReadFromJsonAsync<CursorPage<ConversationDto>>())!;
        inbox.Items.Should().ContainSingle();
        inbox.Items[0].UnreadCount.Should().Be(1);
        inbox.Items[0].LastMessagePreview.Should().Be("hey bob");
        inbox.Items[0].OtherUser.Username.Should().Be("alice");

        // Bob reads the thread; the message is not "his".
        var msgsRes = await _client.GetAuthAsync($"/api/v1/conversations/{conv.Id}/messages", bob.AccessToken);
        var msgs = (await msgsRes.Content.ReadFromJsonAsync<CursorPage<MessageDto>>())!;
        msgs.Items.Should().ContainSingle();
        msgs.Items[0].Body.Should().Be("hey bob");
        msgs.Items[0].IsMine.Should().BeFalse();
    }

    [Fact]
    public async Task Unread_count_and_mark_read()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        var conv = await StartAsync(alice.AccessToken, bob.User.Id);
        await _client.PostAuthAsync($"/api/v1/conversations/{conv.Id}/messages", alice.AccessToken, new SendMessageRequest("one"));
        await _client.PostAuthAsync($"/api/v1/conversations/{conv.Id}/messages", alice.AccessToken, new SendMessageRequest("two"));

        var before = (await (await _client.GetAuthAsync("/api/v1/conversations/unread-count", bob.AccessToken))
            .Content.ReadFromJsonAsync<UnreadCountDto>())!;
        before.Count.Should().Be(2);

        var read = await _client.PostAuthAsync($"/api/v1/conversations/{conv.Id}/read", bob.AccessToken);
        read.StatusCode.Should().Be(HttpStatusCode.NoContent);

        var after = (await (await _client.GetAuthAsync("/api/v1/conversations/unread-count", bob.AccessToken))
            .Content.ReadFromJsonAsync<UnreadCountDto>())!;
        after.Count.Should().Be(0);

        // The sender does not accumulate unread for their own messages.
        var aliceUnread = (await (await _client.GetAuthAsync("/api/v1/conversations/unread-count", alice.AccessToken))
            .Content.ReadFromJsonAsync<UnreadCountDto>())!;
        aliceUnread.Count.Should().Be(0);
    }

    [Fact]
    public async Task Non_participant_cannot_access_conversation()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        var mallory = await _client.RegisterUserAsync("mallory");
        var conv = await StartAsync(alice.AccessToken, bob.User.Id);

        var get = await _client.GetAuthAsync($"/api/v1/conversations/{conv.Id}/messages", mallory.AccessToken);
        get.StatusCode.Should().Be(HttpStatusCode.NotFound);

        var send = await _client.PostAuthAsync($"/api/v1/conversations/{conv.Id}/messages", mallory.AccessToken, new SendMessageRequest("sneaky"));
        send.StatusCode.Should().Be(HttpStatusCode.NotFound);

        var read = await _client.PostAuthAsync($"/api/v1/conversations/{conv.Id}/read", mallory.AccessToken);
        read.StatusCode.Should().Be(HttpStatusCode.NotFound);
    }

    [Fact]
    public async Task Cannot_message_self()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var res = await _client.PostAuthAsync("/api/v1/conversations", alice.AccessToken, new StartConversationRequest(alice.User.Id));
        res.StatusCode.Should().Be(HttpStatusCode.BadRequest);
    }

    [Fact]
    public async Task Blocked_users_cannot_start_a_conversation()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        await _client.PostAuthAsync($"/api/v1/users/{alice.User.Id}/block", bob.AccessToken);

        var res = await _client.PostAuthAsync("/api/v1/conversations", alice.AccessToken, new StartConversationRequest(bob.User.Id));
        res.StatusCode.Should().Be(HttpStatusCode.Forbidden);
    }

    [Fact]
    public async Task Empty_message_is_rejected()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        var conv = await StartAsync(alice.AccessToken, bob.User.Id);

        var res = await _client.PostAuthAsync($"/api/v1/conversations/{conv.Id}/messages", alice.AccessToken, new SendMessageRequest("   "));
        res.StatusCode.Should().Be(HttpStatusCode.BadRequest);
    }

    [Fact]
    public async Task Messages_page_with_cursor_newest_first()
    {
        var alice = await _client.RegisterUserAsync("alice");
        var bob = await _client.RegisterUserAsync("bob");
        var conv = await StartAsync(alice.AccessToken, bob.User.Id);
        foreach (var body in new[] { "m1", "m2", "m3" })
            await _client.PostAuthAsync($"/api/v1/conversations/{conv.Id}/messages", alice.AccessToken, new SendMessageRequest(body));

        var firstRes = await _client.GetAuthAsync($"/api/v1/conversations/{conv.Id}/messages?limit=2", alice.AccessToken);
        var page1 = (await firstRes.Content.ReadFromJsonAsync<CursorPage<MessageDto>>())!;
        page1.Items.Should().HaveCount(2);
        page1.HasMore.Should().BeTrue();
        page1.Items[0].Body.Should().Be("m3"); // newest first

        var secondRes = await _client.GetAuthAsync(
            $"/api/v1/conversations/{conv.Id}/messages?limit=2&cursor={Uri.EscapeDataString(page1.NextCursor!)}",
            alice.AccessToken);
        var page2 = (await secondRes.Content.ReadFromJsonAsync<CursorPage<MessageDto>>())!;
        page2.Items.Should().ContainSingle();
        page2.Items[0].Body.Should().Be("m1");
        page2.HasMore.Should().BeFalse();
    }
}
