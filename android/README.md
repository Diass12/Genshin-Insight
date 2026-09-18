# Genshin Insight (Android)

Kotlin + Jetpack Compose app. See the [repo root README](../README.md) for how
the auto-updating data pipeline works — this folder is just the Android Studio
project itself.

## Modules
- Home dashboard + My Roster
- Character DB: search, element/rarity filters, favorites, roster, build notes,
  talents/constellations, KQM guide link, YouTube search shortcut
- Weapon DB: search, type/rarity filters, refinement details
- Artifact DB: searchable, 2pc/4pc effects, per-piece art
- Monster / Enemy DB: searchable, category/element/region filters, descriptions + drops
- Team Builder: 4 slots, local save, element/resonance snapshot, preset team ideas
- Community & Guides: featured creator, tips shortcuts, KQM/wiki/HoYoLAB links
- Wallpaper Gallery: character splash art + curated wallpapers
- Tools: basic damage model, material planning shell
- Data layer: fetches `data/*.json` from the repo, caches locally, falls back to
  bundled assets when offline

## Data completeness
Data is sourced from [genshin-db](https://github.com/theBowja/genshin-db) and
kept current by `.github/workflows/update-data.yml`. Fields that source doesn't
expose yet (enemy HP/RES scaling, exact material totals) are clearly marked in
the UI rather than guessed at.
