using FluentValidation;
using Microsoft.Extensions.DependencyInjection;
using Zoomers.Application.Identity;
using Zoomers.Application.Profiles;
using Zoomers.Application.Social;

namespace Zoomers.Application;

public static class DependencyInjection
{
    /// <summary>Registers application services and validators.</summary>
    public static IServiceCollection AddApplication(this IServiceCollection services)
    {
        services.AddScoped<IAuthService, AuthService>();
        services.AddScoped<IProfileService, ProfileService>();
        services.AddScoped<ISocialGraphService, SocialGraphService>();
        services.AddValidatorsFromAssemblyContaining<AuthService>(ServiceLifetime.Scoped);
        return services;
    }
}
