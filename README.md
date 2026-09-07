# Player

A native Android IPTV player for user-supplied, legitimate sources. Player does not provide subscriptions, playlists, channels, stream discovery, scraping, DRM bypass, or bundled content.

## Version 1.0.0 foundation

- Kotlin, Jetpack Compose and Material 3
- Adaptive phone, tablet, foldable and Android TV navigation
- Xtream-compatible authentication and Live TV import
- Remote and local M3U/M3U8 import using streaming, background, batched parsing
- Room-backed indexed channel library, favourites and history foundations
- Android Keystore-backed encryption for source endpoints, credentials and stream URLs
- Media3/ExoPlayer playback for HLS, DASH and progressive streams, with retry and Picture-in-Picture
- No credentials or private source data are logged or committed

Movie, Series, XMLTV guide, universal-search aggregation and full source-management screens have extension points but are not complete in this initial release.

## Local development

Install Android Studio with JDK 17 and Android SDK 35. Open the repository and run the `app` debug configuration. Debug builds use the normal Android debug identity and install as `com.traynor.player.debug`, separately from production.

Official release builds deliberately fail without the permanent signing credentials. For an authorised local release, create ignored `keystore.properties`:

```properties
storeFile=/absolute/path/outside/repository/player-release.jks
storePassword=...
keyAlias=player
keyPassword=...
```

Never put IPTV details or signing material in project files.

## Production releases

Repository Actions secrets required:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Push a semantic tag matching `versionName`, such as `v1.0.0`. The workflow tests and lints, builds, verifies the APK signature and application ID, uploads an Actions artifact, and publishes `Player-v1.0.0.apk` to the corresponding GitHub Release. It fails closed if any signing input or verification is missing.

Keep the original keystore and passwords in a secure offline backup. Losing them prevents compatible updates to installed production builds.
