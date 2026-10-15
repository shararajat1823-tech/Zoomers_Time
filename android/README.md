# Zoomers — Android app

Kotlin + Jetpack Compose client for the Zoomers platform, wired to the backend
API. The visual language is adapted from the "Lumi" concept: a warm cream
canvas, deep-teal primary, serif wordmark, soft rounded cards, and a 5-tab
navigation model — rebuilt as Zoomers' own original brand.

> **Status (honest):** this module was authored in a cloud container **without an
> Android SDK or emulator**, so it has **not been compiled or run here**. It is
> idiomatic, current-stable Compose written to build in Android Studio, but
> expect to resolve the occasional first-build nit on your machine. See
> `../DEVELOPMENT_STATUS.md`.

## Responsive by design

The app adapts to every form factor via `WindowSizeClass`
(`designsystem/foundation/Responsive.kt`):

| Width class | Navigation | Content | Grids |
|-------------|-----------|---------|-------|
| Compact (phones) | bottom bar | full-bleed feed | 3 columns |
| Medium (large phones, small foldables, split-screen) | side rail | centered, capped ~640dp | 4 columns |
| Expanded (tablets, unfolded foldables, desktop/CrOS) | side rail | centered, capped ~960dp; feed ~600dp | 6 columns |

The same screen composables are reused across sizes — responsiveness is a
property of the layout system, not per-screen special-casing. Forms cap their
width, the feed centers and caps on large screens, and grids reflow by column
count.

## Architecture

Single `:app` module with packages that mirror the planned multi-module split
(`core:*` / `feature:*`), so it can be extracted into modules later without
moving logic:

```
com.zoomers.app
  designsystem/       theme (Color/Type/Shape/Dimens/Theme), foundation (Responsive),
                      component (Buttons, TextFields, Avatar, PillTabs, Wordmark,
                      States, PostCard deps, Navigation bar/rail)
  core/
    model/            UI models + DTO mappers
    network/          Retrofit API, DTOs, AuthInterceptor, TokenAuthenticator (refresh
                      rotation), ApiResult, Hilt NetworkModule
    data/             TokenStore (DataStore), Auth/Feed/Profile repositories
    common/           formatting (relative time, counts, caption annotation)
  feature/
    auth/             Login + Register screens + AuthViewModel
    home/             HomeScreen, HomeViewModel, PostCard, StoryRow (responsive feed)
    profile/          ProfileScreen + ProfileViewModel (me + logout)
  navigation/         RootViewModel (auth gate), ZoomersApp, MainScaffold (bar/rail),
                      destinations, placeholders
```

Patterns: MVVM with `ViewModel` + `StateFlow`, Hilt DI, Navigation Compose,
Coroutines, Coil image loading, DataStore token persistence, optimistic UI
(like/save with rollback), cursor-paginated infinite feed, skeleton/empty/error
states.

## What's implemented

- **Design system** — full light/dark theme, type scale, shapes, spacing, and
  reusable components matching the concept (pill tabs, story rail, post card with
  carousel, bottom bar + side rail, buttons, fields, state views).
- **Auth** — login + register against `/auth`, token persistence, session gate.
- **Home feed** — stories rail, Following/For-you pill tabs, post cards (like,
  save, carousel, hashtag-tinted captions), pull-to-refresh-less infinite scroll,
  skeleton/empty/error states — all responsive.
- **Profile** — loads `/users/me`, shows the concept's header + stats + grid,
  logout.
- **Networking** — Retrofit + kotlinx-serialization, bearer auth interceptor,
  automatic refresh-token rotation on 401.

Search, Create, Reels, Messages, Notifications, and Comments are navigable
placeholders for the next milestone.

## Build & run

Requires Android Studio (Koala or newer) or a local Android SDK.

```bash
# from android/
# Android Studio: Open this folder; it will sync Gradle and fetch the wrapper.
# CLI (with a local SDK + generated wrapper):
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The debug build points at `http://10.0.2.2:5080/api/v1/` — the emulator's route
to a backend running on the host at port 5080. Run the backend
(`cd ../backend && dotnet run --project src/Zoomers.Api`, configured to listen on
5080) and launch the app in an emulator to exercise the full stack. Change
`API_BASE_URL` in `app/build.gradle.kts` for other setups. Production uses an
HTTPS base URL; cleartext is restricted to local dev hosts via
`network_security_config.xml`.

## Before release (tracked)

- Back `TokenStore` with `EncryptedSharedPreferences` / Keystore (see
  `../SECURITY.md`).
- Bundle the brand serif font (currently the system serif).
- Add instrumented UI tests and screenshot tests across window sizes.
