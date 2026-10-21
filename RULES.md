# Link rules

[`app/src/main/assets/rules.json`](app/src/main/assets/rules.json) says how Skiff opens links on the TV. When you
share a link, the **first rule whose `match` fits** is used. A link no rule matches still works: it's sent as it is to
whichever app on the TV opens it.

The app ships with a copy, and once a day it fetches this file from `main`. A newer `version` wins, so **a fix here
reaches everyone's phone without a new APK.** Bump `version` with every change.

## A rule

```json
{
  "id": "hotstar",
  "name": "JioHotstar",
  "match": { "hosts": ["hotstar.com", "jiohotstar.com"], "schemes": ["hotstar"] },
  "rewrite": { "from": "^https?://(?:www\\.)?(?:jio)?hotstar\\.com/", "to": "hotstar://" },
  "packages": ["in.startv.hotstar"],
  "flags": ["NEW_TASK", "CLEAR_TASK"],
  "title": "slug"
}
```

| Field | What it does |
|---|---|
| `id` | A short name for the rule. Required. |
| `name` | The service's name, for people. |
| `match.hosts` | Web links on these sites (and their subdomains; `www.` doesn't matter). |
| `match.schemes` | App links with these schemes (`hotstar://…`, `xstream://…`). A rule needs hosts or schemes. |
| `rewrite` | Changes the link before it goes to the TV: the first match of the regex `from` is replaced with `to` (`$1` works). For apps that open their own links but not their website's. If the result isn't a link, the original is sent. |
| `packages` | The TV apps to prefer, in order. Only apps the TV itself says open the link are ever used: this only orders them. |
| `flags` | How the app is started. Any of `NEW_TASK`, `CLEAR_TASK`, `CLEAR_TOP`, `SINGLE_TOP`, `NO_HISTORY`, `RESET_TASK_IF_NEEDED`. Default `NEW_TASK` + `CLEAR_TASK`: a fresh start of the app's screen, so an app that's already open reads the link instead of just coming to the front. |
| `title` | Where the title shown (and kept in History) comes from: `slug` (the link's own words: `…/maa-inti-bangaaram/…` → "Maa Inti Bangaaram"), `og` (the page's Open Graph tags), or `oembed:<https url with {link}>`. Default: `og`, then the slug. |

`apps` maps package names to the names shown for them (`"in.startv.hotstar": "JioHotstar"`).

## Adding an app

1. Find the link the TV app opens. Its own home-screen rows often show it: with USB debugging on, see what it writes
   to `content://android.media.tv/watch_next_program` (`intent_uri`), or try links with
   `adb shell am start -a android.intent.action.VIEW -d '<link>'`.
2. Check which apps open it: `adb shell cmd package query-activities --brief -a android.intent.action.VIEW -d '<link>'`.
3. Add a rule, bump `version`, and open a pull request.

## What a rule can't do

Rules are data. A rule can change the link and order the apps the TV says open it; it can't run commands, pick an app
that doesn't open the link, or use flags outside the list above. Every value is quoted before it reaches the TV's
shell. A rule that doesn't parse is skipped, and a file that doesn't parse is ignored (the previous rules stay).
