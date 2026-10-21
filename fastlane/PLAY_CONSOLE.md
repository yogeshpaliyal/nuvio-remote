# Play Console: what to fill in

The store text and graphics are in [metadata/android/en-US](metadata/android/en-US), in fastlane's layout (`fastlane
supply` uploads them as they are). This file has the rest of the Play Console's forms.

## Store listing

| Field | Value |
|---|---|
| App name | `title.txt`: Skiff: Send to TV |
| Short description | `short_description.txt` (73 of 80 characters) |
| Full description | `full_description.txt` |
| App icon | `images/icon.png` (512×512, from [docs/brand/play/icon.html](../docs/brand/play/icon.html)) |
| Feature graphic | `images/featureGraphic.png` (1024×500, from [feature-graphic.html](../docs/brand/play/feature-graphic.html)) |
| Phone screenshots | `images/phoneScreenshots/`: five, taken in demo mode (Skiff Debug → Settings → Demo mode) |
| Category | Tools |
| Tags | Remote control, Streaming |
| Website | https://github.yogeshpaliyal.com/skiff/ |
| Email | your support address (the store shows it publicly) |
| Privacy policy | https://github.com/yogeshpaliyal/skiff/blob/main/PRIVACY.md |

Screenshots worth taking: the main screen with a link and **Open with**, the **Connect** card, the remote, the
pairing code, History, and Settings.

## App content

- **Ads:** none.
- **App access:** everything works without an account. Reviewers can't reach a TV: say so, and that the app finds
  Android TVs on the same Wi-Fi.
- **Target audience:** 18 and over is simplest. Skiff isn't made for children.
- **Content rating (IARC):** category *Utility, productivity, communication or other*. No violence, sexual content,
  language, controlled substances or gambling. Users don't talk to each other or share their location. No purchases.
- **News, health, finance, government:** not applicable.

## Data safety

Skiff's own data stays on the phone; Firebase (a service provider, so this isn't "sharing") collects:

| Data type | Collected | Why | Optional |
|---|---|---|---|
| App info and performance: crash logs | Yes (Crashlytics) | App functionality, analytics | Yes: Settings → Privacy |
| App info and performance: diagnostics | Yes (Crashlytics) | App functionality, analytics | Yes |
| App activity: app interactions | Yes (Analytics) | Analytics | Yes |
| Device or other IDs | Yes (Firebase's app-instance id) | Analytics | Yes |

- **Encrypted in transit:** yes.
- **Can users ask for deletion:** no in-app way. Firebase deletes Analytics data on its own schedule (set the retention
  in Analytics) and Crashlytics data after 90 days.
- **Not collected:** location, personal info, messages, photos, contacts, browsing history, financial data. The links
  and titles you send, and history, never leave the phone.
- **Approximate location:** Analytics derives a country from the IP address. Google's guidance for Firebase says to
  check its [data disclosure page](https://firebase.google.com/docs/android/play-data-disclosure) for the current
  answer before submitting.

## Before the first release

- **Developer account:** $25, once. A new personal account must run a closed test with at least 12 testers for 14
  days before it can publish to everyone.
- **App bundle:** Play takes an `.aab`, not an `.apk`: the release workflow builds `assembleRelease` today; it needs
  `bundleRelease` too.
- **Signing:** with Play App Signing, keep the current key as the upload key (`~/.config/nuvio-remote/release.jks`),
  so GitHub and Play builds update each other.
- **Package:** `dev.skiff`. The debug build (`dev.skiff.debug`) never goes to Play.
