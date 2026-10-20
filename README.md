# Nuvio Remote

Open a movie or episode in [Nuvio](https://github.com/NuvioMedia/NuvioTV) on your Android TV / Google TV, from your phone.

Share an IMDb page to Nuvio Remote (from Chrome or the IMDb app), and Nuvio on the TV opens that title's stream
picker. Movies open right away; for a series, pick the season and episode, then **Open on TV**.

## How it works

- **Finding the TV.** Google TVs announce themselves on the Wi-Fi (mDNS): `_androidtvremote2._tcp` gives the TV's
  name ("Living room TV"), `_adb._tcp` on port 5555 means network debugging is on. No address to type
  ([TvFinder.kt](app/src/main/java/dev/nuvioremote/TvFinder.kt)).
- **The title.** The IMDb id (from the shared link, or typed) gives the name and whether it's a movie or a series,
  through [Cinemeta](https://v3-cinemeta.strem.io), the free catalog Stremio and Nuvio use
  ([Meta.kt](app/src/main/java/dev/nuvioremote/Meta.kt)).
- **Opening it.** Nuvio has no link for a title, but it opens one from these intent extras (the ones it writes to its
  own "Continue Watching" home-screen row). The phone sends them over the TV's ADB, with
  [dadb](https://github.com/mobile-dev-inc/dadb) ([TvLink.kt](app/src/main/java/dev/nuvioremote/TvLink.kt)):

  ```
  am start -n com.nuvio.tv/.MainActivity -f 0x14000000 \
    --es contentId tt42127457 --es contentType series --es videoId tt42127457:1:4 \
    --ei season 1 --ei episode 4 --es launchMode stream --es name 'Agent Kim Reactivated'
  ```

  `launchMode=stream` with a `name` goes to the stream picker; without the name, Nuvio opens the title's page.
  For a movie, `contentType=movie` and `videoId` is just the IMDb id.

## Setup

1. On the TV: Settings → System → About → press *Android TV OS build* 7 times; then Developer options → turn on
   **USB debugging**.
2. Phone and TV on the same Wi-Fi. Open Nuvio Remote: the TV shows up under **Nearby TVs**.
3. The first time you open something, the TV asks **Allow USB debugging?** for the phone: tick *Always allow*.

USB debugging stays on for this to work: anything you allowed on the TV can control it.

## Download

Get the APK from [Releases](../../releases/latest). Each tag like `v1.2.0` builds one, signed with the same release
key, so a new version installs over the old one.

## Build

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Kotlin, Jetpack Compose with Material 3 Expressive and Material You colors, minSdk 26.

A release (`assembleRelease`) is signed when `KEYSTORE_FILE`, `KEYSTORE_PASSWORD` and `KEY_ALIAS` are set, as the
[release workflow](.github/workflows/release.yml) does from the repo's secrets; `VERSION_NAME` and `VERSION_CODE`
come from the tag and the run number.
