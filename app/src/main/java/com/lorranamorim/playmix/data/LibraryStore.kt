package com.lorranamorim.playmix.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Guarda curtidas, playlists, fixados, histórico e configurações num JSON local.
 * Tudo fica no aparelho.
 */
class LibraryStore(context: Context) {

    private val file = File(context.filesDir, "library.json")
    private val queueFile = File(context.filesDir, "queue.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _data = MutableStateFlow(load())
    val data: StateFlow<LibraryData> = _data.asStateFlow()

    // ---------- Curtidas ----------
    fun isLiked(id: Long) = _data.value.liked.contains(id)

    fun toggleLike(id: Long) = edit { d ->
        if (d.liked.contains(id)) d.copy(liked = d.liked - id) else d.copy(liked = listOf(id) + d.liked)
    }

    // ---------- Playlists ----------
    fun createPlaylist(name: String, initial: List<Long> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        edit { d -> d.copy(playlists = listOf(Playlist(id, name.ifBlank { "Minha playlist" }, initial.distinct(), now, now)) + d.playlists) }
        return id
    }

    fun renamePlaylist(id: String, name: String) = editPlaylist(id) { it.copy(name = name.ifBlank { it.name }) }

    fun deletePlaylist(id: String) = edit { d ->
        d.copy(
            playlists = d.playlists.filterNot { it.id == id },
            pinned = d.pinned - "${ContextType.PLAYLIST.name}:$id",
            recentContexts = d.recentContexts.filterNot { it.first == "${ContextType.PLAYLIST.name}:$id" },
        )
    }

    fun addToPlaylist(id: String, trackIds: List<Long>) = editPlaylist(id) { p ->
        p.copy(trackIds = p.trackIds + trackIds.filterNot { it in p.trackIds })
    }

    fun removeFromPlaylist(id: String, trackId: Long) = editPlaylist(id) { p -> p.copy(trackIds = p.trackIds - trackId) }

    private fun editPlaylist(id: String, f: (Playlist) -> Playlist) = edit { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) f(it).copy(updatedAt = System.currentTimeMillis()) else it })
    }

    // ---------- Fixados ----------
    fun togglePin(key: String) = edit { d ->
        if (d.pinned.contains(key)) d.copy(pinned = d.pinned - key) else d.copy(pinned = listOf(key) + d.pinned)
    }

    // ---------- Histórico ----------
    fun recordPlay(trackId: Long) = edit { d ->
        val old = d.stats[trackId]
        d.copy(stats = d.stats + (trackId to PlayStat((old?.count ?: 0) + 1, System.currentTimeMillis())))
    }

    fun recordContext(ctx: PlayContext) = edit { d ->
        val list = listOf(ctx.key to ctx.title) + d.recentContexts.filterNot { it.first == ctx.key }
        d.copy(recentContexts = list.take(30))
    }

    // ---------- Configurações ----------
    fun updateSettings(f: (LibrarySettings) -> LibrarySettings) = edit { d -> d.copy(settings = f(d.settings)) }

    // ---------- Fila salva (usada pelo serviço de reprodução) ----------
    fun saveQueue(json: JSONObject) {
        scope.launch { synchronized(lock) { runCatching { queueFile.writeText(json.toString()) } } }
    }

    fun loadQueue(): JSONObject? = synchronized(lock) {
        runCatching { if (queueFile.exists()) JSONObject(queueFile.readText()) else null }.getOrNull()
    }

    // ---------- Persistência ----------
    private fun edit(f: (LibraryData) -> LibraryData) {
        _data.update(f)
        // grava sempre o estado mais novo, mesmo se duas edições chegarem juntas
        scope.launch { synchronized(lock) { runCatching { file.writeText(toJson(_data.value).toString()) } } }
    }

    private fun load(): LibraryData = runCatching {
        if (!file.exists()) LibraryData() else fromJson(JSONObject(file.readText()))
    }.getOrElse { LibraryData() }

    private fun toJson(d: LibraryData): JSONObject = JSONObject().apply {
        put("liked", JSONArray(d.liked))
        put("playlists", JSONArray().apply {
            d.playlists.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("name", p.name)
                    put("tracks", JSONArray(p.trackIds))
                    put("created", p.createdAt); put("updated", p.updatedAt)
                })
            }
        })
        put("pinned", JSONArray(d.pinned))
        put("stats", JSONObject().apply {
            d.stats.forEach { (id, s) -> put(id.toString(), JSONArray(listOf(s.count, s.lastPlayed))) }
        })
        put("recent", JSONArray().apply {
            d.recentContexts.forEach { (k, t) -> put(JSONArray(listOf(k, t))) }
        })
        put("settings", JSONObject().apply {
            put("fade", d.settings.fadeSeconds)
            put("excluded", JSONArray(d.settings.excludedFolders.toList()))
            put("musicFolders", JSONArray(d.settings.musicFolders.toList()))
            put("grid", d.settings.gridMode)
            put("sort", d.settings.sort.name)
            put("autoEq", d.settings.autoEq)
            put("beatMatch", d.settings.beatMatch)
            put("smartCut", d.settings.smartCut)
            put("profileName", d.settings.profileName)
            put("photoV", d.settings.profilePhotoVersion)
        })
    }

    private fun fromJson(o: JSONObject): LibraryData {
        val liked = o.optJSONArray("liked").longs()
        val playlists = o.optJSONArray("playlists")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val p = arr.optJSONObject(i) ?: return@mapNotNull null
                Playlist(
                    id = p.optString("id"),
                    name = p.optString("name"),
                    trackIds = p.optJSONArray("tracks").longs(),
                    createdAt = p.optLong("created"),
                    updatedAt = p.optLong("updated"),
                )
            }
        } ?: emptyList()
        val pinned = o.optJSONArray("pinned").strings()
        val stats = HashMap<Long, PlayStat>()
        o.optJSONObject("stats")?.let { s ->
            s.keys().forEach { k ->
                val a = s.optJSONArray(k) ?: return@forEach
                k.toLongOrNull()?.let { id -> stats[id] = PlayStat(a.optInt(0), a.optLong(1)) }
            }
        }
        val recent = o.optJSONArray("recent")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val a = arr.optJSONArray(i) ?: return@mapNotNull null
                a.optString(0) to a.optString(1)
            }
        } ?: emptyList()
        val s = o.optJSONObject("settings")
        val settings = if (s == null) LibrarySettings() else LibrarySettings(
            fadeSeconds = s.optInt("fade", 4),
            excludedFolders = s.optJSONArray("excluded").strings().toSet(),
            musicFolders = s.optJSONArray("musicFolders").strings().toSet(),
            gridMode = s.optBoolean("grid", false),
            sort = runCatching { LibrarySort.valueOf(s.optString("sort")) }.getOrDefault(LibrarySort.RECENT),
            autoEq = s.optBoolean("autoEq", true),
            beatMatch = s.optBoolean("beatMatch", true),
            smartCut = s.optBoolean("smartCut", true),
            profileName = s.optString("profileName", ""),
            profilePhotoVersion = s.optLong("photoV", 0L),
        )
        return LibraryData(liked, playlists, pinned, stats, recent, settings)
    }

    private fun JSONArray?.longs(): List<Long> =
        if (this == null) emptyList() else (0 until length()).map { optLong(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }
}
