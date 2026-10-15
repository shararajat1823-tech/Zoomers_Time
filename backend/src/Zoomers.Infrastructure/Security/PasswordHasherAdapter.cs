using Microsoft.AspNetCore.Identity;
using Zoomers.Application.Abstractions;
using AppResult = Zoomers.Application.Abstractions.PasswordVerificationResult;
using IdentityResult = Microsoft.AspNetCore.Identity.PasswordVerificationResult;

namespace Zoomers.Infrastructure.Security;

/// <summary>
/// Wraps ASP.NET Core Identity's PBKDF2 <see cref="PasswordHasher{T}"/> — an
/// established implementation — behind the application's <see cref="IPasswordHasher"/>.
/// No custom cryptography.
/// </summary>
public sealed class PasswordHasherAdapter : IPasswordHasher
{
    private static readonly object Dummy = new();
    private readonly PasswordHasher<object> _inner = new();

    public string Hash(string password) => _inner.HashPassword(Dummy, password);

    public AppResult Verify(string hash, string password)
    {
        var result = _inner.VerifyHashedPassword(Dummy, hash, password);
        return result switch
        {
            IdentityResult.Success => AppResult.Success,
            IdentityResult.SuccessRehashNeeded => AppResult.SuccessRehashNeeded,
            _ => AppResult.Failed
        };
    }
}
