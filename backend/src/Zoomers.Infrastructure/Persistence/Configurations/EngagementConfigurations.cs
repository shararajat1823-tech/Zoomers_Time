using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Zoomers.Domain.Engagement;

namespace Zoomers.Infrastructure.Persistence.Configurations;

public sealed class PostLikeConfiguration : IEntityTypeConfiguration<PostLike>
{
    public void Configure(EntityTypeBuilder<PostLike> b)
    {
        b.ToTable("PostLikes");
        b.HasKey(l => new { l.PostId, l.UserId }); // idempotent likes

        b.HasIndex(l => l.UserId).HasDatabaseName("IX_PostLikes_UserId");

        b.HasOne(l => l.Post).WithMany().HasForeignKey(l => l.PostId).OnDelete(DeleteBehavior.Cascade);
        b.HasOne(l => l.User).WithMany().HasForeignKey(l => l.UserId).OnDelete(DeleteBehavior.Cascade);

        b.HasQueryFilter(l => l.Post!.DeletedAt == null && l.User!.DeletedAt == null);
    }
}

public sealed class SavedPostConfiguration : IEntityTypeConfiguration<SavedPost>
{
    public void Configure(EntityTypeBuilder<SavedPost> b)
    {
        b.ToTable("SavedPosts");
        b.HasKey(s => new { s.UserId, s.PostId }); // idempotent saves

        b.HasIndex(s => new { s.UserId, s.CreatedAt }).HasDatabaseName("IX_SavedPosts_User_CreatedAt");

        b.HasOne(s => s.Post).WithMany().HasForeignKey(s => s.PostId).OnDelete(DeleteBehavior.Cascade);
        b.HasOne(s => s.User).WithMany().HasForeignKey(s => s.UserId).OnDelete(DeleteBehavior.Cascade);

        b.HasQueryFilter(s => s.Post!.DeletedAt == null && s.User!.DeletedAt == null);
    }
}

public sealed class CommentConfiguration : IEntityTypeConfiguration<Comment>
{
    public void Configure(EntityTypeBuilder<Comment> b)
    {
        b.ToTable("Comments");
        b.HasKey(c => c.Id);

        b.Property(c => c.Body).HasMaxLength(2200).IsRequired();

        b.HasIndex(c => new { c.PostId, c.CreatedAt }).HasDatabaseName("IX_Comments_Post_CreatedAt");
        b.HasIndex(c => new { c.ParentCommentId, c.CreatedAt }).HasDatabaseName("IX_Comments_Parent_CreatedAt");

        b.HasQueryFilter(c => c.DeletedAt == null && c.Post!.DeletedAt == null && c.Author!.DeletedAt == null);

        b.HasOne(c => c.Post).WithMany().HasForeignKey(c => c.PostId).OnDelete(DeleteBehavior.Cascade);
        b.HasOne(c => c.Author).WithMany().HasForeignKey(c => c.AuthorId).OnDelete(DeleteBehavior.Restrict);
        b.HasOne(c => c.ParentComment).WithMany().HasForeignKey(c => c.ParentCommentId).OnDelete(DeleteBehavior.Cascade);
    }
}

public sealed class CommentLikeConfiguration : IEntityTypeConfiguration<CommentLike>
{
    public void Configure(EntityTypeBuilder<CommentLike> b)
    {
        b.ToTable("CommentLikes");
        b.HasKey(l => new { l.CommentId, l.UserId });

        b.HasOne(l => l.Comment).WithMany().HasForeignKey(l => l.CommentId).OnDelete(DeleteBehavior.Cascade);
        b.HasOne(l => l.User).WithMany().HasForeignKey(l => l.UserId).OnDelete(DeleteBehavior.Cascade);

        b.HasQueryFilter(l => l.Comment!.DeletedAt == null && l.User!.DeletedAt == null);
    }
}
