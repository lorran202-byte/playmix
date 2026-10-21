package com.lorranamorim.playmix.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MusicLibrary(
    val tracks: List<Track> = emptyList(),
    val byId: Map<Long, Track> = emptyMap(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val genres: List<String> = emptyList(),
    val folders: List<String> = emptyList(),
    val loaded: Boolean = false,
)

class MusicRepository(private val context: Context, private val store: LibraryStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scanner = MusicScanner(context)
    private val allTracks = MutableStateFlow<List<Track>>(emptyList())
    private val loaded = MutableStateFlow(false)
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private var observerRegistered = false
    private var rescanJob: Job? = null

    /** Todas as pastas encontradas, mesmo as que o usuário escondeu. */
    val allFolders: StateFlow<List<String>> =
        combine(allTracks, loaded) { t, _ -> t.map { it.folder }.distinct().sorted() }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val library: StateFlow<MusicLibrary> = combine(allTracks, loaded, store.data) { tracks, isLoaded, data ->
        build(tracks.filterNot { it.folder in data.settings.excludedFolders }, isLoaded)
    }.stateIn(scope, SharingStarted.Eagerly, MusicLibrary())

    fun refresh() {
        scope.launch {
            _scanning.value = true
            allTracks.value = scanner.scan()
            loaded.value = true
            _scanning.value = false
        }
        registerObserver()
    }

    /** Quando arquivos de música mudam no aparelho, varre de novo sozinho. */
    private fun registerObserver() {
        if (observerRegistered) return
        observerRegistered = true
        runCatching {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        rescanJob?.cancel()
                        rescanJob = scope.launch {
                            delay(2000)
                            allTracks.value = scanner.scan()
                        }
                    }
                },
            )
        }
    }

    private fun build(tracks: List<Track>, isLoaded: Boolean): MusicLibrary {
        val albums = tracks.groupBy { it.albumId }.map { (id, t) ->
            val sorted = t.sortedWith(compareBy({ it.trackNumber }, { it.title.lowercase() }))
            val artist = t.groupingBy { it.primaryArtist }.eachCount().maxByOrNull { it.value }?.key ?: ""
            Album(id, t.first().album, artist, sorted)
        }.sortedBy { it.title.lowercase() }
        val albumsByArtist = albums.groupBy { it.artist }
        // a faixa entra em todos os artistas dela (principal e participações)
        val byArtist = LinkedHashMap<String, MutableList<Track>>()
        tracks.forEach { t -> t.artists.forEach { a -> byArtist.getOrPut(a) { ArrayList() }.add(t) } }
        val artists = byArtist.map { (name, t) ->
            Artist(name, t.sortedBy { it.title.lowercase() }, albumsByArtist[name] ?: emptyList())
        }.sortedBy { it.name.lowercase() }
        return MusicLibrary(
            tracks = tracks.sortedBy { it.title.lowercase() },
            byId = tracks.associateBy { it.id },
            albums = albums,
            artists = artists,
            genres = tracks.mapNotNull { it.genre }.distinct().sortedBy { it.lowercase() },
            folders = tracks.map { it.folder }.distinct().sorted(),
            loaded = isLoaded,
        )
    }
}
