package com.genshincompanion.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.app.WallpaperManager
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.genshincompanion.app.data.DataRepository
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TalentInfo(val name: String, val description: String, val descriptionId: String = "")
private data class ConstellationInfo(val level: String, val name: String, val description: String, val descriptionId: String = "")
private data class RefinementInfo(val refinement: String, val description: String, val descriptionId: String = "")
private data class AscendCost(val name: String, val count: Int, val icon: String?)

/** en/id current language currently selected by the user (default: id). */
private val LocalLang = compositionLocalOf { "id" }

/** Picks the Indonesian variant when available and the app is set to id, else falls back to English. */
@Composable
private fun localized(en: String, id: String): String =
    if (LocalLang.current == "id" && id.isNotBlank()) id else en

// Filter chip values like "All" are compared internally (e.g. `element ==
// "All"`), so the underlying value stays fixed; this only translates the
// displayed label. Element/weapon-type names (Pyro, Sword, ...) are left
// as-is since even the official Indonesian localization keeps them in
// English (confirmed against genshin-db's Indonesian output).
@Composable
private fun filterLabel(value: String): String = if (value == "All") localized("All", "Semua") else value

// Section chip keys stay in English internally (used for state/`when`
// matching); this only translates what's shown on the chip.
@Composable
private fun sectionLabel(key: String): String = when (key) {
    "Overview" -> localized("Overview", "Ringkasan")
    "Talents" -> localized("Talents", "Talent")
    "Constellations" -> localized("Constellations", "Constellation")
    "Level Up" -> localized("Level Up", "Naik Level")
    "Build" -> localized("Build", "Build")
    else -> key
}

// Ascension breakpoints are fixed game mechanics (level cap goes 20/40/50/
// 60/70/80/90 for every character and weapon since launch), not something
// that changes per patch, so it's safe to hardcode rather than pull from data.
// AscendStageKeys[i] is the cost key needed to go from AscendLevelLabels[i]
// to AscendLevelLabels[i+1]; the first interval (Lv1-20) needs no ascension.
private val AscendLevelLabels = listOf("Lv1", "Lv20", "Lv40", "Lv50", "Lv60", "Lv70", "Lv80", "Lv90")
private val AscendStageKeys = listOf(null, "ascend1", "ascend2", "ascend3", "ascend4", "ascend5", "ascend6")
private data class ArtifactPiece(val slot: String, val name: String, val description: String, val image: String?)

private data class Character(
    val id: String,
    val name: String,
    val title: String,
    val description: String,
    val descriptionId: String,
    val rarity: Int,
    val element: String,
    val weapon: String,
    val region: String,
    val affiliation: String,
    val substat: String,
    val icon: String?,
    val card: String?,
    val splash: String?,
    val fandomUrl: String?,
    val ascensionCosts: Map<String, List<AscendCost>>,
    val talents: List<TalentInfo>,
    val passives: List<TalentInfo>,
    val constellations: List<ConstellationInfo>,
    val guideUrl: String
)

private data class Weapon(
    val id: String,
    val name: String,
    val type: String,
    val rarity: Int,
    val description: String,
    val descriptionId: String,
    val baseAtk: Double?,
    val mainStat: String,
    val mainStatValue: String,
    val effectName: String,
    val refinements: List<RefinementInfo>,
    val ascensionCosts: Map<String, List<AscendCost>>,
    val icon: String?
)

private data class Artifact(
    val id: String,
    val name: String,
    val rarity: Int,
    val effect2Pc: String,
    val effect2PcId: String,
    val effect4Pc: String,
    val effect4PcId: String,
    val pieces: List<ArtifactPiece>
)

private data class Enemy(
    val id: String,
    val name: String,
    val category: String,
    val element: String,
    val region: String,
    val description: String,
    val descriptionId: String,
    val drops: List<String>,
    val icon: String?
)

private data class Team(val name: String, val core: List<String>, val focus: String)

private data class Domain(
    val id: String,
    val name: String,
    val type: String,
    val region: String,
    val entrance: String,
    val description: String,
    val descriptionId: String,
    val recommendedLevel: Int?,
    val recommendedElements: List<String>,
    val monsters: List<String>,
    val rewardItems: List<String>
)
private data class Wallpaper(val title: String, val category: String, val url: String)

private enum class Tab(val labelEn: String, val labelId: String) {
    HOME("Home", "Beranda"),
    CHAR("Char", "Char"),
    WEAPON("Weapon", "Senjata"),
    ARTIFACT("Artifact", "Artifact"),
    TEAM("Team", "Tim"),
    TOOLS("Tools", "Tools"),
    MORE("More", "Lainnya")
}

@Composable
private fun Tab.label(): String = localized(labelEn, labelId)

private class Store(context: Context) {
    private val prefs = context.getSharedPreferences("gc_store", Context.MODE_PRIVATE)

    fun set(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }

    fun get(key: String): Set<String> = prefs.getStringSet(key, emptySet()) ?: emptySet()

    fun note(id: String): String = prefs.getString("note_$id", "") ?: ""

    fun note(id: String, value: String) {
        prefs.edit().putString("note_$id", value).apply()
    }

    fun saveTeam(value: List<String>) {
        prefs.edit().putString("team", value.joinToString("|")) .apply()
    }

    fun team(): List<String> = prefs.getString("team", "")
        ?.split("|")
        ?.filter(String::isNotBlank)
        ?: emptyList()

    fun lang(): String = prefs.getString("lang", "id") ?: "id"

    fun setLang(value: String) {
        prefs.edit().putString("lang", value).apply()
    }
}

