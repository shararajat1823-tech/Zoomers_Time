using Zoomers.Application.Settings.Dtos;
using Zoomers.Application.Social.Dtos;
using Zoomers.Shared.Pagination;
using Zoomers.Shared.Results;

namespace Zoomers.Application.Settings;

public interface ISettingsService
{
    Task<Result<SettingsView>> GetAsync(Guid userId, CancellationToken ct = default);
    Task<Result<SettingsView>> UpdateAsync(Guid userId, UpdateSettingsRequest request, CancellationToken ct = default);

    Task<Result<CursorPage<UserListItem>>> GetBlockedAsync(Guid userId, string? cursor, int limit, CancellationToken ct = default);
    Task<Result<CursorPage<UserListItem>>> GetMutedAsync(Guid userId, string? cursor, int limit, CancellationToken ct = default);
}
