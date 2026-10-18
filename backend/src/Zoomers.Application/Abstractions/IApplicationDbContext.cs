using Microsoft.EntityFrameworkCore;
using Zoomers.Domain.Engagement;
using Zoomers.Domain.Identity;
using Zoomers.Domain.Media;
using Zoomers.Domain.Messaging;
using Zoomers.Domain.Notifications;
using Zoomers.Domain.Push;
using Zoomers.Domain.Reels;
using Zoomers.Domain.Stories;
using Zoomers.Domain.Posts;
using Zoomers.Domain.Profiles;
using Zoomers.Domain.Social;
using Zoomers.Domain.Tags;

namespace Zoomers.Application.Abstractions;

/// <summary>
/// The persistence surface the application layer depends on. Implemented by the
/// EF Core context in Infrastructure. Keeping this interface here lets services
/// and tests depend on an abstraction rather than a concrete DbContext.
/// </summary>
public interface IApplicationDbContext
{
    DbSet<User> Users { get; }
    DbSet<UserProfile> UserProfiles { get; }
    DbSet<UserSettings> UserSettings { get; }
    DbSet<RefreshToken> RefreshTokens { get; }
    DbSet<PasswordResetToken> PasswordResetTokens { get; }

    DbSet<Follow> Follows { get; }
    DbSet<FollowRequest> FollowRequests { get; }
    DbSet<UserBlock> UserBlocks { get; }

    DbSet<Post> Posts { get; }
    DbSet<PostMedia> PostMedia { get; }
    DbSet<UploadSession> UploadSessions { get; }
    DbSet<PostLike> PostLikes { get; }
    DbSet<SavedPost> SavedPosts { get; }
    DbSet<Comment> Comments { get; }
    DbSet<CommentLike> CommentLikes { get; }

    DbSet<Hashtag> Hashtags { get; }
    DbSet<PostHashtag> PostHashtags { get; }
    DbSet<Mention> Mentions { get; }
    DbSet<UserMute> UserMutes { get; }

    DbSet<Notification> Notifications { get; }

    DbSet<Story> Stories { get; }
    DbSet<StoryView> StoryViews { get; }

    DbSet<Conversation> Conversations { get; }
    DbSet<Message> Messages { get; }

    DbSet<DeviceToken> DeviceTokens { get; }

    DbSet<Reel> Reels { get; }
    DbSet<ReelLike> ReelLikes { get; }

    Task<int> SaveChangesAsync(CancellationToken cancellationToken = default);

    /// <summary>Executes work inside a database transaction (all-or-nothing).</summary>
    Task ExecuteInTransactionAsync(Func<CancellationToken, Task> action, CancellationToken cancellationToken = default);
}
