# syntax=docker/dockerfile:1

# ---- Build stage ----
# Restores and publishes the API as a self-contained framework-dependent build.
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src

# Copy solution + project files first so dependency restore is cached across
# source-only changes.
COPY Zoomers.sln ./
COPY src/Zoomers.Domain/Zoomers.Domain.csproj src/Zoomers.Domain/
COPY src/Zoomers.Application/Zoomers.Application.csproj src/Zoomers.Application/
COPY src/Zoomers.Infrastructure/Zoomers.Infrastructure.csproj src/Zoomers.Infrastructure/
COPY src/Zoomers.Api/Zoomers.Api.csproj src/Zoomers.Api/
COPY src/Zoomers.Shared/Zoomers.Shared.csproj src/Zoomers.Shared/
RUN dotnet restore src/Zoomers.Api/Zoomers.Api.csproj

# Copy the rest and publish.
COPY . .
RUN dotnet publish src/Zoomers.Api/Zoomers.Api.csproj -c Release -o /app --no-restore

# ---- Runtime stage ----
FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS runtime
WORKDIR /app

# Run as a non-root user.
RUN adduser --disabled-password --gecos "" --uid 1001 appuser
USER appuser

COPY --from=build /app ./

# The hosting platform injects PORT; the app reads it and binds 0.0.0.0:$PORT.
# 8080 is the default when PORT is absent (e.g. local `docker run`).
ENV ASPNETCORE_ENVIRONMENT=Production
EXPOSE 8080

ENTRYPOINT ["dotnet", "Zoomers.Api.dll"]
