# Genshin Insight

Native Android companion app (Kotlin + Jetpack Compose) for Genshin Impact, with
game data that auto-updates itself from [genshin-db](https://github.com/theBowja/genshin-db)
instead of being frozen inside the APK.

## Structure

```
android/    Android Studio project — open this folder in Android Studio
data/       Generated JSON data package (characters, weapons, artifacts, enemies, domains, talents)
scripts/    Node.js generator that builds data/ from genshin-db
.github/workflows/update-data.yml   Scheduled job that keeps data/ in sync automatically
```

## How the auto-update works

1. `scripts/generate-data.js` pulls characters, weapons, artifacts, enemies and
   domains from the `genshin-db` npm package (MIT licensed, community-maintained,
   updated within a day or two of every game patch).
2. `.github/workflows/update-data.yml` runs that script on GitHub's servers —
   once a day on a schedule, and immediately whenever `scripts/` changes — and
   commits the refreshed JSON back into `data/` if anything changed. This needs
   no Node.js install on your PC; it all happens in the cloud.
3. The Android app fetches `data/*.json` straight from this repo
   (`raw.githubusercontent.com/Diass12/Genshin-Insight/main/data/...`) the first
   time it's opened with internet, caches it locally, and falls back to a
   bundled copy in `android/app/src/main/assets/` if there's no internet at all.
   So updating the game data never requires rebuilding or reinstalling the APK.

To force an update immediately instead of waiting for the daily schedule, open
this repo's **Actions** tab on GitHub → "Update game data" → **Run workflow**.

## Opening in Android Studio

Open the `android/` folder (not the repo root) as the project.

## What's implemented

- Character / Weapon / Artifact / Monster & Boss databases with search, filters,
  real images sourced from miHoYo's own CDN, talents, constellations, refinements,
  2-piece/4-piece artifact effects, and enemy drop tables.
- Community tab: featured creator (Kokobear, Indonesian Genshin guides), curated
  tips topics that deep-link to YouTube search, and links to KQM / the Fandom wiki
  / HoYoLAB. Every character detail page also links straight to its KQM guide page.
- Roster, favorites, build notes, team builder, and preset team ideas — stored
  locally via SharedPreferences.
- Basic damage calculator and material-planning shell.

## Next up: Readiness Score calculator

Not built yet. The plan: pick up to 4 characters from your roster (weapon/artifact
level optional), pick a domain or piece of content, and get a heuristic
"~X% likely to clear" estimate plus suggestions — using the real talent multiplier
tables already being generated into `data/talents.json` and the domain data in
`data/domains.json`. This is intentionally an estimate, not a full combat
simulator (Genshin's real combat math involves rotation timing, ICD, and buff
stacking that's out of scope for a mobile companion app).
