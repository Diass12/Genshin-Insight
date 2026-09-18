package com.genshincompanion.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.app.WallpaperManager
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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

private data class TalentInfo(val name: String, val description: String)
private data class ConstellationInfo(val level: String, val name: String, val description: String)
private data class RefinementInfo(val refinement: String, val description: String)
private data class ArtifactPiece(val slot: String, val name: String, val description: String, val image: String?)

private data class Character(
    val id: String,
    val name: String,
    val title: String,
    val description: String,
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
    val baseAtk: Double?,
    val mainStat: String,
    val mainStatValue: String,
    val effectName: String,
    val refinements: List<RefinementInfo>,
    val icon: String?
)

private data class Artifact(
    val id: String,
    val name: String,
    val rarity: Int,
    val effect2Pc: String,
    val effect4Pc: String,
    val pieces: List<ArtifactPiece>
)

private data class Enemy(
    val id: String,
    val name: String,
    val category: String,
    val element: String,
    val region: String,
    val description: String,
    val drops: List<String>,
    val icon: String?
)

private data class Team(val name: String, val core: List<String>, val focus: String)
private data class Wallpaper(val title: String, val category: String, val url: String)

private enum class Tab(val label: String) {
    HOME("Home"), CHAR("Char"), WEAPON("Weapon"), ARTIFACT("Artifact"), TEAM("Team"), TOOLS("Tools"), MORE("More")
}

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
}

