package com.genshincompanion.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Base of the GitHub repo that hosts the auto-updated game data package.
 * The repo's .github/workflows/update-data.yml refreshes these files daily
 * (and on every push to scripts/) from the genshin-db source.
 */
private const val REMOTE_BASE =
    "https://raw.githubusercontent.com/Diass12/Genshin-Insight/main/data"

data class DataStatus(val source: String, val generatedAt: String?)

class DataRepository(private val context: Context) {
    private val cacheDir = File(context.filesDir, "data_cache").apply { mkdirs() }
    var lastStatus: DataStatus = DataStatus("bundled", null)
        private set

    suspend fun loadArray(fileName: String): JSONArray = withContext(Dispatchers.IO) {
        fetchRemote(fileName)?.let { text ->
            val parsed = runCatching { JSONArray(text) }.getOrNull()
            if (parsed != null && parsed.length() > 0) {
                runCatching { File(cacheDir, fileName).writeText(text) }
                lastStatus = DataStatus("remote", nowIso())
                return@withContext parsed
            }
        }

        val cached = File(cacheDir, fileName).takeIf { it.exists() }?.readText()
        if (cached != null) {
            runCatching { JSONArray(cached) }.getOrNull()?.let {
                lastStatus = DataStatus("cache", null)
                return@withContext it
            }
        }

        val bundled = runCatching {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        }.getOrNull()
        if (bundled != null) {
            runCatching { JSONArray(bundled) }.getOrNull()?.let {
                lastStatus = DataStatus("bundled", null)
                return@withContext it
            }
        }

        JSONArray()
    }

    /** Clears the local cache so the next load forces a fresh network fetch. */
    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun fetchRemote(fileName: String): String? = runCatching {
        val connection = URL("$REMOTE_BASE/$fileName").openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 12000
        connection.requestMethod = "GET"
        connection.connect()
        if (connection.responseCode !in 200..299) return null
        connection.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun nowIso(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
}
