// Generates the JSON data package for the Genshin Insight app.
// Runs on GitHub Actions (see .github/workflows/update-data.yml) so the repo
// stays in sync with genshin-db without needing Node installed locally.
//
// Output: ../data/{manifest,characters,weapons,artifacts,enemies,domains,talents}.json

const fs = require('fs');
const path = require('path');
const genshindb = require('genshin-db');

const OUT_DIR = path.join(__dirname, '..', 'data');

function slug(name) {
  return name
    .toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, '') // strip accents
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
}

function pickImage(images, ...keys) {
  if (!images) return null;
  for (const key of keys) {
    if (images[key]) return images[key];
  }
  return null;
}

// enka.network mirrors the game's own UI_* texture files directly and is
// far more reliable than genshin-db's "mihoyo_*" URLs, which point at
// miHoYo's BBS showcase cache and 404 for a large fraction of characters/
// weapons (confirmed by spot-checking Arlecchino, Wriothesley, Neuvillette,
// Athame Artis, etc. - even long-released ones). Prefer the deterministic
// Enka URL built from filename_icon, and only fall back to the genshin-db
// hosted fields if no filename is available at all.
function enkaUrl(filenameIcon) {
  return filenameIcon ? `https://enka.network/ui/${filenameIcon}.png` : null;
}

// gi.yatta.moe mirrors monster/boss icons the same way (genshin-db exposes
// no hosted URL for these at all, only the bare filename).
function monsterIconUrl(filenameIcon) {
  return filenameIcon ? `https://gi.yatta.moe/assets/UI/monster/${filenameIcon}.png` : null;
}

// Ascension/level-up cost entries only carry {id, name, count} - no icon.
// Build a name -> icon lookup once from the materials folder (and Mora,
// which isn't in that folder) so we can attach real item art to costs.
function buildMaterialIconLookup() {
  const lookup = { Mora: enkaUrl('UI_ItemIcon_202') };
  const names = genshindb.materials('names', { matchCategories: true });
  for (const name of names) {
    const m = genshindb.materials(name);
    if (m?.name && m.images?.filename_icon) {
      lookup[m.name] = enkaUrl(m.images.filename_icon);
    }
  }
  return lookup;
}

function enrichCosts(costs, materialIcons) {
  if (!costs) return null;
  const result = {};
  for (const stage of Object.keys(costs)) {
    result[stage] = (costs[stage] || []).map((item) => ({
      id: item.id,
      name: item.name,
      count: item.count,
      icon: materialIcons[item.name] || null,
    }));
  }
  return result;
}

function buildCharacters(materialIcons) {
  const names = genshindb.characters('names', { matchCategories: true });
  const out = [];
  for (const name of names) {
    const c = genshindb.characters(name);
    if (!c || !c.weaponType || !c.elementType) continue; // skip non-playable entries
    // Playable characters are always QUALITY_ORANGE (5*) or QUALITY_PURPLE (4*).
    // bodyType has many values (BODY_LOLI, BODY_BOY, BODY_LADY, ...) and using
    // an allowlist previously dropped valid characters like Iansan - quality
    // is the reliable signal instead.
    if (!['QUALITY_ORANGE', 'QUALITY_PURPLE'].includes(c.qualityType)) continue;

    const talentData = genshindb.talents(name);
    const constellationData = genshindb.constellations(name);

    out.push({
      id: slug(c.name),
      name: c.name,
      title: c.title || '',
      description: c.description || '',
      rarity: c.rarity,
      element: c.elementText,
      weapon: c.weaponText,
      region: c.region || 'Unknown',
      affiliation: c.affiliation || '',
      substat: c.substatText || '',
      birthday: c.birthday || '',
      cvEnglish: c.cv?.english || '',
      cvJapanese: c.cv?.japanese || '',
      icon: enkaUrl(c.images?.filename_icon) || pickImage(c.images, 'hoyowiki_icon', 'mihoyo_icon', 'icon'),
      sideIcon: enkaUrl(c.images?.filename_sideIcon) || pickImage(c.images, 'mihoyo_sideIcon', 'sideIcon'),
      card: pickImage(c.images, 'card'),
      portrait: pickImage(c.images, 'portrait'),
      splash: pickImage(c.images, 'cover1', 'cover2'),
      fandomUrl: c.url?.fandom || null,
      ascensionCosts: enrichCosts(c.costs, materialIcons),
      talents: (talentData ? [talentData.combat1, talentData.combat2, talentData.combat3]
        .filter(Boolean)
        .map((t) => ({ name: t.name, description: t.description })) : []),
      passives: (talentData ? Object.keys(talentData)
        .filter((k) => k.startsWith('passive'))
        .map((k) => ({ name: talentData[k].name, description: talentData[k].description })) : []),
      constellations: (constellationData ? Object.keys(constellationData)
        .filter((k) => k.startsWith('c'))
        .map((k) => ({ level: k, name: constellationData[k].name, description: constellationData[k].description })) : []),
      guideUrl: `https://keqingmains.com/${slug(c.name).replace(/_/g, '-')}/`,
    });
  }
  return out.sort((a, b) => b.rarity - a.rarity || a.name.localeCompare(b.name));
}

