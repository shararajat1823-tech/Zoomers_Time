using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Zoomers.Application.Abstractions;
using Zoomers.Infrastructure.Media;
using Zoomers.Infrastructure.Messaging;
using Zoomers.Infrastructure.Persistence;
using Zoomers.Infrastructure.Security;
using Zoomers.Infrastructure.Time;

namespace Zoomers.Infrastructure;

public static class DependencyInjection
{
    /// <summary>
    /// Registers persistence and the external-service adapters. The adapters are
    /// the local/in-memory implementations; swapping to cloud providers is a
    /// configuration + registration change here, with no caller changes.
    /// </summary>
    public static IServiceCollection AddInfrastructure(this IServiceCollection services, IConfiguration configuration)
    {
        var connectionString = configuration.GetConnectionString("Default")
            ?? throw new InvalidOperationException("Missing connection string 'ConnectionStrings:Default'.");

        services.AddDbContext<ZoomersDbContext>(options =>
            options.UseNpgsql(connectionString, npg => npg.MigrationsAssembly(typeof(ZoomersDbContext).Assembly.FullName)));

        services.AddScoped<IApplicationDbContext>(sp => sp.GetRequiredService<ZoomersDbContext>());

        // Options bound from configuration; secrets come from env/secret manager.
        services.AddOptions<JwtOptions>()
            .Bind(configuration.GetSection(JwtOptions.SectionName))
            .ValidateDataAnnotations()
            .ValidateOnStart();

        services.Configure<LocalMediaOptions>(configuration.GetSection(LocalMediaOptions.SectionName));

        // Core services.
        services.AddSingleton<IClock, SystemClock>();
        services.AddSingleton<IPasswordHasher, PasswordHasherAdapter>();
        services.AddScoped<ITokenService, JwtTokenService>();

        // External-service adapters (local/in-memory first).
        services.AddScoped<IMediaStorage, LocalFileMediaStorage>();
        services.AddScoped<IEmailSender, LoggingEmailSender>();
        services.AddScoped<IPushSender, LoggingPushSender>();

        return services;
    }
}
