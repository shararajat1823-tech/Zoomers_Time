using System.Text;
using System.Threading.RateLimiting;
using Asp.Versioning;
using FluentValidation;
using FluentValidation.AspNetCore;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Diagnostics.HealthChecks;
using Microsoft.AspNetCore.HttpOverrides;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;
using Serilog;
using Zoomers.Api.Common;
using Zoomers.Application;
using Zoomers.Application.Identity.Validators;
using Zoomers.Infrastructure;
using Zoomers.Infrastructure.Security;

var builder = WebApplication.CreateBuilder(args);

// ---- Hosting platform wiring (Render/Neon/Railway/Fly/etc.) ----
// Managed hosts expose the database as a single URL-style DATABASE_URL and tell
// the process which port to listen on via PORT. Translate both into the shapes
// the app already understands, so the same image runs locally and in the cloud.
var databaseUrl = builder.Configuration["DATABASE_URL"];
if (DatabaseUrl.LooksLikeUrl(databaseUrl))
{
    builder.Configuration["ConnectionStrings:Default"] = DatabaseUrl.ToNpgsql(databaseUrl!);
}

var port = builder.Configuration["PORT"];
if (!string.IsNullOrWhiteSpace(port))
{
    builder.WebHost.UseUrls($"http://0.0.0.0:{port}");
}

// Behind a TLS-terminating proxy (the platform's edge), honour the forwarded
// scheme/host so redirects and generated URLs stay https.
builder.Services.Configure<ForwardedHeadersOptions>(options =>
{
    options.ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto;
    options.KnownNetworks.Clear();
    options.KnownProxies.Clear();
});

// ---- Logging (structured; secrets/message bodies never logged) ----
builder.Host.UseSerilog((context, logger) => logger
    .ReadFrom.Configuration(context.Configuration)
    .Enrich.FromLogContext());

// ---- Application + infrastructure ----
builder.Services.AddApplication();
builder.Services.AddInfrastructure(builder.Configuration);

// ---- Current-user accessor ----
builder.Services.AddHttpContextAccessor();
builder.Services.AddScoped<ICurrentUser, CurrentUser>();

// ---- MVC + validation ----
builder.Services.AddControllers();
builder.Services.AddFluentValidationAutoValidation();
builder.Services.AddValidatorsFromAssemblyContaining<RegisterRequestValidator>();

// ---- API versioning ----
builder.Services
    .AddApiVersioning(options =>
    {
        options.DefaultApiVersion = new ApiVersion(1, 0);
        options.AssumeDefaultVersionWhenUnspecified = true;
        options.ReportApiVersions = true;
    })
    .AddApiExplorer(options =>
    {
        options.GroupNameFormat = "'v'VVV";
        options.SubstituteApiVersionInUrl = true;
    });

// ---- Authentication (JWT bearer) ----
var jwtSection = builder.Configuration.GetSection(JwtOptions.SectionName);
var jwt = jwtSection.Get<JwtOptions>() ?? new JwtOptions();
builder.Services
    .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ValidIssuer = jwt.Issuer,
            ValidAudience = jwt.Audience,
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwt.SigningKey)),
            ClockSkew = TimeSpan.FromSeconds(30)
        };
    });
builder.Services.AddAuthorization();

