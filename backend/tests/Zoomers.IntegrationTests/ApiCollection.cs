using Xunit;

namespace Zoomers.IntegrationTests;

/// <summary>Shared fixture: builds the app + migrates the DB once for the whole collection.</summary>
public sealed class ApiFixture : IAsyncLifetime
{
    public ZoomersApiFactory Factory { get; } = new();

    public async Task InitializeAsync() => await Factory.InitializeDatabaseAsync();

    public async Task DisposeAsync() => await Factory.DisposeAsync();
}

[CollectionDefinition("api")]
public sealed class ApiCollection : ICollectionFixture<ApiFixture> { }
