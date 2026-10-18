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
        services.Configure<S3StorageOptions>(configuration.GetSection(S3StorageOptions.SectionName));
        services.Configure<FcmOptions>(configuration.GetSection(FcmOptions.SectionName));

        // Core services.
        services.AddSingleton<IClock, SystemClock>();
        services.AddSingleton<IPasswordHasher, PasswordHasherAdapter>();
        services.AddScoped<ITokenService, JwtTokenService>();

        // Media storage: S3-compatible (e.g. Cloudflare R2) when configured,
        // otherwise local disk for development. Same IMediaStorage contract, so
        // no caller changes either way.
        var s3 = configuration.GetSection(S3StorageOptions.SectionName).Get<S3StorageOptions>() ?? new S3StorageOptions();
        if (s3.Enabled)
        {
            services.AddSingleton<Amazon.S3.IAmazonS3>(_ =>
            {
                var config = new Amazon.S3.AmazonS3Config
                {
                    ServiceURL = s3.ServiceUrl,
                    ForcePathStyle = s3.ForcePathStyle,
                    AuthenticationRegion = s3.Region,
                };
                return new Amazon.S3.AmazonS3Client(s3.AccessKey, s3.SecretKey, config);
            });
            services.AddScoped<IMediaStorage, S3MediaStorage>();
        }
        else
        {
            services.AddScoped<IMediaStorage, LocalFileMediaStorage>();
        }

        // External-service adapters (local/in-memory first).
        services.AddScoped<IEmailSender, LoggingEmailSender>();

        // Push: FCM when a service-account key is configured, otherwise a logging
        // no-op so the backend runs fine without Firebase. Same IPushSender contract.
        var fcm = configuration.GetSection(FcmOptions.SectionName).Get<FcmOptions>() ?? new FcmOptions();
        if (fcm.Enabled)
            services.AddScoped<IPushSender, FcmPushSender>();
        else
            services.AddScoped<IPushSender, LoggingPushSender>();

        return services;
    }
}