function buildWeapons(materialIcons) {
  const names = genshindb.weapons('names', { matchCategories: true });
  const out = [];
  for (const name of names) {
    const w = genshindb.weapons(name);
    if (!w || !w.weaponType) continue;
    out.push({
      id: slug(w.name),
      name: w.name,
      type: w.weaponText,
      rarity: w.rarity,
      description: w.description || '',
      baseAtk: w.baseAtkValue || null,
      mainStat: w.mainStatText || '',
      mainStatValue: w.baseStatText || '',
      effectName: w.effectName || '',
      refinements: ['r1', 'r2', 'r3', 'r4', 'r5']
        .filter((k) => w[k])
        .map((k) => ({ refinement: k.toUpperCase(), description: w[k].description })),
      ascensionCosts: enrichCosts(w.costs, materialIcons),
      icon: enkaUrl(w.images?.filename_icon) || pickImage(w.images, 'mihoyo_icon', 'icon'),
      awakenIcon: enkaUrl(w.images?.filename_awakenIcon) || pickImage(w.images, 'mihoyo_awakenIcon', 'awakenicon'),
    });
  }
  return out.sort((a, b) => b.rarity - a.rarity || a.name.localeCompare(b.name));
}

function buildArtifacts() {
  const names = genshindb.artifacts('names', { matchCategories: true });
  const out = [];
  for (const name of names) {
    const a = genshindb.artifacts(name);
    if (!a) continue;
    const pieces = ['flower', 'plume', 'sands', 'goblet', 'circlet']
      .filter((k) => a[k])
      .map((k) => ({
        slot: k,
        name: a[k].name,
        description: a[k].description || '',
        image: enkaUrl(a.images?.[`filename_${k}`]) || pickImage(a.images, `mihoyo_${k}`, k),
      }));
    out.push({
      id: slug(a.name),
      name: a.name,
      rarity: Math.max(...(a.rarityList || [a.rarity || 5])),
      effect2Pc: a.effect2Pc || '',
      effect4Pc: a.effect4Pc || '',
      pieces,
    });
  }
  return out.sort((a, b) => a.name.localeCompare(b.name));
}

// Hand-curated Normal/Elite/Boss/Weekly Boss taxonomy with element + region,
// which genshin-db's enemy folder doesn't cleanly provide on its own. This
// gets enriched below with real description/drops looked up from genshin-db.
const CURATED_ENEMIES = require('./curated-enemies.json');

function buildEnemies() {
  const out = [];
  for (const curated of CURATED_ENEMIES) {
    const match = genshindb.enemies(curated.name) || genshindb.enemies(curated.id.replace(/_/g, ' '));
    out.push({
      id: curated.id,
      name: curated.name,
      category: curated.category,
      element: curated.element || 'None',
      region: curated.region || 'Teyvat',
      description: match?.description || '',
      drops: match ? (match.rewardPreview || []).filter((r) => r.name !== 'Mora').map((r) => r.name) : [],
      icon: monsterIconUrl(match?.images?.filename_icon),
    });
  }
  return out;
}

function buildDomains() {
  const names = genshindb.domains('names', { matchCategories: true });
  const out = [];
  for (const name of names) {
    const d = genshindb.domains(name);
    if (!d) continue;
    out.push({
      id: slug(d.name),
      name: d.name,
      type: d.domainText || '',
      region: d.regionName || '',
      entrance: d.entranceName || '',
      description: d.description || '',
      recommendedLevel: d.recommendedLevel || null,
      recommendedElements: d.recommendedElements || [],
      unlockRank: d.unlockRank || null,
      monsters: (d.monsterList || []).map((m) => m.name),
      rewardItems: (d.rewardPreview || []).filter((r) => r.rarity).map((r) => r.name),
      mechanics: d.disorder || [],
    });
  }
  return out.sort((a, b) => a.name.localeCompare(b.name));
}

function buildTalentTables(characters) {
  // Keyed by character id, keeps the heavy per-level multiplier tables out of
  // characters.json so the browsing list stays light on mobile data.
  const table = {};
  for (const c of characters) {
    const t = genshindb.talents(c.name);
    if (!t) continue;
    const combats = ['combat1', 'combat2', 'combat3']
      .filter((k) => t[k])
      .map((k) => ({
        key: k,
        name: t[k].name,
        attributes: t[k].attributes || null,
      }));
    table[c.id] = combats;
  }
  return table;
}

function writeJson(filename, data) {
  fs.writeFileSync(path.join(OUT_DIR, filename), JSON.stringify(data));
  console.log(`wrote ${filename} (${Array.isArray(data) ? data.length + ' items' : 'object'})`);
}

function main() {
  fs.mkdirSync(OUT_DIR, { recursive: true });

  const materialIcons = buildMaterialIconLookup();
  const characters = buildCharacters(materialIcons);
  const weapons = buildWeapons(materialIcons);
  const artifacts = buildArtifacts();
  const enemies = buildEnemies();
  const domains = buildDomains();
  const talents = buildTalentTables(characters);

  writeJson('characters.json', characters);
  writeJson('weapons.json', weapons);
  writeJson('artifacts.json', artifacts);
  writeJson('enemies.json', enemies);
  writeJson('domains.json', domains);
  writeJson('talents.json', talents);
  writeJson('manifest.json', {
    generatedAt: new Date().toISOString(),
    source: 'genshin-db',
    counts: {
      characters: characters.length,
      weapons: weapons.length,
      artifacts: artifacts.length,
      enemies: enemies.length,
      domains: domains.length,
    },
  });
}

main();