private data class DB(
    val characters: List<Character>,
    val weapons: List<Weapon>,
    val artifacts: List<Artifact>,
    val enemies: List<Enemy>,
    val teams: List<Team>,
    val domains: List<Domain>
) {
    companion object {
        private fun stringList(obj: JSONObject, key: String): List<String> {
            val arr = obj.optJSONArray(key) ?: JSONArray()
            return buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }
        }

        private fun parseAscendCosts(item: JSONObject): Map<String, List<AscendCost>> {
            val obj = item.optJSONObject("ascensionCosts") ?: return emptyMap()
            val result = mutableMapOf<String, List<AscendCost>>()
            obj.keys().forEach { stage ->
                val arr = obj.optJSONArray(stage) ?: JSONArray()
                result[stage] = buildList {
                    for (i in 0 until arr.length()) {
                        val cost = arr.getJSONObject(i)
                        add(
                            AscendCost(
                                name = cost.optString("name"),
                                count = cost.optInt("count"),
                                icon = cost.optString("icon").ifBlank { null }
                            )
                        )
                    }
                }
            }
            return result
        }

        fun parseCharacters(arr: JSONArray): List<Character> = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val talents = buildList {
                    val t = item.optJSONArray("talents") ?: JSONArray()
                    for (j in 0 until t.length()) {
                        val o = t.getJSONObject(j)
                        add(TalentInfo(o.optString("name"), o.optString("description"), o.optString("descriptionId")))
                    }
                }
                val passives = buildList {
                    val t = item.optJSONArray("passives") ?: JSONArray()
                    for (j in 0 until t.length()) {
                        val o = t.getJSONObject(j)
                        add(TalentInfo(o.optString("name"), o.optString("description"), o.optString("descriptionId")))
                    }
                }
                val constellations = buildList {
                    val t = item.optJSONArray("constellations") ?: JSONArray()
                    for (j in 0 until t.length()) {
                        val o = t.getJSONObject(j)
                        add(ConstellationInfo(o.optString("level"), o.optString("name"), o.optString("description"), o.optString("descriptionId")))
                    }
                }
                add(
                    Character(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        title = item.optString("title"),
                        description = item.optString("description"),
                        descriptionId = item.optString("descriptionId"),
                        rarity = item.optInt("rarity"),
                        element = item.optString("element"),
                        weapon = item.optString("weapon"),
                        region = item.optString("region"),
                        affiliation = item.optString("affiliation"),
                        substat = item.optString("substat"),
                        icon = item.optString("icon").ifBlank { null },
                        card = item.optString("card").ifBlank { null },
                        splash = item.optString("splash").ifBlank { null },
                        fandomUrl = item.optString("fandomUrl").ifBlank { null },
                        ascensionCosts = parseAscendCosts(item),
                        talents = talents,
                        passives = passives,
                        constellations = constellations,
                        guideUrl = item.optString(
                            "guideUrl",
                            "https://keqingmains.com/${item.optString("id").replace('_', '-')}/"
                        )
                    )
                )
            }
        }

        fun parseWeapons(arr: JSONArray): List<Weapon> = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val refinements = buildList {
                    val r = item.optJSONArray("refinements") ?: JSONArray()
                    for (j in 0 until r.length()) {
                        val o = r.getJSONObject(j)
                        add(RefinementInfo(o.optString("refinement"), o.optString("description"), o.optString("descriptionId")))
                    }
                }
                add(
                    Weapon(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        type = item.optString("type"),
                        rarity = item.optInt("rarity"),
                        description = item.optString("description"),
                        descriptionId = item.optString("descriptionId"),
                        baseAtk = if (item.has("baseAtk") && !item.isNull("baseAtk")) item.optDouble("baseAtk") else null,
                        mainStat = item.optString("mainStat"),
                        mainStatValue = item.optString("mainStatValue"),
                        effectName = item.optString("effectName"),
                        refinements = refinements,
                        ascensionCosts = parseAscendCosts(item),
                        icon = item.optString("icon").ifBlank { null }
                    )
                )
            }
        }

        fun parseArtifacts(arr: JSONArray): List<Artifact> = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val pieces = buildList {
                    val p = item.optJSONArray("pieces") ?: JSONArray()
                    for (j in 0 until p.length()) {
                        val o = p.getJSONObject(j)
                        add(
                            ArtifactPiece(
                                slot = o.optString("slot"),
                                name = o.optString("name"),
                                description = o.optString("description"),
                                image = o.optString("image").ifBlank { null }
                            )
                        )
                    }
                }
                add(
                    Artifact(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        rarity = item.optInt("rarity", 5),
                        effect2Pc = item.optString("effect2Pc"),
                        effect2PcId = item.optString("effect2PcId"),
                        effect4Pc = item.optString("effect4Pc"),
                        effect4PcId = item.optString("effect4PcId"),
                        pieces = pieces
                    )
                )
            }
        }

        fun parseEnemies(arr: JSONArray): List<Enemy> = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                add(
                    Enemy(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        category = item.optString("category"),
                        element = item.optString("element", "None"),
                        region = item.optString("region", "Teyvat"),
                        description = item.optString("description"),
                        descriptionId = item.optString("descriptionId"),
                        drops = stringList(item, "drops"),
                        icon = item.optString("icon").ifBlank { null }
                    )
                )
            }
        }

        fun parseTeams(arr: JSONArray): List<Team> = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val core = item.optJSONArray("core") ?: JSONArray()
                val members = buildList { for (j in 0 until core.length()) add(core.optString(j)) }
                add(Team(item.optString("name"), members, item.optString("focus")))
            }
        }

        fun parseDomains(arr: JSONArray): List<Domain> = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                add(
                    Domain(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        type = item.optString("type"),
                        region = item.optString("region", "Teyvat"),
                        entrance = item.optString("entrance"),
                        description = item.optString("description"),
                        descriptionId = item.optString("descriptionId"),
                        recommendedLevel = if (item.has("recommendedLevel") && !item.isNull("recommendedLevel")) item.optInt("recommendedLevel") else null,
                        recommendedElements = stringList(item, "recommendedElements"),
                        monsters = stringList(item, "monsters"),
                        rewardItems = stringList(item, "rewardItems")
                    )
                )
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContextHolder.context = applicationContext
        val repository = DataRepository(applicationContext)
        setContent {
            App(repository, Store(this))
        }
    }
}

private val AppBackground = Color(0xFF080B16)
private val AppSurface = Color(0xFF12182A)
private val AppSurfaceVariant = Color(0xFF19223A)
private val AppPrimary = Color(0xFF9AA6FF)
private val AppSecondary = Color(0xFFE8C77B)

private fun rarityColors(rarity: Int): List<Color> = when (rarity) {
    5 -> listOf(Color(0xFFFFE3A3), Color(0xFFC08A34))
    4 -> listOf(Color(0xFFE3C2FF), Color(0xFF9856D6))
    else -> listOf(Color(0xFFC7D0F0), Color(0xFF6672A8))
}

private fun elementColor(element: String): Color = when (element.trim().lowercase()) {
    "pyro" -> Color(0xFFFF7A4D)
    "hydro" -> Color(0xFF4FC3F7)
    "cryo" -> Color(0xFF8CE8E4)
    "electro" -> Color(0xFFC77DFF)
    "anemo" -> Color(0xFF5FE0C6)
    "geo" -> Color(0xFFFFCB4D)
    "dendro" -> Color(0xFFA6E06B)
    else -> AppPrimary
}

private fun rarityBorder(rarity: Int) = Modifier.border(
    width = 1.5.dp,
    brush = Brush.linearGradient(rarityColors(rarity)),
    shape = RoundedCornerShape(14.dp)
)

