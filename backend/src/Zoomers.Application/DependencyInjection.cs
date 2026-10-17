using FluentValidation;
using Microsoft.Extensions.DependencyInjection;
using Zoomers.Application.Engagement;
using Zoomers.Application.Identity;
using Zoomers.Application.Media;
using Zoomers.Application.Posts;
using Zoomers.Application.Profiles;
using Zoomers.Application.Search;
using Zoomers.Application.Settings;
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
        services.AddScoped<IMediaService, MediaService>();
        services.AddScoped<PostService>();
        services.AddScoped<IPostService>(sp => sp.GetRequiredService<PostService>());
        services.AddScoped<IFeedService, FeedService>();
        services.AddScoped<IEngagementService, EngagementService>();
        services.AddScoped<ISearchService, SearchService>();
        services.AddScoped<ISettingsService, SettingsService>();
        services.AddScoped<Notifications.INotificationService, Notifications.NotificationService>();
        services.AddScoped<Stories.IStoryService, Stories.StoryService>();
        services.AddScoped<Messaging.IMessagingService, Messaging.MessagingService>();
        services.AddValidatorsFromAssemblyContaining<AuthService>(ServiceLifetime.Scoped);
        return services;
    }
}
