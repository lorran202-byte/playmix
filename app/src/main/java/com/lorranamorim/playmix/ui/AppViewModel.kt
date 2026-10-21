package com.lorranamorim.playmix.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lorranamorim.playmix.PlayMixApp
import com.lorranamorim.playmix.data.Album
import com.lorranamorim.playmix.data.Artist
import com.lorranamorim.playmix.data.ContextType
import com.lorranamorim.playmix.data.LibraryData
import com.lorranamorim.playmix.data.LibrarySettings
import com.lorranamorim.playmix.data.Lyrics
import com.lorranamorim.playmix.data.MusicLibrary
import com.lorranamorim.playmix.data.PlayContext
import com.lorranamorim.playmix.data.Playlist
import com.lorranamorim.playmix.data.Track
import com.lorranamorim.playmix.playback.PlayerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.random.Random

sealed interface LyricsState {
    data object Idle : LyricsState
    data object Loading : LyricsState
    data object NotFound : LyricsState
    /** [translation]: uma linha traduzida para cada linha da letra (null = sem tradução). */
    data class Ready(val trackId: Long, val lyrics: Lyrics, val translation: List<String>? = null) : LyricsState
}

/** Um item clicável do Início / Biblioteca (playlist, álbum, artista, curtidas, mix). */
data class Shelf(val ctx: PlayContext, val subtitle: String, val artTracks: List<Track>)