private data class DB(
    val characters: List<Character>,
    val weapons: List<Weapon>,
    val artifacts: List<Artifact>,
    val enemies: List<Enemy>,
    val teams: List<Team>
) {
    companion object {
        private fun stringList(obj: JSONObject, key: String): List<String> {
            val arr = obj.optJSONArray(key) ?: JSONArray()
            return buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }
        }

        fun parseCharacters(arr: JSONArray): List<Character> = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val talents = buildList {
                    val t = item.optJSONArray("talents") ?: JSONArray()
                    for (j in 0 until t.length()) {
                        val o = t.getJSONObject(j)
                        add(TalentInfo(o.optString("name"), o.optString("description")))
                    }
                }
                val passives = buildList {
                    val t = item.optJSONArray("passives") ?: JSONArray()
                    for (j in 0 until t.length()) {
                        val o = t.getJSONObject(j)
                        add(TalentInfo(o.optString("name"), o.optString("description")))
                    }
                }
                val constellations = buildList {
                    val t = item.optJSONArray("constellations") ?: JSONArray()
                    for (j in 0 until t.length()) {
                        val o = t.getJSONObject(j)
                        add(ConstellationInfo(o.optString("level"), o.optString("name"), o.optString("description")))
                    }
                }
                add(
                    Character(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        title = item.optString("title"),
                        description = item.optString("description"),
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
                        add(RefinementInfo(o.optString("refinement"), o.optString("description")))
                    }
                }
                add(
                    Weapon(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        type = item.optString("type"),
                        rarity = item.optInt("rarity"),
                        description = item.optString("description"),
                        baseAtk = if (item.has("baseAtk") && !item.isNull("baseAtk")) item.optDouble("baseAtk") else null,
                        mainStat = item.optString("mainStat"),
                        mainStatValue = item.optString("mainStatValue"),
                        effectName = item.optString("effectName"),
                        refinements = refinements,
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
                        effect4Pc = item.optString("effect4Pc"),
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
            DB(characters, weapons, artifacts, enemies, teams)
        }.onSuccess { db = it }
            .onFailure { loadError = true }
    }

    val loadedDb = db
    if (loadedDb == null) {
        SplashScreen(error = loadError)
        return
    }

    var tab by remember { mutableStateOf(Tab.HOME) }
    var selectedCharacter by remember { mutableStateOf<Character?>(null) }
    var selectedWeapon by remember { mutableStateOf<Weapon?>(null) }
    var selectedArtifact by remember { mutableStateOf<Artifact?>(null) }
    var selectedEnemy by remember { mutableStateOf<Enemy?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = AppBackground,
            surface = AppSurface,
            surfaceVariant = AppSurfaceVariant,
            primary = AppPrimary,
            secondary = AppSecondary
        )
    ) {
        Scaffold(
            containerColor = AppBackground,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0C1120)) {
                    Tab.entries.forEach { currentTab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
                            onClick = { tab = currentTab },
                            icon = { TabIcon(currentTab) },
                            label = { Text(currentTab.label, fontSize = 9.sp) }
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
                        openTools = { tab = Tab.TOOLS }
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
                        onChange = { refresh++ }
                    )
                }
            }

            selectedCharacter?.let { character ->
                CharacterDetail(character, store) {
                    selectedCharacter = null
                    refresh++
                }
            }
            selectedWeapon?.let { weapon ->
                WeaponDetail(weapon) { selectedWeapon = null }
            }
            selectedArtifact?.let { artifact ->
                ArtifactDetail(artifact) { selectedArtifact = null }
            }
            selectedEnemy?.let { enemy ->
                EnemyDetail(enemy) { selectedEnemy = null }
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
    Icon(icon, contentDescription = tab.label)
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
    openTools: () -> Unit
) {
    val roster = store.get("roster")
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 15.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Header("✦ Genshin Insight", "V6 Native Android · Auto-updating data") }
        item { NewsAndBannerCard() }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151D35))) {
                Column(Modifier.padding(17.dp)) {
                    Text("Your Genshin Toolkit", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Database karakter/senjata/artifact/musuh terupdate otomatis dari repo.",
                        color = Color(0xFFADB8D2)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Stat("${db.characters.size}", "Characters", Modifier.weight(1f))
                        Stat("${db.weapons.size}", "Weapons", Modifier.weight(1f))
                        Stat("${roster.size}", "My Roster", Modifier.weight(1f))
                    }
                }
            }
        }
        item { Text("Quick Access", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickAction("Character", Icons.Default.Person, openCharacters, Modifier.weight(1f))
                QuickAction("Weapon", Icons.Default.Build, openWeapons, Modifier.weight(1f))
                QuickAction("Team", Icons.Default.Group, openTeams, Modifier.weight(1f))
                QuickAction("Calc", Icons.Default.Calculate, openTools, Modifier.weight(1f))
            }
        }
        item { Text("My Roster", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        items(db.characters.filter { roster.contains(it.id) }.take(10)) { character ->
            Compact(character)
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
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1426))) {
        Column(Modifier.padding(9.dp)) {
            Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 9.sp, color = Color(0xFF8995B3))
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
private fun Compact(character: Character) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF11182B))) {
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
        Header("Character Database", "Search · filter · roster · favorites")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search character") },
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
                    label = { Text(value, fontSize = 9.sp) }
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
                label = { Text("My Roster", fontSize = 10.sp) }
            )
            listOf("All", "5", "4").forEach { value ->
                FilterChip(
                    selected = rarity == value,
                    onClick = { rarity = value },
                    label = { Text("${value}★", fontSize = 10.sp) }
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
                Card(
                    modifier = Modifier.clickable { onSelect(character) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))
                ) {
                    Column {
                        ImageBox(label = character.name, url = character.icon, height = 155)
                        Column(Modifier.padding(8.dp)) {
                            Row {
                                Text(
                                    character.name,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                if (favorites.contains(character.id)) {
                                    Text("★", color = AppSecondary)
                                }
                            }
                            Text(
                                "${character.element} · ${character.weapon}",
                                fontSize = 10.sp,
                                color = Color(0xFF9DA9C7)
                            )
                            Text(character.region, fontSize = 9.sp, color = Color(0xFF77839F))
                        }
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

@Composable
private fun CharacterDetail(character: Character, store: Store, close: () -> Unit) {
    var section by remember { mutableStateOf("Overview") }
    var inRoster by remember { mutableStateOf(store.get("roster").contains(character.id)) }
    var favorite by remember { mutableStateOf(store.get("fav").contains(character.id)) }
    var note by remember { mutableStateOf(store.note(character.id)) }

    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = close) { Text("Close") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(character.name, Modifier.weight(1f))
                IconButton(onClick = {
                    favorite = !favorite
                    val values = store.get("fav").toMutableSet()
                    if (favorite) values.add(character.id) else values.remove(character.id)
                    store.set("fav", values)
                }) {
                    Icon(
                        if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite"
                    )
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ImageBox(character.name, character.card ?: character.icon, 170)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("Overview", "Talents", "Constellations", "Build").forEach { value ->
                        FilterChip(
                            selected = section == value,
                            onClick = { section = value },
                            label = { Text(value, fontSize = 9.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                when (section) {
                    "Overview" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${character.rarity}★ · ${character.element} · ${character.weapon}")
                        Text("Region: ${character.region}")
                        if (character.title.isNotBlank()) Text("\"${character.title}\"", color = Color(0xFFB9C4F7))
                        if (character.description.isNotBlank()) {
                            Text(character.description, fontSize = 11.sp, color = Color(0xFF9DA9C7))
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
                            character.talents.forEach { InfoBlock(it.name, it.description) }
                            character.passives.forEach { InfoBlock(it.name, it.description) }
                        }
                    }
                    "Constellations" -> if (character.constellations.isEmpty()) {
                        Missing("Constellation C1–C6")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            character.constellations.forEach { InfoBlock("${it.level.uppercase()} · ${it.name}", it.description) }
                        }
                    }
                    "Build" -> Column {
                        Text("Build Workspace", fontWeight = FontWeight.Bold)
                        Text(
                            "Weapon / Artifact / Main Stat / Sub Stat / Rotation",
                            fontSize = 11.sp,
                            color = Color(0xFF9DA9C7)
                        )
                        OutlinedTextField(
                            value = note,
                            onValueChange = {
                                note = it
                                store.note(character.id, it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Build notes") }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    inRoster = !inRoster
                    val values = store.get("roster").toMutableSet()
                    if (inRoster) values.add(character.id) else values.remove(character.id)
                    store.set("roster", values)
                }) {
                    Text(if (inRoster) "Remove from My Roster" else "Add to My Roster")
                }
            }
        }
    )
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
        Header("Weapon Database", "Search · class · rarity")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search weapon") }
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 6.dp).horizontalScroll(rememberScrollState())
        ) {
            listOf("All", "Sword", "Claymore", "Polearm", "Bow", "Catalyst").forEach { value ->
                FilterChip(
                    selected = type == value,
                    onClick = { type = value },
                    label = { Text(value, fontSize = 9.sp) }
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
                    label = { Text("${value}★", fontSize = 9.sp) }
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
                    modifier = Modifier.clickable { onSelect(weapon) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))
                ) {
                    Column {
                        ImageBox(weapon.name, weapon.icon, 140)
                        Column(Modifier.padding(8.dp)) {
                            Text(weapon.name, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(
                                "${weapon.type} · ${"★".repeat(weapon.rarity)}",
                                fontSize = 10.sp,
                                color = Color(0xFF9DA9C7)
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
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = close) { Text("Close") } },
        title = { Text(weapon.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ImageBox(weapon.name, weapon.icon, 170)
                Text("${weapon.rarity}★ · ${weapon.type}")
                if (weapon.baseAtk != null) {
                    Text("Base ATK: ${weapon.baseAtk.toInt()} · ${weapon.mainStat} ${weapon.mainStatValue}", fontSize = 11.sp, color = Color(0xFF9DA9C7))
                }
                if (weapon.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(weapon.description, fontSize = 11.sp, color = Color(0xFF9DA9C7))
                }
                Spacer(Modifier.height(8.dp))
                if (weapon.effectName.isNotBlank()) {
                    Text(weapon.effectName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                if (weapon.refinements.isEmpty()) {
                    Missing("Passive", "Refinement R1–R5")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        weapon.refinements.forEach { InfoBlock(it.refinement, it.description) }
                    }
                }
            }
        }
    )
}

@Composable
private fun Artifacts(db: DB, onSelect: (Artifact) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 13.dp)) {
        Header("Artifact Database", "Set effects · searchable")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search artifact set") },
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
                            val summary = if (artifact.effect2Pc.isNotBlank()) artifact.effect2Pc else artifact.effect4Pc
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
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = close) { Text("Close") } },
        title = { Text(artifact.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (artifact.effect2Pc.isNotBlank()) {
                    Text("2-Piece", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(artifact.effect2Pc, fontSize = 11.sp, color = Color(0xFF9DA9C7))
                    Spacer(Modifier.height(6.dp))
                }
                if (artifact.effect4Pc.isNotBlank()) {
                    Text("4-Piece", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(artifact.effect4Pc, fontSize = 11.sp, color = Color(0xFF9DA9C7))
                }
                if (artifact.pieces.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Pieces", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    artifact.pieces.forEach { piece ->
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (piece.image != null) {
                                AsyncImage(
                                    model = piece.image,
                                    contentDescription = piece.name,
                                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)),
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
    )
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
        item { Header("Team Builder", "4 slots · roster only") }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search roster") }
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
                ) { Text("Save") }
                OutlinedButton(
                    onClick = { slots = List(4) { "" } },
                    modifier = Modifier.weight(1f)
                ) { Text("Clear") }
            }
        }
        item {
            val chosen = slots.mapNotNull { id -> db.characters.find { it.id == id } }
            if (chosen.isNotEmpty()) {
                val elements = chosen.groupingBy { it.element }.eachCount()
                val resonance = elements.filterValues { it >= 2 }.keys.joinToString().ifBlank { "No duplicate element yet" }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101A30))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Team Snapshot", fontWeight = FontWeight.Bold)
                        Text("Members: ${chosen.size}/4", fontSize = 11.sp, color = Color(0xFFB6C1DA))
                        Text("Elements: ${elements.entries.joinToString { "${it.key} ×${it.value}" }}", fontSize = 11.sp, color = Color(0xFFB6C1DA))
                        Text("Potential resonance: $resonance", fontSize = 11.sp, color = Color(0xFFB6C1DA))
                        Text("This is a composition summary, not a power ranking.", fontSize = 9.sp, color = Color(0xFF8995B3))
                    }
                }
            }
        }
        item {
            Text("Preset Team Ideas", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
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
        item { Header("Tools", "Damage · material · enhancement") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text("Damage Calculator", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Num("ATK", atk) { atk = it }
                    Num("Talent %", multiplier) { multiplier = it }
                    Num("DMG Bonus %", bonus) { bonus = it }
                    Num("Crit DMG %", crit) { crit = it }
                    Num("RES %", resistance) { resistance = it }
                    Num("Reaction ×", reaction) { reaction = it }
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
                    }) { Text("Calculate") }
                    Text(result, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Basic model; defense/buffs/ICD/reaction-specific formulas will be data-driven in the engine layer.",
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
                    Text("Material Calculator", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Num("From level", levelFrom) { levelFrom = it }
                    Num("To level", levelTo) { levelTo = it }
                    Num("Owned material", materialOwned) { materialOwned = it }
                    Text("Planning shell: $levelFrom → $levelTo", color = Color(0xFFB6C1DA))
                    Text(
                        "Exact Mora/XP/material totals will be read from the full material dataset.",
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
                    Text("Readiness Score (Coming Next)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppSecondary)
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
            label = { Text("Search monster/boss") }
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp)) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 8.dp).horizontalScroll(rememberScrollState())
                ) {
                    categories.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c, fontSize = 9.sp) })
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
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = close) { Text("Close") } },
        title = { Text(enemy.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (enemy.icon != null) {
                    ImageBox(enemy.name, enemy.icon, 130)
                }
                Text("${enemy.category} · ${enemy.element}", fontWeight = FontWeight.Bold)
                Text("Region: ${enemy.region}")
                if (enemy.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(enemy.description, fontSize = 11.sp, color = Color(0xFF9DA9C7))
                }
                if (enemy.drops.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Drops", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(enemy.drops.joinToString(" · "), fontSize = 11.sp, color = Color(0xFF9DA9C7))
                }
                if (enemy.description.isBlank() && enemy.drops.isEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Missing("HP / level scaling", "Elemental & physical resistance", "Skills / mechanics", "Location / domain")
                }
            }
        }
    )
}

