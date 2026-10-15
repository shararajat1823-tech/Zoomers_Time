using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Zoomers.Api.Common;
using Zoomers.Infrastructure.Persistence;

namespace Zoomers.IntegrationTests;

/// <summary>
/// Boots the real API against a real PostgreSQL database for integration tests.
/// The connection string and signing key come from environment variables so no
/// secrets live in the test code; a developer/CI sets:
///   ConnectionStrings__Default, Jwt__SigningKey
/// (defaults below point at the local 'zoomers_test' database). These are set
/// as process environment variables (shared by all test instances) so they are
/// present when the app reads configuration during service registration.
/// Per-instance knobs (rate limits) are overridden through DI instead.
/// </summary>
public sealed class ZoomersApiFactory : WebApplicationFactory<Program>
{
    static ZoomersApiFactory()
    {
        SetIfMissing("ConnectionStrings__Default",
            "Host=localhost;Port=5432;Database=zoomers_test;Username=zoomers;Password=devpassword");
        SetIfMissing("Jwt__SigningKey", "integration-test-signing-key-at-least-32-bytes-long!!");
        SetIfMissing("Jwt__Issuer", "zoomers");
        SetIfMissing("Jwt__Audience", "zoomers.clients");

        static void SetIfMissing(string key, string value)
        {
            if (string.IsNullOrEmpty(Environment.GetEnvironmentVariable(key)))
                Environment.SetEnvironmentVariable(key, value);
        }
    }

    /// <summary>Auth-endpoint permit limit; overridable so a focused test can set a low value.</summary>
    public int AuthPermitLimit { get; init; } = 100_000;

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseEnvironment("Production"); // exercise production error-handling behavior

        builder.ConfigureServices(services =>
        {
            // Per-instance rate-limit overrides via DI (reliable regardless of config timing).
            services.PostConfigure<RateLimitingOptions>(o =>
            {
                o.AuthPermitLimit = AuthPermitLimit;
                o.AuthWindowSeconds = 60;
                o.GlobalPermitLimit = 100_000;
                o.GlobalWindowSeconds = 60;
            });
        });
    }

    /// <summary>Ensures the schema exists (migrations applied) before tests run.</summary>
    public async Task InitializeDatabaseAsync()
    {
        using var scope = Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<ZoomersDbContext>();
        await db.Database.MigrateAsync();
    }

    /// <summary>Removes all rows so each test class starts from a clean slate.</summary>
    public async Task ResetAsync()
    {
        using var scope = Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<ZoomersDbContext>();
        await db.Database.ExecuteSqlRawAsync(
            "TRUNCATE \"RefreshTokens\", \"PasswordResetTokens\", \"UserSettings\", \"UserProfiles\", \"Users\" RESTART IDENTITY CASCADE;");
    }
}