data class HomeState(
    val shortcuts: List<Shelf> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
    val mostPlayed: List<Track> = emptyList(),
    val rediscover: List<Track> = emptyList(),
    val mixes: List<Shelf> = emptyList(),
    val albums: List<Album> = emptyList(),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val c = (app as PlayMixApp).container
    val music = c.music
    val store = c.store
    val playerConn = c.player

    val library: StateFlow<MusicLibrary> = music.library
    val data: StateFlow<LibraryData> = store.data
    val player: StateFlow<PlayerState> = playerConn.state
    val scanning = music.scanning
    val allFolders = music.allFolders
    val eqPreset = c.eq.preset

    private val _lyrics = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val lyrics: StateFlow<LyricsState> = _lyrics.asStateFlow()
    private var lyricsJob: Job? = null

    val home: StateFlow<HomeState> = combine(library, data) { lib, d -> buildHome(lib, d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    init {
        viewModelScope.launch {
            combine(player.map { it.currentId }.distinctUntilChanged(), library) { id, lib -> id?.let { lib.byId[it] } }
                .distinctUntilChanged()
                .collect { track ->
                    loadLyrics(track)
                    // garante o equalizador certo mesmo se a biblioteca carregou depois da música começar
                    if (track != null) c.eq.apply(track.genre)
                }
        }
    }

    fun refreshLibrary() = music.refresh()

    // ---------------- Contextos ----------------

    fun tracksFor(ctx: PlayContext): List<Track> {
        val lib = library.value
        return when (ctx.type) {
            ContextType.PLAYLIST -> data.value.playlists.firstOrNull { it.id == ctx.id }?.trackIds?.mapNotNull { lib.byId[it] } ?: emptyList()
            ContextType.ALBUM -> lib.albums.firstOrNull { it.id.toString() == ctx.id }?.tracks ?: emptyList()
            ContextType.ARTIST -> lib.artists.firstOrNull { it.name == ctx.id }?.tracks ?: emptyList()
            ContextType.LIKED -> data.value.liked.mapNotNull { lib.byId[it] }
            ContextType.ALL -> lib.tracks
            ContextType.GENRE -> lib.tracks.filter { it.genre == ctx.id }
            ContextType.SEARCH -> emptyList()
        }
    }

    fun shelfFor(ctx: PlayContext, lib: MusicLibrary = library.value, d: LibraryData = data.value): Shelf? = when (ctx.type) {
        ContextType.PLAYLIST -> d.playlists.firstOrNull { it.id == ctx.id }?.let { playlistShelf(it, lib) }
        ContextType.ALBUM -> lib.albums.firstOrNull { it.id.toString() == ctx.id }?.let { albumShelf(it) }
        ContextType.ARTIST -> lib.artists.firstOrNull { it.name == ctx.id }?.let { artistShelf(it) }
        ContextType.LIKED -> likedShelf(lib, d)
        ContextType.GENRE -> lib.tracks.filter { it.genre == ctx.id }.takeIf { it.isNotEmpty() }?.let {
            Shelf(PlayContext(ContextType.GENRE, ctx.id, "Mix ${ctx.id}"), "${it.size} músicas", it.take(4))
        }
        ContextType.ALL -> Shelf(PlayContext(ContextType.ALL, "all", "Todas as músicas"), "${lib.tracks.size} músicas", lib.tracks.take(4))
        ContextType.SEARCH -> null
    }

    fun playlistShelf(p: Playlist, lib: MusicLibrary = library.value): Shelf {
        val tracks = p.trackIds.mapNotNull { lib.byId[it] }
        return Shelf(PlayContext(ContextType.PLAYLIST, p.id, p.name), "Playlist • ${tracks.size} músicas", tracks.distinctBy { it.albumId }.take(4))
    }

    fun albumShelf(a: Album) = Shelf(PlayContext(ContextType.ALBUM, a.id.toString(), a.title), "Álbum • ${a.artist}", a.tracks.take(1))
    fun artistShelf(a: Artist) = Shelf(PlayContext(ContextType.ARTIST, a.name, a.name), "Artista", a.tracks.take(1))
    fun likedShelf(lib: MusicLibrary = library.value, d: LibraryData = data.value): Shelf {
        val n = d.liked.count { lib.byId.containsKey(it) }
        return Shelf(PlayContext(ContextType.LIKED, "liked", "Músicas Curtidas"), "Playlist • $n músicas", emptyList())
    }

    // ---------------- Tocar ----------------

    fun play(ctx: PlayContext, tracks: List<Track>, index: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        if (ctx.type != ContextType.SEARCH) store.recordContext(ctx)
        playerConn.play(tracks, index, shuffle, ctx.title)
    }

    fun playContext(ctx: PlayContext, shuffle: Boolean = false) {
        play(ctx, tracksFor(ctx), if (shuffle) -1 else 0, shuffle)
    }

    fun playSingle(track: Track, list: List<Track>, title: String) {
        val idx = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playerConn.play(list, idx, player.value.shuffle, title)
    }

    // ---------------- Biblioteca ----------------

    fun toggleLike(id: Long) = store.toggleLike(id)
    fun togglePin(key: String) = store.togglePin(key)
    fun createPlaylist(name: String, tracks: List<Long> = emptyList()) = store.createPlaylist(name, tracks)
    fun renamePlaylist(id: String, name: String) = store.renamePlaylist(id, name)
    fun deletePlaylist(id: String) = store.deletePlaylist(id)
    fun addToPlaylist(id: String, tracks: List<Long>) = store.addToPlaylist(id, tracks)
    fun removeFromPlaylist(id: String, trackId: Long) = store.removeFromPlaylist(id, trackId)
    fun updateSettings(f: (LibrarySettings) -> LibrarySettings) {
        store.updateSettings(f)
        c.eq.reapply()
    }

    // ---------------- Perfil ----------------

    val profilePhotoFile: java.io.File get() = java.io.File(getApplication<Application>().filesDir, "profile.jpg")

    fun setProfileName(name: String) = store.updateSettings { it.copy(profileName = name.trim().take(40)) }

    /** Copia a foto escolhida para dentro do app (a original pode ser apagada depois). */
    fun setProfilePhoto(uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ok = runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    profilePhotoFile.outputStream().use { input.copyTo(it) }
                } != null
            }.getOrDefault(false)
            if (ok) store.updateSettings { it.copy(profilePhotoVersion = System.currentTimeMillis()) }
        }
    }

    fun removeProfilePhoto() {
        profilePhotoFile.delete()
        store.updateSettings { it.copy(profilePhotoVersion = 0L) }
    }

    fun retryLyrics() {
        val id = player.value.currentId ?: return
        c.lyrics.clearCache(id)
        loadLyrics(library.value.byId[id])
    }

    private fun loadLyrics(track: Track?) {
        lyricsJob?.cancel()
        if (track == null) { _lyrics.value = LyricsState.Idle; return }
        _lyrics.value = LyricsState.Loading
        lyricsJob = viewModelScope.launch {
            val l = runCatching { c.lyrics.get(track) }.getOrNull()
            if (l == null || l.isEmpty) {
                _lyrics.value = LyricsState.NotFound
                return@launch
            }
            _lyrics.value = LyricsState.Ready(track.id, l)
            // tradução para português (não traduz se a letra já estiver em português)
            val lines = if (l.isSynced) l.synced.map { it.text } else l.plain.orEmpty().lines()
            val tr = runCatching { c.translator.translate(track.id, lines) }.getOrNull()
            if (tr != null) _lyrics.value = LyricsState.Ready(track.id, l, tr)
        }
    }

    // ---------------- Início ----------------

    private fun buildHome(lib: MusicLibrary, d: LibraryData): HomeState {
        if (lib.tracks.isEmpty()) return HomeState()
        val shortcuts = ArrayList<Shelf>()
        d.recentContexts.forEach { (key, title) ->
            if (shortcuts.size >= 8) return@forEach
            PlayContext.fromKey(key, title)?.let { ctx -> shelfFor(ctx, lib, d)?.let { shortcuts += it } }
        }
        if (shortcuts.none { it.ctx.type == ContextType.LIKED } && shortcuts.size < 8) shortcuts += likedShelf(lib, d)
        d.playlists.forEach { p -> if (shortcuts.size < 8 && shortcuts.none { it.ctx.key == "PLAYLIST:${p.id}" }) shortcuts += playlistShelf(p, lib) }
        lib.albums.shuffled(Random(daySeed())).forEach { a ->
            if (shortcuts.size < 8 && shortcuts.none { it.ctx.key == "ALBUM:${a.id}" }) shortcuts += albumShelf(a)
        }

        val played = d.stats.entries.filter { lib.byId.containsKey(it.key) }
        val recent = played.sortedByDescending { it.value.lastPlayed }.take(20).mapNotNull { lib.byId[it.key] }
        val most = played.filter { it.value.count >= 2 }.sortedByDescending { it.value.count }.take(20).mapNotNull { lib.byId[it.key] }
        val monthAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        val rediscover = lib.tracks.filter { (d.stats[it.id]?.lastPlayed ?: 0L) < monthAgo }
            .shuffled(Random(daySeed())).take(20)

        val mixes = lib.tracks.mapNotNull { it.genre }.groupingBy { it }.eachCount()
            .filter { it.value >= 6 }.entries.sortedByDescending { it.value }.take(6)
            .mapNotNull { shelfFor(PlayContext(ContextType.GENRE, it.key, "Mix ${it.key}"), lib, d) }

        return HomeState(
            shortcuts = shortcuts,
            recentTracks = recent,
            mostPlayed = most,
            rediscover = rediscover,
            mixes = mixes,
            albums = lib.albums.sortedByDescending { a -> a.tracks.maxOf { it.dateAdded } }.take(12),
        )
    }

    private fun daySeed(): Long {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR)
    }
}