@Composable
private fun SplashScreen(error: Boolean = false) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A0F1F), Color(0xFF151D35)))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(92.dp).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(AppPrimary, AppSecondary))), contentAlignment = Alignment.Center) {
                Text("✦", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color(0xFF101527))
            }
            Spacer(Modifier.height(18.dp))
            Text("Genshin Insight", fontSize = 25.sp, fontWeight = FontWeight.Bold)
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
            Column { Text("Wallpaper Gallery", fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("~Dias~ Collection", fontSize = 11.sp, color = AppSecondary) }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf("All", "Character", "Region", "Mobile").forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c, fontSize = 9.sp) }) }
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
                            }, modifier = Modifier.fillMaxWidth()) { Text("Set Wallpaper", fontSize = 10.sp) }
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
                            Text("Featured Creator", fontWeight = FontWeight.Bold, color = AppSecondary)
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
                        Text("HoYoLAB Community", modifier = Modifier.weight(1f), fontSize = 12.sp)
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
    onChange: () -> Unit
) {
    var message by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf("main") }
    val scope = rememberCoroutineScope()

    when (screen) {
        "wallpaper" -> { WallpaperGallery(db) { screen = "main" }; return }
        "community" -> { CommunityScreen { screen = "main" }; return }
        "enemies" -> { EnemiesScreen(db, onBack = { screen = "main" }, onSelect = openEnemy); return }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { Header("More", "V6 · database · storage · updates") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2F))) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Local Database", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${db.characters.size} characters · ${db.weapons.size} weapons · ${db.artifacts.size} artifacts · ${db.enemies.size} enemies")
                    Text("Roster ${store.get("roster").size} · Favorites ${store.get("fav").size}")
                    Text("Sumber data: ${repository.lastStatus.source}" + (repository.lastStatus.generatedAt?.let { " · diambil $it" } ?: ""), fontSize = 10.sp, color = Color(0xFF8995B3))
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text("Jelajahi", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        item {
            FeatureRow("Monster & Boss Database", "${db.enemies.size} musuh · deskripsi & drop item", Icons.Default.Whatshot) { screen = "enemies" }
        }
        item {
            FeatureRow("Community & Guides", "Creator, tips video, KQM, wiki", Icons.Default.Groups) { screen = "community" }
        }
        item {
            FeatureRow("Wallpaper Gallery", "Pilih wallpaper dan set ke layar HP", Icons.Default.Wallpaper) { screen = "wallpaper" }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text("Data Updates", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Data diambil otomatis dari repo GitHub (auto-refresh harian via GitHub Actions). Tekan tombol di bawah untuk paksa ambil versi terbaru sekarang.",
                fontSize = 11.sp,
                color = Color(0xFF8995B3)
            )
            Button(onClick = {
                repository.clearCache()
                message = "Cache dibersihkan. Buka ulang app untuk mengambil data terbaru."
                onChange()
            }) { Text("Refresh Data") }
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
