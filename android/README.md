# OmniRead Android App

Kotlin + Jetpack Compose Android client for OmniRead.

## Stack

- Kotlin 2.1, Java 17, Gradle 8.10, AGP 8.7
- Jetpack Compose (BOM 2024.12) + Material 3
- Hilt (DI), Retrofit + OkHttp + kotlinx.serialization
- Navigation Compose, Coil for images, DataStore for tokens
- Play Billing (for v1.1 subscription/coins purchase wiring)

## Build

1. Open `android/` in Android Studio Ladybug or newer.
2. Set the API base URL by adding to `~/.gradle/gradle.properties` (or per-project):

   ```
   OMNIREAD_API_BASE=http://10.0.2.2:8000
   ```

   `10.0.2.2` reaches your host machine from the emulator. For physical devices use your LAN IP.

3. Sync Gradle and run the `app` configuration.

## Architecture

```
data/
├── api/        Retrofit interface, envelope, auth interceptor
├── local/      DataStore-backed TokenStore
├── model/      Serialization models (matches backend Pydantic schemas)
└── repo/       AuthRepository, StoryRepository, UserRepository, PaymentRepository
di/             Hilt NetworkModule
ui/
├── theme/      Dark cinematic Material3 theme + tokens
├── components/ Pill, FullScreenLoading, ErrorBanner
└── screens/
    ├── auth/         AuthLanding, Login, Register
    ├── onboarding/   4-screen onboarding pager
    ├── feed/         Vertical pager feed + bottom nav scaffold
    ├── search/       Search with trending fallback
    ├── detail/       Story detail with chapter list
    ├── reader/       Long-form reader + locked-chapter unlock sheet
    ├── library/      Bookmarks + history tabs
    ├── profile/      Profile, streak, daily login, sign out
    ├── coins/        Coin store (ads + packs)
    └── SplashScreen.kt
util/           Device fingerprint helper
```

## Auth flow

- Tokens persist in `DataStore` (`datastore/omniread_auth.preferences_pb`, excluded from cloud/device backup)
- Splash decides between auth landing and main scaffold based on stored access token
- Refresh token rotation is server-driven (matches backend `/v1/auth/refresh`)
- Guest sign-in is one tap from the auth landing — uses a SHA-256 device fingerprint

## What's intentionally not in v1

- Audio narration screens, mini player, fullscreen audio, ambient sounds — see `BLUEPRINT_GAPS.md`
- Comments posting (read scaffold present, write to be added in v1.1)
- Subscription purchase UI (Play Billing dependency is wired; flow to be implemented in v1.1)
- Offline downloads (premium feature, v1.1)
- Push notification token registration UI (endpoint `POST /v1/user/fcm-token` exists; FCM SDK to be added)
- iOS

## Notes

- `network_security_config.xml` allows cleartext only for `10.0.2.2` and `localhost` — production traffic is HTTPS-only.
- AdMob app ID in `strings.xml` is the Google test ID — replace before release.
- Backup rules exclude auth tokens and offline chapter caches so refresh tokens and saved chapter text don't sync across devices.
- Compose previews are not included in this scaffold; add per-screen `@Preview` composables as you iterate.
