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

## AdMob

- Debug builds use Google's demo app/ad unit IDs, so development ads should show the `Test Ad` label.
- Release builds default to OmniRead's live AdMob IDs:
  - App: `ca-app-pub-1681671255853598~7517732244`
  - Rewarded `Chapter Unlock Reward`: `ca-app-pub-1681671255853598/4826017508`
  - Banner `Home Feed Banner`: `ca-app-pub-1681671255853598/1418840716`
  - Interstitial `Reader Chapter Transition Interstitial`: `ca-app-pub-1681671255853598/6994601029`
- Release IDs can be overridden with Gradle properties or environment variables:
  - `OMNIREAD_ADMOB_APP_ID`
  - `OMNIREAD_ADMOB_REWARDED_AD_UNIT_ID`
  - `OMNIREAD_ADMOB_BANNER_AD_UNIT_ID`
  - `OMNIREAD_ADMOB_INTERSTITIAL_AD_UNIT_ID`
- The app caps ad content at Teen and explicitly marks OmniRead as not child-directed.
- Do not click live ads while testing release builds. Use debug builds or configured AdMob test devices for development.
- Rewarded ads still depend on AdMob server-side verification for trusted rewards. AdMob requires an HTTPS callback URL, so the temporary HTTP endpoint cannot be saved in AdMob; update SSV after the production API domain is live.
- Leave AdMob store details unlinked until the Google Play app/listing is available. Before production review, add a developer website and publish `app-ads.txt`.

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
- AdMob IDs are injected through Gradle build config fields and the manifest placeholder.
- Backup rules exclude auth tokens and offline chapter caches so refresh tokens and saved chapter text don't sync across devices.
- Compose previews are not included in this scaffold; add per-screen `@Preview` composables as you iterate.