@Composable
private fun App(repository: DataRepository, store: Store) {
    var db by remember { mutableStateOf<DB?>(null) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching {
            val characters = DB.parseCharacters(repository.loadArray("characters.json"))
            val weapons = DB.parseWeapons(repository.loadArray("weapons.json"))
            val artifacts = DB.parseArtifacts(repository.loadArray("artifacts.json"))
            val enemies = DB.parseEnemies(repository.loadArray("enemies.json"))
            val teams = DB.parseTeams(repository.loadArray("teams.json"))
            val domains = DB.parseDomains(repository.loadArray("domains.json"))
            DB(characters, weapons, artifacts, enemies, teams, domains)
        }.onSuccess { db = it }
            .onFailure { loadError = true }
    }

    var tab by remember { mutableStateOf(Tab.HOME) }
    var selectedCharacter by remember { mutableStateOf<Character?>(null) }
    var selectedWeapon by remember { mutableStateOf<Weapon?>(null) }
    var selectedArtifact by remember { mutableStateOf<Artifact?>(null) }
    var selectedEnemy by remember { mutableStateOf<Enemy?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var lang by remember { mutableStateOf(store.lang()) }

    // Everything - including the loading splash - renders inside MaterialTheme.
    // SplashScreen used to render before this wrapper existed, with no Surface
    // ancestor to resolve a default text color, so its title text fell back to
    // LocalContentColor's built-in black default and was invisible on the dark
    // background - same root cause as the earlier DetailScaffold bug.
    CompositionLocalProvider(LocalLang provides lang) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = AppBackground,
            surface = AppSurface,
            surfaceVariant = AppSurfaceVariant,
            primary = AppPrimary,
            secondary = AppSecondary
        )
    ) {
        val loadedDb = db
        if (loadedDb == null) {
            SplashScreen(error = loadError)
            return@MaterialTheme
        }
        // Detail pages render full-screen (replacing the whole Scaffold, bottom
        // nav included) instead of floating over it, so they read like a real
        // profile page rather than a popup.
        val character = selectedCharacter
        val weapon = selectedWeapon
        val artifact = selectedArtifact
        val enemy = selectedEnemy
        // Detail screens used to be AlertDialogs, which wire up the system
        // back gesture/button for free. As full-screen composables they don't,
        // so without this the back gesture falls through to the Activity and
        // exits the whole app instead of just closing the detail page.
        BackHandler(enabled = character != null || weapon != null || artifact != null || enemy != null) {
            if (character != null) refresh++
            selectedCharacter = null
            selectedWeapon = null
            selectedArtifact = null
            selectedEnemy = null
        }
        when {
            character != null -> CharacterDetail(character, store) {
                selectedCharacter = null
                refresh++
            }
            weapon != null -> WeaponDetail(weapon) { selectedWeapon = null }
            artifact != null -> ArtifactDetail(artifact) { selectedArtifact = null }
            enemy != null -> EnemyDetail(enemy) { selectedEnemy = null }
            else -> Scaffold(
                containerColor = AppBackground,
                bottomBar = {
                    NavigationBar(containerColor = Color(0xFF0C1120)) {
                        Tab.entries.forEach { currentTab ->
                            NavigationBarItem(
                                selected = tab == currentTab,
                                onClick = { tab = currentTab },
                                icon = { TabIcon(currentTab) },
                                label = { Text(currentTab.label(), fontSize = 9.sp) }
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (tab) {
                        Tab.HOME -> Home(
                            db = loadedDb,
                            store = store,
                            openCharacters = { tab = Tab.CHAR },
                            openWeapons = { tab = Tab.WEAPON },
                            openTeams = { tab = Tab.TEAM },
                            openTools = { tab = Tab.TOOLS },
                            onSelectCharacter = { selectedCharacter = it }
                        )
                        Tab.CHAR -> Characters(loadedDb, store) { selectedCharacter = it }
                        Tab.WEAPON -> Weapons(loadedDb) { selectedWeapon = it }
                        Tab.ARTIFACT -> Artifacts(loadedDb) { selectedArtifact = it }
                        Tab.TEAM -> Teams(loadedDb, store, refresh)
                        Tab.TOOLS -> Tools()
                        Tab.MORE -> More(
                            db = loadedDb,
                            store = store,
                            repository = repository,
                            openEnemy = { selectedEnemy = it },
                            onChange = { refresh++ },
                            lang = lang,
                            onLangChange = {
                                lang = it
                                store.setLang(it)
                            }
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun TabIcon(tab: Tab) {
    val icon = when (tab) {
        Tab.HOME -> Icons.Default.Home
        Tab.CHAR -> Icons.Default.Person
        Tab.WEAPON -> Icons.Default.Build
        Tab.ARTIFACT -> Icons.Default.AutoAwesome
        Tab.TEAM -> Icons.Default.Group
        Tab.TOOLS -> Icons.Default.Calculate
        Tab.MORE -> Icons.Default.MoreHoriz
    }
    Icon(icon, contentDescription = tab.label())
}

@Composable
private fun Header(title: String, sub: String = "") {
    Column(Modifier.padding(top = 18.dp, bottom = 11.dp)) {
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        if (sub.isNotEmpty()) {
            Text(sub, fontSize = 12.sp, color = Color(0xFF9DA9C7))
        }
    }
}

@Composable
private fun Home(
    db: DB,
    store: Store,
    openCharacters: () -> Unit,
    openWeapons: () -> Unit,
    openTeams: () -> Unit,
    openTools: () -> Unit,
    onSelectCharacter: (Character) -> Unit
) {
    val roster = store.get("roster")
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 15.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(6.dp)) }
        item { HomeHero(db, roster.size) }
        item { NewsAndBannerCard() }
        item { Text(localized("Quick Access", "Akses Cepat"), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickAction("Character", Icons.Default.Person, openCharacters, Modifier.weight(1f))
                QuickAction("Weapon", Icons.Default.Build, openWeapons, Modifier.weight(1f))
                QuickAction("Team", Icons.Default.Group, openTeams, Modifier.weight(1f))
                QuickAction("Calc", Icons.Default.Calculate, openTools, Modifier.weight(1f))
            }
        }
        item { Text(localized("My Roster", "Roster Saya"), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        items(db.characters.filter { roster.contains(it.id) }.take(10)) { character ->
            Compact(character) { onSelectCharacter(character) }
        }
        if (roster.isEmpty()) {
            item {
                Text(
                    "Belum ada karakter. Tambahkan dari Character Database.",
                    color = Color(0xFF8995B3)
                )
            }
        }
    }
}

@Composable
private fun NewsAndBannerCard() {
    val context = LocalContext.current
    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1530))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = AppSecondary)
                Spacer(Modifier.width(6.dp))
                Text("News & Banner", fontWeight = FontWeight.Bold, color = AppSecondary)
            }
            Text(
                "Nama banner nggak kita simpan di app (ganti tiap ~3 minggu dan genshin-db nggak nyimpen jadwal gacha), jadi ini langsung ke sumber yang selalu update sendiri.",
                fontSize = 10.sp,
                color = Color(0xFF9DA9C7)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                OpenUrlButton("🎴 Banner Sekarang", "https://gi.yatta.moe/en/banner/character")
                OpenUrlButton(
                    "🔮 Bocoran Banner Berikutnya",
                    "https://www.youtube.com/results?search_query=${Uri.encode("Genshin Impact next banner leak")}"
                )
                OpenUrlButton("📰 Berita Resmi", "https://genshin.hoyoverse.com/en/news")
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0x33FFFFFF))) {
        Column(Modifier.padding(9.dp)) {
            Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, fontSize = 9.sp, color = Color(0xFFE3E6F5))
        }
    }
}

@Composable
private fun HomeHero(db: DB, rosterSize: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(
                    listOf(Color(0xFF3A2C6B), Color(0xFF5C3A8E), Color(0xFF9856D6))
                )
            )
        ) {
            Box(
                Modifier.fillMaxWidth().background(
                    Brush.radialGradient(
                        listOf(Color(0x33FFE3A3), Color.Transparent),
                        radius = 420f
                    )
                )
            )
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(AppPrimary, AppSecondary))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✦", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF201436))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Genshin Insight", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Auto-updating companion · ~Dias~", fontSize = 10.sp, color = Color(0xFFE3E6F5))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stat("${db.characters.size}", "Characters", Modifier.weight(1f))
                    Stat("${db.weapons.size}", "Weapons", Modifier.weight(1f))
                    Stat("$rosterSize", "My Roster", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171F36))
    ) {
        Column(
            Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = title, tint = AppPrimary)
            Text(title, fontSize = 10.sp)
        }
    }
}

@Composable
private fun Compact(character: Character, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF11182B))
    ) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF252F51)),
                contentAlignment = Alignment.Center
            ) {
                if (character.icon != null) {
                    AsyncImage(
                        model = character.icon,
                        contentDescription = character.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(character.name.take(1), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(character.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${character.element} · ${character.weapon}",
                    fontSize = 10.sp,
                    color = Color(0xFF8F9BB9)
                )
            }
        }
    }
}

