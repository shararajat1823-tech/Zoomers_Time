using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.DependencyInjection;

namespace Zoomers.Api.Common;

/// <summary>Base controller exposing the authenticated user id from the validated JWT.</summary>
[ApiController]
public abstract class ApiControllerBase : ControllerBase
{
    /// <summary>The current user's id, or null if unauthenticated.</summary>
    protected Guid? CurrentUserId =>
        HttpContext.RequestServices.GetRequiredService<ICurrentUser>().UserId;
}
