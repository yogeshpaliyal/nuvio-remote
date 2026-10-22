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
- **Trakt** (`trakt.tv`): for a Trakt link you share or paste, Skiff opens that title's Trakt page (as a browser would)
  to read its IMDb id. When that fails, the words of the link's name go to Cinemeta's search.
- **Simkl** (`api.simkl.com`), **Letterboxd** (`letterboxd.com`, `boxd.it`) and **WeTrakr** (`wetrakr.com`): the same,
  for their links: Skiff asks that site for the title's IMDb id, sending only the id or name in the link. Simkl's and
  Letterboxd's, when that fails, go to Cinemeta's search by name. An **MDBList** link has the IMDb id in it, or else a
  name that goes to Cinemeta's search: nothing is sent to MDBList.
- **Link rules**: once a day, Skiff downloads [rules.json](app/src/main/assets/rules.json) from this repository.
- **Updates**: about twice a day, Skiff asks GitHub (`api.github.com`) for its latest release, to tell you when there's
  a newer version. Nothing about you goes with it.
- **Firebase Crashlytics** (Google): when Skiff crashes, the crash report (stack trace, device model, Android
  version, app version); and when something fails without crashing (reaching the TV, an update, the link rules),
  where it failed, the kind of error and its stack trace, without the error's message.
- **Firebase Analytics** (Google): which screens are opened, and whether opening a link, playing a title,
  connecting the remote, pairing, typing on the TV, an ADB command or an APK install worked; how it went (pairing
  or USB debugging); for a link, its site (for example `youtube.com`). Also which features are used: sharing to
  Skiff, pasting, a title search (how many titles it found, and which place in the list you picked), finding a
  title on Cinemeta, picking a TV, saving, editing or removing an entry, and the update prompt; and where something
  failed, with the kind of error. Firebase adds an app-instance id, device model, Android version, country and app
  version. **Never** titles, links, text you type or search for, TV addresses, or commands.

Crashlytics and Analytics are on by default; **Settings → Privacy → Share crash reports and usage** turns both off.

Skiff has no account, no ads, and sells nothing. Google's handling of Firebase data:
<https://firebase.google.com/support/privacy>.

## Contact

Questions: open an issue at <https://github.com/yogeshpaliyal/skiff/issues>.