// ---- Rate limiting (stricter on auth) ----
// Options come from DI (IOptionsMonitor) so limits are environment-configurable
// and overridable in tests via PostConfigure.
builder.Services.Configure<RateLimitingOptions>(builder.Configuration.GetSection(RateLimitingOptions.SectionName));
builder.Services.AddRateLimiter(options =>
{
    options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;

    static string PartitionKey(HttpContext ctx) => ctx.Connection.RemoteIpAddress?.ToString() ?? "unknown";
    static RateLimitingOptions Current(HttpContext ctx) =>
        ctx.RequestServices.GetRequiredService<IOptionsMonitor<RateLimitingOptions>>().CurrentValue;

    // Strict limiter applied only to auth endpoints (see [EnableRateLimiting("auth")]).
    options.AddPolicy("auth", context =>
    {
        var o = Current(context);
        return RateLimitPartition.GetFixedWindowLimiter(PartitionKey(context), _ => new FixedWindowRateLimiterOptions
        {
            PermitLimit = o.AuthPermitLimit,
            Window = TimeSpan.FromSeconds(o.AuthWindowSeconds),
            QueueLimit = 0
        });
    });

    // Global limiter for the rest of the API.
    options.GlobalLimiter = PartitionedRateLimiter.Create<HttpContext, string>(context =>
    {
        var o = Current(context);
        return RateLimitPartition.GetFixedWindowLimiter(PartitionKey(context), _ => new FixedWindowRateLimiterOptions
        {
            PermitLimit = o.GlobalPermitLimit,
            Window = TimeSpan.FromSeconds(o.GlobalWindowSeconds),
            QueueLimit = 0
        });
    });
});

// ---- CORS ----
// Native mobile clients don't enforce CORS, but a permissive policy lets the
// browser Swagger UI and any future web client call the API. Auth is via a
// bearer header (no cookies), so allowing any origin is safe here.
const string CorsPolicy = "zoomers-clients";
builder.Services.AddCors(options => options.AddPolicy(CorsPolicy, policy =>
    policy.AllowAnyOrigin().AllowAnyHeader().AllowAnyMethod()));

// ---- Health checks ----
// Liveness (/health/live) has no dependencies so the platform can tell the
// process is up; readiness (/health/ready) verifies the database is reachable.
builder.Services.AddHealthChecks()
    .AddDbContextCheck<Zoomers.Infrastructure.Persistence.ZoomersDbContext>("database", tags: new[] { "ready" });

// ---- Swagger / OpenAPI (with bearer auth) ----
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(options =>
{
    options.SwaggerDoc("v1", new OpenApiInfo { Title = "Zoomers API", Version = "v1" });
    var scheme = new OpenApiSecurityScheme
    {
        Name = "Authorization",
        Type = SecuritySchemeType.Http,
        Scheme = "bearer",
        BearerFormat = "JWT",
        In = ParameterLocation.Header,
        Reference = new OpenApiReference { Type = ReferenceType.SecurityScheme, Id = "Bearer" }
    };
    options.AddSecurityDefinition("Bearer", scheme);
    options.AddSecurityRequirement(new OpenApiSecurityRequirement { [scheme] = Array.Empty<string>() });
});

var app = builder.Build();

// ---- Apply migrations (and optionally seed) on startup when asked ----
// Off by default so tests and local runs manage their own schema; the hosting
// platform sets Database__AutoMigrate=true so a fresh managed database is built
// on first deploy.
if (app.Configuration.GetValue("Database:AutoMigrate", false))
{
    using var scope = app.Services.CreateScope();
    var db = scope.ServiceProvider.GetRequiredService<Zoomers.Infrastructure.Persistence.ZoomersDbContext>();
    await db.Database.MigrateAsync();
}

// ---- Pipeline ----
app.UseForwardedHeaders();
app.UseMiddleware<ExceptionHandlingMiddleware>();
app.UseSerilogRequestLogging();

// Swagger is served in every environment so the live API is self-documenting.
app.UseSwagger();
app.UseSwaggerUI();

// The platform edge terminates TLS; only redirect to HTTPS inside the container
// when explicitly asked (e.g. a self-hosted deployment doing its own TLS).
if (app.Configuration.GetValue("ForceHttps", false))
{
    app.UseHttpsRedirection();
    app.UseHsts();
}

app.UseCors(CorsPolicy);
app.UseRateLimiter();
app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();
app.MapHealthChecks("/health/live", new HealthCheckOptions { Predicate = _ => false });
app.MapHealthChecks("/health/ready", new HealthCheckOptions { Predicate = check => check.Tags.Contains("ready") });

app.Run();

// Exposed so integration tests can use WebApplicationFactory<Program>.
public partial class Program { }
