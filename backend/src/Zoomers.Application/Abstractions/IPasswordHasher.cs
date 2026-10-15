namespace Zoomers.Application.Abstractions;

public enum PasswordVerificationResult { Failed, Success, SuccessRehashNeeded }

/// <summary>
/// Password hashing/verification. Implemented in Infrastructure with the
/// established ASP.NET Core Identity PBKDF2 hasher — no custom cryptography.
/// </summary>
public interface IPasswordHasher
{
    string Hash(string password);
    PasswordVerificationResult Verify(string hash, string password);
}