@Composable
private fun Characters(db: DB, store: Store, onSelect: (Character) -> Unit) {
    var query by remember { mutableStateOf("") }
    var element by remember { mutableStateOf("All") }
    var rarity by remember { mutableStateOf("All") }
    var mineOnly by remember { mutableStateOf(false) }
    val favorites = store.get("fav")
    val roster = store.get("roster")

    Column(Modifier.fillMaxSize().padding(horizontal = 13.dp)) {
        Header(localized("Character Database", "Database Karakter"), localized("Search · filter · roster · favorites", "Cari · filter · roster · favorit"))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(localized("Search character", "Cari karakter")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 6.dp).horizontalScroll(rememberScrollState())
        ) {
            listOf("All", "Pyro", "Hydro", "Cryo", "Electro", "Anemo", "Geo", "Dendro").forEach { value ->
                FilterChip(
                    selected = element == value,
                    onClick = { element = value },
                    label = { Text(filterLabel(value), fontSize = 9.sp) }
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            FilterChip(
                selected = mineOnly,
                onClick = { mineOnly = !mineOnly },
                label = { Text(localized("My Roster", "Roster Saya"), fontSize = 10.sp) }
            )
            listOf("All", "5", "4").forEach { value ->
                FilterChip(
                    selected = rarity == value,
                    onClick = { rarity = value },
                    label = { Text(if (value == "All") filterLabel(value) else "${value}★", fontSize = 10.sp) }
                )
            }
        }

        val filtered = db.characters.filter { character ->
            (query.isBlank() || character.name.contains(query, ignoreCase = true)) &&
                (element == "All" || character.element.equals(element, ignoreCase = true)) &&
                (rarity == "All" || character.rarity.toString() == rarity) &&
                (!mineOnly || roster.contains(character.id))
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(145.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            gridItems(filtered) { character ->
                val ring = rarityColors(character.rarity)
                Card(
                    modifier = Modifier.clickable { onSelect(character) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                Modifier.size(78.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(ring))
                                    .padding(2.5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1B2338)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (character.icon != null) {
                                    AsyncImage(
                                        model = character.icon,
                                        contentDescription = character.name,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(character.name.take(1), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ring.first())
                                }
                            }
                            Box(
                                Modifier.size(20.dp).clip(CircleShape)
                                    .background(elementColor(character.element))
                                    .border(1.5.dp, Color(0xFF141B2F), CircleShape)
                            )
                            if (favorites.contains(character.id)) {
                                Box(
                                    Modifier.align(Alignment.TopStart).size(18.dp).clip(CircleShape)
                                        .background(Color(0xFF141B2F)),
                                    contentAlignment = Alignment.Center
                                ) { Text("★", fontSize = 11.sp, color = AppSecondary) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(character.name, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 13.sp)
                        Text(
                            "${character.element} · ${character.weapon}",
                            fontSize = 9.sp,
                            color = Color(0xFF9DA9C7),
                            maxLines = 1
                        )
                        Text(character.region, fontSize = 9.sp, color = Color(0xFF77839F))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageBox(label: String, url: String?, height: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(Brush.linearGradient(listOf(Color(0xFF2A355B), Color(0xFF101527))))
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(label.take(1), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB9C4F7))
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = label,
                modifier = Modifier.fillMaxSize().padding(top = 5.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center
            )
        }
    }
}

@Composable
private fun OpenUrlButton(label: String, url: String) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }) { Text(label, fontSize = 11.sp) }
}

/**
 * Full-screen detail page (hero image + gradient scrim + title block, content
 * below) used for character/weapon/artifact/enemy detail. Replaces the old
 * small AlertDialog popups with a HoYoLAB-style profile page. The caller is
 * responsible for swapping this in for the whole Scaffold (see App()) so it
 * isn't obscured by the bottom nav bar.
 */
@Composable
private fun DetailScaffold(
    title: String,
    subtitle: String,
    heroImage: String?,
    accent: Color,
    close: () -> Unit,
    topActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    // Surface (unlike a plain Box+background) propagates a correct default
    // LocalContentColor to descendants, which is why AlertDialog-based detail
    // screens never needed explicit text colors but this full-screen version
    // did - any Text() here without an explicit color was rendering black.
    Surface(modifier = Modifier.fillMaxSize(), color = AppBackground, contentColor = Color(0xFFE7E9F5)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF0A0F1F)))
                    )
                )
                if (heroImage != null) {
                    AsyncImage(
                        model = heroImage,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent, AppBackground),
                            startY = 0.35f * 780f
                        )
                    )
                )
                Row(
                    Modifier.fillMaxWidth().padding(10.dp).align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = close,
                        modifier = Modifier.background(Color(0x66000000), CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { topActions() }
                }
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, fontSize = 12.sp, color = Color(0xFFE7E9F5))
                    }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun CharacterDetail(character: Character, store: Store, close: () -> Unit) {
    var section by remember { mutableStateOf("Overview") }
    var inRoster by remember { mutableStateOf(store.get("roster").contains(character.id)) }
    var favorite by remember { mutableStateOf(store.get("fav").contains(character.id)) }
    var note by remember { mutableStateOf(store.note(character.id)) }
    val accent = elementColor(character.element)

    DetailScaffold(
        title = character.name,
        subtitle = "${character.rarity}★ · ${character.element} · ${character.weapon} · ${character.region}",
        heroImage = character.card ?: character.splash ?: character.icon,
        accent = accent,
        close = close,
        topActions = {
            IconButton(
                onClick = {
                    favorite = !favorite
                    val values = store.get("fav").toMutableSet()
                    if (favorite) values.add(character.id) else values.remove(character.id)
                    store.set("fav", values)
                },
                modifier = Modifier.background(Color(0x66000000), CircleShape)
            ) {
                Icon(
                    if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (favorite) AppSecondary else Color.White
                )
            }
        }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf("Overview", "Talents", "Constellations", "Level Up", "Build").forEach { value ->
                FilterChip(
                    selected = section == value,
                    onClick = { section = value },
                    label = { Text(sectionLabel(value), fontSize = 9.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.35f))
                )
            }
        }
        RarityCard(character.rarity) {
            when (section) {
                "Overview" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (character.title.isNotBlank()) Text("\"${character.title}\"", color = Color(0xFFB9C4F7), fontWeight = FontWeight.SemiBold)
                    if (character.description.isNotBlank()) {
                        Text(localized(character.description, character.descriptionId), fontSize = 12.sp, color = Color(0xFFB6C1DA))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        OpenUrlButton("📖 Guide di KQM", character.guideUrl)
                        OpenUrlButton(
                            "▶ Cari di YouTube",
                            "https://www.youtube.com/results?search_query=${Uri.encode(character.name + " Genshin build guide")}"
                        )
                        if (character.fandomUrl != null) {
                            OpenUrlButton("📜 Lore Lengkap", character.fandomUrl)
                        }
                    }
                }
                "Talents" -> if (character.talents.isEmpty() && character.passives.isEmpty()) {
                    Missing("Normal Attack", "Elemental Skill", "Elemental Burst", "Passive 1–4")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        character.talents.forEach { InfoBlock(it.name, localized(it.description, it.descriptionId)) }
                        character.passives.forEach { InfoBlock(it.name, localized(it.description, it.descriptionId)) }
                    }
                }
                "Constellations" -> if (character.constellations.isEmpty()) {
                    Missing("Constellation C1–C6")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        character.constellations.forEach { InfoBlock("${it.level.uppercase()} · ${it.name}", localized(it.description, it.descriptionId)) }
                    }
                }
                "Level Up" -> Column {
                    LevelUpCalculator(character.ascensionCosts, accent)
                }
                "Build" -> Column {
                    Text(localized("Build Workspace", "Ruang Kerja Build"), fontWeight = FontWeight.Bold)
                    Text(
                        "Weapon / Artifact / Main Stat / Sub Stat / Rotation",
                        fontSize = 11.sp,
                        color = Color(0xFF9DA9C7)
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = {
                            note = it
                            store.note(character.id, it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(localized("Build notes", "Catatan build")) }
                    )
                }
            }
        }
        Button(
            onClick = {
                inRoster = !inRoster
                val values = store.get("roster").toMutableSet()
                if (inRoster) values.add(character.id) else values.remove(character.id)
                store.set("roster", values)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = accent)
        ) {
            Text(
                if (inRoster) localized("Remove from My Roster", "Hapus dari Roster") else localized("Add to My Roster", "Tambah ke Roster"),
                color = Color(0xFF0C1120),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RarityCard(rarity: Int, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().then(rarityBorder(rarity)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

/** Level-up material calculator shared by CharacterDetail and WeaponDetail. */
@Composable
private fun LevelUpCalculator(costs: Map<String, List<AscendCost>>, accent: Color) {
    if (costs.values.all { it.isEmpty() }) {
        Missing("Ascension material list")
        return
    }
    var fromIndex by remember { mutableIntStateOf(0) }
    var toIndex by remember { mutableIntStateOf(AscendLevelLabels.lastIndex) }

    Text("Dari level", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        AscendLevelLabels.dropLast(1).forEachIndexed { i, label ->
            FilterChip(
                selected = fromIndex == i,
                onClick = { fromIndex = i; if (toIndex <= i) toIndex = i + 1 },
                label = { Text(label, fontSize = 9.sp) }
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Text("Sampai level", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        AscendLevelLabels.drop(1).forEachIndexed { offset, label ->
            val i = offset + 1
            FilterChip(
                selected = toIndex == i,
                onClick = { if (i > fromIndex) toIndex = i },
                label = { Text(label, fontSize = 9.sp) }
            )
        }
    }
    Spacer(Modifier.height(10.dp))

    val totals = remember(fromIndex, toIndex, costs) {
        val agg = linkedMapOf<String, Pair<Int, String?>>()
        for (i in fromIndex until toIndex) {
            val key = AscendStageKeys.getOrNull(i) ?: continue
            costs[key]?.forEach { c ->
                val prev = agg[c.name]
                agg[c.name] = (((prev?.first ?: 0) + c.count) to (prev?.second ?: c.icon))
            }
        }
        agg
    }

    if (totals.isEmpty()) {
        Text("Nggak butuh material ascension di rentang level ini.", fontSize = 11.sp, color = Color(0xFF9DA9C7))
    } else {
        Text(
            "Total material ${AscendLevelLabels[fromIndex]} → ${AscendLevelLabels[toIndex]}:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        totals.forEach { (name, pair) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF252F51)),
                    contentAlignment = Alignment.Center
                ) {
                    if (pair.second != null) {
                        AsyncImage(model = pair.second, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(name.take(1), fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(name, modifier = Modifier.weight(1f), fontSize = 11.sp)
                Text("×${pair.first}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
            }
        }
    }
}

@Composable
private fun InfoBlock(title: String, body: String) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        if (body.isNotBlank()) {
            Text(body, fontSize = 11.sp, color = Color(0xFF9DA9C7))
        }
    }
}

@Composable
private fun Missing(vararg rows: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        rows.forEach { row ->
            Text(
                "• $row: menunggu update data berikutnya",
                fontSize = 11.sp,
                color = Color(0xFF9DA9C7)
            )
        }
    }
}

@Composable
private fun Weapons(db: DB, onSelect: (Weapon) -> Unit) {
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("All") }
    var rarity by remember { mutableStateOf("All") }

    Column(Modifier.fillMaxSize().padding(horizontal = 13.dp)) {
        Header(localized("Weapon Database", "Database Senjata"), localized("Search · class · rarity", "Cari · tipe · rarity"))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(localized("Search weapon", "Cari senjata")) }
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 6.dp).horizontalScroll(rememberScrollState())
        ) {
            listOf("All", "Sword", "Claymore", "Polearm", "Bow", "Catalyst").forEach { value ->
                FilterChip(
                    selected = type == value,
                    onClick = { type = value },
                    label = { Text(filterLabel(value), fontSize = 9.sp) }
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf("All", "5", "4").forEach { value ->
                FilterChip(
                    selected = rarity == value,
                    onClick = { rarity = value },
                    label = { Text(if (value == "All") filterLabel(value) else "${value}★", fontSize = 9.sp) }
                )
            }
        }

        val filtered = db.weapons.filter { weapon ->
            (query.isBlank() || weapon.name.contains(query, ignoreCase = true)) &&
                (type == "All" || weapon.type.equals(type, ignoreCase = true)) &&
                (rarity == "All" || weapon.rarity.toString() == rarity)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(145.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            gridItems(filtered) { weapon ->
                Card(
                    modifier = Modifier.clickable { onSelect(weapon) }.then(rarityBorder(weapon.rarity)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))
                ) {
                    Column {
                        ImageBox(weapon.name, weapon.icon, 140)
                        Column(Modifier.padding(8.dp)) {
                            Text(weapon.name, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(
                                "${weapon.type} · ${"★".repeat(weapon.rarity)}",
                                fontSize = 10.sp,
                                color = rarityColors(weapon.rarity).first()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeaponDetail(weapon: Weapon, close: () -> Unit) {
    val accent = rarityColors(weapon.rarity).first()
    DetailScaffold(
        title = weapon.name,
        subtitle = "${weapon.rarity}★ · ${weapon.type}",
        heroImage = weapon.icon,
        accent = accent,
        close = close
    ) {
        RarityCard(weapon.rarity) {
            if (weapon.baseAtk != null) {
                Text(localized("Base ATK", "ATK Dasar") + ": ${weapon.baseAtk.toInt()} · ${weapon.mainStat} ${weapon.mainStatValue}", fontSize = 12.sp, color = Color(0xFFB6C1DA))
            }
            if (weapon.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(localized(weapon.description, weapon.descriptionId), fontSize = 12.sp, color = Color(0xFFB6C1DA))
            }
        }
        RarityCard(weapon.rarity) {
            if (weapon.effectName.isNotBlank()) {
                Text(weapon.effectName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accent)
                Spacer(Modifier.height(6.dp))
            }
            if (weapon.refinements.isEmpty()) {
                Missing("Passive", "Refinement R1–R5")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    weapon.refinements.forEach { InfoBlock(it.refinement, localized(it.description, it.descriptionId)) }
                }
            }
        }
        RarityCard(weapon.rarity) {
            Text(localized("Level Up", "Naik Level"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accent)
            Spacer(Modifier.height(8.dp))
            LevelUpCalculator(weapon.ascensionCosts, accent)
        }
    }
}

@Composable
private fun Artifacts(db: DB, onSelect: (Artifact) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 13.dp)) {
        Header(localized("Artifact Database", "Database Artifact"), localized("Set effects · searchable", "Efek set · bisa dicari"))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(localized("Search artifact set", "Cari set artifact")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )
        Spacer(Modifier.height(8.dp))
        val filtered = db.artifacts.filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true) ||
                it.effect2Pc.contains(query, ignoreCase = true) || it.effect4Pc.contains(query, ignoreCase = true)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered) { artifact ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(artifact) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))
                ) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        val pieceImage = artifact.pieces.firstOrNull()?.image
                        if (pieceImage != null) {
                            AsyncImage(
                                model = pieceImage,
                                contentDescription = artifact.name,
                                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(artifact.name, fontWeight = FontWeight.Bold)
                            val summary = if (artifact.effect2Pc.isNotBlank()) localized(artifact.effect2Pc, artifact.effect2PcId) else localized(artifact.effect4Pc, artifact.effect4PcId)
                            Text(summary, fontSize = 11.sp, color = Color(0xFF9DA9C7), maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactDetail(artifact: Artifact, close: () -> Unit) {
    val accent = rarityColors(artifact.rarity).first()
    DetailScaffold(
        title = artifact.name,
        subtitle = "${artifact.rarity}★ Artifact Set",
        heroImage = artifact.pieces.firstOrNull()?.image,
        accent = accent,
        close = close
    ) {
        RarityCard(artifact.rarity) {
            if (artifact.effect2Pc.isNotBlank()) {
                Text(localized("2-Piece", "2-Bagian"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = accent)
                Text(localized(artifact.effect2Pc, artifact.effect2PcId), fontSize = 12.sp, color = Color(0xFFB6C1DA))
                Spacer(Modifier.height(8.dp))
            }
            if (artifact.effect4Pc.isNotBlank()) {
                Text(localized("4-Piece", "4-Bagian"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = accent)
                Text(localized(artifact.effect4Pc, artifact.effect4PcId), fontSize = 12.sp, color = Color(0xFFB6C1DA))
            }
        }
        if (artifact.pieces.isNotEmpty()) {
            RarityCard(artifact.rarity) {
                Text(localized("Pieces", "Bagian Artifact"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                artifact.pieces.forEach { piece ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (piece.image != null) {
                            AsyncImage(
                                model = piece.image,
                                contentDescription = piece.name,
                                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Column {
                            Text(piece.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(piece.slot.replaceFirstChar { it.uppercase() }, fontSize = 9.sp, color = Color(0xFF77839F))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Teams(db: DB, store: Store, refresh: Int) {
    var slots by remember {
        mutableStateOf(
            store.team().take(4).let { saved -> saved + List(4 - saved.size) { "" } }
        )
    }
    var query by remember { mutableStateOf("") }
    val roster = db.characters.filter { store.get("roster").contains(it.id) }
    val filtered = roster.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

    LazyColumn(
        Modifier.fillMaxSize().padding(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { Header(localized("Team Builder", "Penyusun Tim"), localized("4 slots · roster only", "4 slot · dari roster kamu")) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(localized("Search roster", "Cari roster")) }
            )
        }
        item {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                slots.forEachIndexed { index, id ->
                    Card(
                        modifier = Modifier.weight(1f).height(65.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF171F36))
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (id.isBlank()) "Slot ${index + 1}"
                                else db.characters.find { it.id == id }?.name?.take(9) ?: "?",
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { store.saveTeam(slots.filter(String::isNotBlank)) },
                    modifier = Modifier.weight(1f)
                ) { Text(localized("Save", "Simpan")) }
                OutlinedButton(
                    onClick = { slots = List(4) { "" } },
                    modifier = Modifier.weight(1f)
                ) { Text(localized("Clear", "Reset")) }
            }
        }
        item {
            val chosen = slots.mapNotNull { id -> db.characters.find { it.id == id } }
            if (chosen.isNotEmpty()) {
                val elements = chosen.groupingBy { it.element }.eachCount()
                val resonance = elements.filterValues { it >= 2 }.keys.joinToString().ifBlank { "No duplicate element yet" }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101A30))) {
                    Column(Modifier.padding(12.dp)) {
                        Text(localized("Team Snapshot", "Ringkasan Tim"), fontWeight = FontWeight.Bold)
                        Text("Members: ${chosen.size}/4", fontSize = 11.sp, color = Color(0xFFB6C1DA))
                        Text("Elements: ${elements.entries.joinToString { "${it.key} ×${it.value}" }}", fontSize = 11.sp, color = Color(0xFFB6C1DA))
                        Text("Potential resonance: $resonance", fontSize = 11.sp, color = Color(0xFFB6C1DA))
                        Text("This is a composition summary, not a power ranking.", fontSize = 9.sp, color = Color(0xFF8995B3))
                    }
                }
            }
        }
        item {
            Text(localized("Preset Team Ideas", "Ide Tim Preset"), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        }
        items(db.teams) { team ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11182B))
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(team.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(team.core.joinToString(" · "), fontSize = 10.sp, color = Color(0xFF9DA9C7))
                    Text(team.focus, fontSize = 9.sp, color = Color(0xFF77839F))
                }
            }
        }
        item {
            Text(
                "Roster",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        items(filtered) { character ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable {
                        val values = slots.toMutableList()
                        if (values.contains(character.id)) {
                            values[values.indexOf(character.id)] = ""
                        } else {
                            val index = values.indexOfFirst(String::isBlank)
                            if (index >= 0) values[index] = character.id
                        }
                        slots = values
                    },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(character.name, Modifier.weight(1f))
                    Text(character.element, fontSize = 10.sp, color = Color(0xFF9DA9C7))
                    if (slots.contains(character.id)) Text("✓", color = AppPrimary)
                }
            }
        }
        if (roster.isEmpty()) {
            item {
                Text(
                    "Roster kosong. Tambahkan karakter dari Character Database.",
                    color = Color(0xFF8995B3)
                )
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val ignoredRefresh = refresh
}

@Composable
private fun Tools() {
    var atk by remember { mutableStateOf("2500") }
    var multiplier by remember { mutableStateOf("300") }
    var bonus by remember { mutableStateOf("46.6") }
    var crit by remember { mutableStateOf("200") }
    var resistance by remember { mutableStateOf("10") }
    var reaction by remember { mutableStateOf("1.5") }
    var result by remember { mutableStateOf("—") }
    var levelFrom by remember { mutableStateOf("1") }
    var levelTo by remember { mutableStateOf("90") }
    var materialOwned by remember { mutableStateOf("0") }

    LazyColumn(
        Modifier.fillMaxSize().padding(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { Header(localized("Tools", "Kalkulator"), localized("Damage · material · enhancement", "Damage · material · upgrade")) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(localized("Damage Calculator", "Kalkulator Damage"), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Num("ATK", atk) { atk = it }
                    Num(localized("Talent %", "Talent %"), multiplier) { multiplier = it }
                    Num(localized("DMG Bonus %", "Bonus DMG %"), bonus) { bonus = it }
                    Num(localized("Crit DMG %", "Crit DMG %"), crit) { crit = it }
                    Num(localized("RES %", "RES %"), resistance) { resistance = it }
                    Num(localized("Reaction ×", "Reaksi ×"), reaction) { reaction = it }
                    Button(onClick = {
                        val a = atk.toDoubleOrNull() ?: 0.0
                        val m = (multiplier.toDoubleOrNull() ?: 0.0) / 100.0
                        val b = (bonus.toDoubleOrNull() ?: 0.0) / 100.0
                        val c = (crit.toDoubleOrNull() ?: 0.0) / 100.0
                        val r = resistance.toDoubleOrNull() ?: 0.0
                        val resistanceMultiplier = when {
                            r < 0 -> 1.0 - r / 200.0
                            r < 75 -> 1.0 - r / 100.0
                            else -> 1.0 / (1.0 + r / 100.0)
                        }
                        val reactionMultiplier = reaction.toDoubleOrNull() ?: 1.0
                        val damage = a * m * (1 + b) * (1 + c) * resistanceMultiplier * reactionMultiplier
                        result = String.format(Locale.US, "%,.0f", damage)
                    }) { Text(localized("Calculate", "Hitung")) }
                    Text(result, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    Text(
                        localized(
                            "Basic model; defense/buffs/ICD/reaction-specific formulas will be data-driven in the engine layer.",
                            "Model dasar; formula defense/buff/ICD/reaksi spesifik akan berbasis data di engine berikutnya."
                        ),
                        fontSize = 10.sp,
                        color = Color(0xFF8995B3)
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(localized("Material Calculator", "Kalkulator Material"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Num(localized("From level", "Dari level"), levelFrom) { levelFrom = it }
                    Num(localized("To level", "Sampai level"), levelTo) { levelTo = it }
                    Num(localized("Owned material", "Material dimiliki"), materialOwned) { materialOwned = it }
                    Text(localized("Planning shell: $levelFrom → $levelTo", "Rencana: $levelFrom → $levelTo"), color = Color(0xFFB6C1DA))
                    Text(
                        localized(
                            "Exact Mora/XP/material totals will be read from the full material dataset.",
                            "Total Mora/XP/material yang pasti akan diambil dari dataset material lengkap."
                        ),
                        fontSize = 10.sp,
                        color = Color(0xFF8995B3)
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171F36))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(localized("Readiness Score (Coming Next)", "Readiness Score (Segera Hadir)"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppSecondary)
                    Text(
                        "Fitur berikutnya: pilih tim dari roster kamu (level senjata/artifact opsional) lalu app menghitung perkiraan persentase kemungkinan clear untuk domain/konten tertentu, berdasarkan data talent resmi + rekomendasi elemen domain. Ini estimasi heuristik, bukan simulator combat 100% akurat.",
                        fontSize = 11.sp,
                        color = Color(0xFF9DA9C7)
                    )
                }
            }
        }
    }
}

@Composable
private fun Num(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EnemiesScreen(db: DB, onBack: () -> Unit, onSelect: (Enemy) -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    val categories = listOf("All") + db.enemies.map { it.category }.distinct()
    val filtered = db.enemies.filter {
        (query.isBlank() || it.name.contains(query, ignoreCase = true)) &&
            (category == "All" || it.category == category)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 13.dp)) {
        Row(Modifier.padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Column { Text("Monster & Boss Database", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("${db.enemies.size} entri · normal, elite, boss, weekly boss", fontSize = 10.sp, color = Color(0xFF9DA9C7)) }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(localized("Search monster/boss", "Cari monster/boss")) }
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp)) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 8.dp).horizontalScroll(rememberScrollState())
                ) {
                    categories.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(filterLabel(c), fontSize = 9.sp) })
                    }
                }
            }
            items(filtered) { enemy ->
                Card(
                    onClick = { onSelect(enemy) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF252F51)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (enemy.icon != null) {
                                AsyncImage(model = enemy.icon, contentDescription = enemy.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Text(enemy.name.take(1), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(enemy.name, fontWeight = FontWeight.Bold)
                            Text("${enemy.category} · ${enemy.element} · ${enemy.region}", fontSize = 11.sp, color = Color(0xFFB6C1DA))
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun EnemyDetail(enemy: Enemy, close: () -> Unit) {
    val accent = if (enemy.category.contains("Boss", ignoreCase = true)) Color(0xFFE05A5A) else elementColor(enemy.element)
    DetailScaffold(
        title = enemy.name,
        subtitle = "${enemy.category} · ${enemy.element} · ${enemy.region}",
        heroImage = enemy.icon,
        accent = accent,
        close = close
    ) {
        RarityCard(0) {
            if (enemy.description.isNotBlank()) {
                Text(localized(enemy.description, enemy.descriptionId), fontSize = 12.sp, color = Color(0xFFB6C1DA))
            }
            if (enemy.drops.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(localized("Drops", "Item Drop"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = accent)
                Text(enemy.drops.joinToString(" · "), fontSize = 11.sp, color = Color(0xFFB6C1DA))
            }
            if (enemy.description.isBlank() && enemy.drops.isEmpty()) {
                Missing("HP / level scaling", "Elemental & physical resistance", "Skills / mechanics", "Location / domain")
            }
        }
    }
}

@Composable
private fun DomainsScreen(db: DB, onBack: () -> Unit) {
    val context = LocalContext.current
    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("All") }
    var expanded by remember { mutableStateOf<String?>(null) }
    val regions = listOf("All") + db.domains.map { it.region }.filter { it.isNotBlank() }.distinct()
    val filtered = db.domains.filter {
        (query.isBlank() || it.name.contains(query, ignoreCase = true)) &&
            (region == "All" || it.region == region)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 13.dp)) {
        Row(Modifier.padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Column { Text("Peta & Domain", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("${db.domains.size} domain terdaftar", fontSize = 10.sp, color = Color(0xFF9DA9C7)) }
        }
        Button(
            onClick = { openUrl("https://act.hoyolab.com/ys/app/interactive-map/index.html") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("🗺 Buka Peta Interaktif Resmi (HoYoLAB)") }
        Text(
            "Kita nggak punya data koordinat lokasi, jadi buat peta visual langsung pakai peta resmi ini. Daftar di bawah dari data domain yang kita generate otomatis.",
            fontSize = 10.sp,
            color = Color(0xFF8995B3),
            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(localized("Search domain", "Cari domain")) }
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            regions.forEach { r ->
                FilterChip(selected = region == r, onClick = { region = r }, label = { Text(filterLabel(r), fontSize = 9.sp) })
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered) { domain ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        expanded = if (expanded == domain.id) null else domain.id
                    },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(domain.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "${domain.type} · ${domain.region}" + (domain.entrance.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            fontSize = 10.sp,
                            color = Color(0xFF9DA9C7)
                        )
                        if (domain.recommendedLevel != null || domain.recommendedElements.isNotEmpty()) {
                            Text(
                                "Rekomendasi: " +
                                    listOfNotNull(
                                        domain.recommendedLevel?.let { "Lv $it" },
                                        domain.recommendedElements.takeIf { it.isNotEmpty() }?.joinToString(),
                                    ).joinToString(" · "),
                                fontSize = 10.sp,
                                color = Color(0xFFB6C1DA)
                            )
                        }
                        if (expanded == domain.id) {
                            Spacer(Modifier.height(6.dp))
                            if (domain.description.isNotBlank()) {
                                Text(localized(domain.description, domain.descriptionId), fontSize = 11.sp, color = Color(0xFF9DA9C7))
                                Spacer(Modifier.height(4.dp))
                            }
                            if (domain.monsters.isNotEmpty()) {
                                Text("Musuh: ${domain.monsters.joinToString()}", fontSize = 10.sp, color = Color(0xFF9DA9C7))
                            }
                            if (domain.rewardItems.isNotEmpty()) {
                                Text("Reward: ${domain.rewardItems.joinToString()}", fontSize = 10.sp, color = Color(0xFF9DA9C7))
                            }
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "Belum ada data domain (buka app dengan internet dulu supaya data ter-fetch).",
                        color = Color(0xFF8995B3),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashScreen(error: Boolean = false) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A0F1F), Color(0xFF151D35)))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(92.dp).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(AppPrimary, AppSecondary))), contentAlignment = Alignment.Center) {
                Text("✦", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color(0xFF101527))
            }
            Spacer(Modifier.height(18.dp))
            Text("Genshin Insight", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("~Dias~", fontSize = 18.sp, color = AppSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (error) {
                Text("Gagal memuat data, memakai data bundled...", fontSize = 10.sp, color = Color(0xFFE8A67B))
            } else {
                CircularProgressIndicator(color = AppPrimary, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(8.dp))
                Text("Menyiapkan data terbaru…", fontSize = 10.sp, color = Color(0xFF8995B3))
            }
        }
    }
}

private val WallpaperCatalog = listOf(
    Wallpaper("Fantasy Night", "Mobile", "https://images.unsplash.com/photo-1534796636912-3b95b3ab5986?auto=format&fit=crop&w=1200&q=85"),
    Wallpaper("Mountain Dawn", "Mobile", "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&w=1200&q=85"),
    Wallpaper("Starry Sky", "Mobile", "https://images.unsplash.com/photo-1519681393784-d120267933ba?auto=format&fit=crop&w=1200&q=85"),
    Wallpaper("Fantasy Landscape", "Region", "https://images.unsplash.com/photo-1500534623283-312aade485b7?auto=format&fit=crop&w=1200&q=85")
)

@Composable
private fun WallpaperGallery(db: DB, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf("All") }
    var status by remember { mutableStateOf("") }
    val characterWallpapers = db.characters.filter { it.splash != null }.take(24)
        .map { Wallpaper(it.name, "Character", it.splash!!) }
    val allWallpapers = characterWallpapers + WallpaperCatalog
    val list = allWallpapers.filter { category == "All" || it.category == category }
    Column(Modifier.fillMaxSize().padding(horizontal = 13.dp)) {
        Row(Modifier.padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Column { Text(localized("Wallpaper Gallery", "Galeri Wallpaper"), fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("~Dias~ Collection", fontSize = 11.sp, color = AppSecondary) }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf("All", "Character", "Region", "Mobile").forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(filterLabel(c), fontSize = 9.sp) }) }
        }
        if (status.isNotEmpty()) Text(status, color = AppPrimary, fontSize = 10.sp, modifier = Modifier.padding(vertical = 5.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(155.dp), contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            gridItems(list) { wp ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))) {
                    Column {
                        AsyncImage(model = wp.url, contentDescription = wp.title, modifier = Modifier.fillMaxWidth().height(205.dp), contentScale = ContentScale.Crop, alignment = Alignment.Center)
                        Column(Modifier.padding(9.dp)) {
                            Text(wp.title, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(wp.category, fontSize = 9.sp, color = Color(0xFF8995B3))
                            Spacer(Modifier.height(5.dp))
                            Button(onClick = {
                                status = "Menyiapkan wallpaper…"
                                scope.launch {
                                    val ok = setWallpaperFromUrl(wp.url)
                                    status = if (ok) "Wallpaper berhasil dipasang: ${wp.title}" else "Gagal memasang. Pastikan internet aktif dan gambar tersedia."
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text(localized("Set Wallpaper", "Pasang Wallpaper"), fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun setWallpaperFromUrl(url: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.connect()
        if (connection.responseCode !in 200..299) return@withContext false
        val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) } ?: return@withContext false
        WallpaperManager.getInstance(AppContextHolder.context).setBitmap(bitmap)
        true
    } catch (_: Exception) { false }
}

private object AppContextHolder { lateinit var context: Context }

private data class CreatorLink(val label: String, val query: String)

private val TipTopics = listOf(
    CreatorLink("Tips Spiral Abyss", "Genshin Impact tips spiral abyss terbaru"),
    CreatorLink("Tips Farming Artifact", "Genshin Impact cara farming artifact efisien"),
    CreatorLink("Tim Awal untuk Pemain Baru", "Genshin Impact tim rekomendasi pemain baru"),
    CreatorLink("Build Karakter Terbaik", "Genshin Impact build karakter terbaik"),
    CreatorLink("Strategi Gacha", "Genshin Impact strategi gacha")
)

// genshin-db doesn't have quest/puzzle walkthrough data at all (only game
// stats), and quest names/steps are the kind of thing that's easy to
// misremember or which goes stale after a patch. Rather than hand-writing
// steps from memory and risking wrong info, these deep-link to a live search
// so results always reflect the current patch - covering puzzle categories
// that stay relevant across regions/updates instead of one-off quest names.
private val QuestTopics = listOf(
    CreatorLink("Quest Tersulit Patch Terbaru", "Genshin Impact quest paling susah patch terbaru walkthrough"),
    CreatorLink("Puzzle Aranara (Sumeru/Vanarana)", "Genshin Impact Aranara puzzle walkthrough"),
    CreatorLink("Puzzle Electroculus & Seelie", "Genshin Impact electro seelie puzzle guide"),
    CreatorLink("World Quest yang Bikin Bingung", "Genshin Impact world quest confusing walkthrough guide"),
    CreatorLink("Puzzle Domain / Mekanisme Tersembunyi", "Genshin Impact hidden domain puzzle mechanism guide")
)

@Composable
private fun CommunityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 13.dp)) {
        Row(Modifier.padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Column { Text("Community & Guides", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Creator, tips & sumber terpercaya", fontSize = 11.sp, color = AppSecondary) }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171F36))) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = AppSecondary)
                            Spacer(Modifier.width(6.dp))
                            Text(localized("Featured Creator", "Creator Pilihan"), fontWeight = FontWeight.Bold, color = AppSecondary)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Kokobear", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Content creator Genshin Impact berbahasa Indonesia — guide Spiral Abyss, strategi gacha, dan tips harian. @KokobearGaming",
                            fontSize = 11.sp,
                            color = Color(0xFFB6C1DA)
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { openUrl("https://www.youtube.com/@KokobearGaming") }, modifier = Modifier.fillMaxWidth()) {
                            Text("Buka Channel YouTube")
                        }
                    }
                }
            }
            item { Text("Tips & Trik", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
            items(TipTopics) { topic ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        openUrl("https://www.youtube.com/results?search_query=${Uri.encode(topic.query)}")
                    },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))
                ) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AppPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(topic.label, modifier = Modifier.weight(1f), fontSize = 12.sp)
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
            item { Text("Quest & Puzzle yang Bikin Struggle", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
            item {
                Text(
                    "Genshin nggak punya data quest resmi yang bisa ditarik otomatis, jadi ini nyari panduan terbaru langsung (bukan teks tetap yang bisa basi tiap patch).",
                    fontSize = 10.sp,
                    color = Color(0xFF8995B3)
                )
            }
            items(QuestTopics) { topic ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        openUrl("https://www.youtube.com/results?search_query=${Uri.encode(topic.query)}")
                    },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))
                ) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = AppPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(topic.label, modifier = Modifier.weight(1f), fontSize = 12.sp)
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
            item { Text("Sumber & Referensi", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { openUrl("https://keqingmains.com") },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))
                ) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = AppPrimary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("KQM · Theorycrafting Hub", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Guide karakter & tim paling detail, dipakai juga di halaman Character Detail", fontSize = 10.sp, color = Color(0xFF9DA9C7))
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { openUrl("https://genshin-impact.fandom.com") },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))
                ) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Public, contentDescription = null, tint = AppPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Genshin Impact Fandom Wiki", modifier = Modifier.weight(1f), fontSize = 12.sp)
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { openUrl("https://www.hoyolab.com") },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))
                ) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Forum, contentDescription = null, tint = AppPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(localized("HoYoLAB Community", "Komunitas HoYoLAB"), modifier = Modifier.weight(1f), fontSize = 12.sp)
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun More(
    db: DB,
    store: Store,
    repository: DataRepository,
    openEnemy: (Enemy) -> Unit,
    onChange: () -> Unit,
    lang: String,
    onLangChange: (String) -> Unit
) {
    var message by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf("main") }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = screen != "main") { screen = "main" }

    when (screen) {
        "wallpaper" -> { WallpaperGallery(db) { screen = "main" }; return }
        "community" -> { CommunityScreen { screen = "main" }; return }
        "enemies" -> { EnemiesScreen(db, onBack = { screen = "main" }, onSelect = openEnemy); return }
        "domains" -> { DomainsScreen(db) { screen = "main" }; return }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { Header(localized("More", "Lainnya"), localized("V6 · database · storage · updates", "V6 · database · penyimpanan · update")) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(localized("Local Database", "Database Lokal"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(localized(
                        "${db.characters.size} characters · ${db.weapons.size} weapons · ${db.artifacts.size} artifacts · ${db.enemies.size} enemies",
                        "${db.characters.size} karakter · ${db.weapons.size} senjata · ${db.artifacts.size} artifact · ${db.enemies.size} musuh"
                    ))
                    Text(localized(
                        "Roster ${store.get("roster").size} · Favorites ${store.get("fav").size}",
                        "Roster ${store.get("roster").size} · Favorit ${store.get("fav").size}"
                    ))
                    Text("Sumber data: ${repository.lastStatus.source}" + (repository.lastStatus.generatedAt?.let { " · diambil $it" } ?: ""), fontSize = 10.sp, color = Color(0xFF8995B3))
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bahasa / Language", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Ganti bahasa deskripsi karakter, senjata, artifact, dan musuh.",
                        fontSize = 10.sp,
                        color = Color(0xFF9DA9C7)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = lang == "id",
                            onClick = { onLangChange("id") },
                            label = { Text("Bahasa Indonesia") }
                        )
                        FilterChip(
                            selected = lang == "en",
                            onClick = { onLangChange("en") },
                            label = { Text("English") }
                        )
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text("Jelajahi", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        item {
            FeatureRow(
                localized("Monster & Boss Database", "Database Monster & Boss"),
                localized("${db.enemies.size} enemies · description & drops", "${db.enemies.size} musuh · deskripsi & drop item"),
                Icons.Default.Whatshot
            ) { screen = "enemies" }
        }
        item {
            FeatureRow(
                localized("Map & Domains", "Peta & Domain"),
                localized("${db.domains.size} domains · official interactive map", "${db.domains.size} domain · peta interaktif resmi"),
                Icons.Default.Map
            ) { screen = "domains" }
        }
        item {
            FeatureRow(
                localized("Community & Guides", "Community & Guides"),
                localized("Creators, tips videos, KQM, wiki", "Creator, tips video, KQM, wiki"),
                Icons.Default.Groups
            ) { screen = "community" }
        }
        item {
            FeatureRow(
                localized("Wallpaper Gallery", "Galeri Wallpaper"),
                localized("Pick a wallpaper and set it on your phone", "Pilih wallpaper dan set ke layar HP"),
                Icons.Default.Wallpaper
            ) { screen = "wallpaper" }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text(localized("Data Updates", "Update Data"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Data diambil otomatis dari repo GitHub (auto-refresh harian via GitHub Actions). Tekan tombol di bawah untuk paksa ambil versi terbaru sekarang.",
                fontSize = 11.sp,
                color = Color(0xFF8995B3)
            )
            Button(onClick = {
                repository.clearCache()
                message = "Cache dibersihkan. Buka ulang app untuk mengambil data terbaru."
                onChange()
            }) { Text(localized("Refresh Data", "Segarkan Data")) }
            if (message.isNotEmpty()) {
                Text(message, color = AppPrimary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun FeatureRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF18213A))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AppSecondary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 11.sp, color = Color(0xFFB6C1DA))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
