# Skiff privacy policy

*Last updated: 21 October 2026*

Skiff (`dev.skiff`) sends what you share from your phone to your TV, on your Wi-Fi. This is what it collects, and
where it goes.

## On your phone only

- **History and Saved for later**: the titles and links you shared, pasted or played (`files/library.json`).
- **Activity**: the last 50 things sent to the TV (keys, typed text, links, commands), shown in Settings → Activity.
- **Your TVs**: their addresses, the pairing (the TV's certificate fingerprint), the phone's own key for USB
  debugging and certificate for pairing (in the Android Keystore), and your settings.

None of this leaves the phone, except to your TV when you send something.

## Sent to services

- **Cinemeta** (`v3-cinemeta.strem.io`): the IMDb id of a title you share, for its name, art and type; and the words
  you type in the search box (not a link or an IMDb id), to find matching titles. Nothing else goes with them.
- **The page you share**: Skiff reads its title and picture (as a browser would).
- **Link rules**: once a day, Skiff downloads [rules.json](app/src/main/assets/rules.json) from this repository.
- **Firebase Crashlytics** (Google): when Skiff crashes, the crash report (stack trace, device model, Android
  version, app version).
- **Firebase Analytics** (Google): which screens are opened, and whether opening a link, playing a title,
  connecting the remote, pairing, an ADB command or an APK install worked; how it went (pairing or USB debugging);
  for a link, its site (for example `youtube.com`). Firebase adds an app-instance id, device model, Android version,
  country and app version. **Never** titles, links, text you type, or commands.

Crashlytics and Analytics are on by default; **Settings → Privacy → Share crash reports and usage** turns both off.

Skiff has no account, no ads, and sells nothing. Google's handling of Firebase data:
<https://firebase.google.com/support/privacy>.

## Contact

Questions: open an issue at <https://github.com/yogeshpaliyal/skiff/issues>.
