package com.lorranamorim.playmix.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorranamorim.playmix.data.ContextType
import com.lorranamorim.playmix.data.LibrarySort
import com.lorranamorim.playmix.data.PlayContext
import com.lorranamorim.playmix.ui.AppViewModel
import com.lorranamorim.playmix.ui.Nav
import com.lorranamorim.playmix.ui.Shelf
import com.lorranamorim.playmix.ui.components.ProfileAvatar
import com.lorranamorim.playmix.ui.components.GlassCircleButton
import com.lorranamorim.playmix.ui.components.GlassPill
import com.lorranamorim.playmix.ui.components.GlassSegmented
import com.lorranamorim.playmix.ui.components.ScreenHeader
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.KeyboardArrowDown
import com.lorranamorim.playmix.ui.components.ProfileDialog
import com.lorranamorim.playmix.ui.components.NameDialog
import com.lorranamorim.playmix.ui.components.ShelfCard
import com.lorranamorim.playmix.ui.components.ShelfRow
import com.lorranamorim.playmix.ui.components.TrackRow
import com.lorranamorim.playmix.ui.theme.PM
import kotlinx.coroutines.launch

private enum class LibFilter(val label: String) { PLAYLISTS("Playlists"), ARTISTS("Artistas"), ALBUMS("Álbuns"), SONGS("Músicas") }

@Composable
fun LibraryScreen(vm: AppViewModel, nav: Nav, bottomSpace: Dp) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    if (showProfile) ProfileDialog(vm) { showProfile = false }
    var sortMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val f = filter?.let { LibFilter.valueOf(it) }
    val settings = data.settings

    val shelves: List<Shelf> = remember(lib, data, f) {
        val recentRank = data.recentContexts.mapIndexed { i, p -> p.first to i }.toMap()
        val list = ArrayList<Pair<Shelf, Long>>() // shelf + data de adição
        if (f == null || f == LibFilter.PLAYLISTS) {
            list += vm.likedShelf(lib, data) to Long.MAX_VALUE
            data.playlists.forEach { list += vm.playlistShelf(it, lib) to it.createdAt }
        }
        // sem filtro: só o que você usa (tocados recentemente e fixados); a lista completa fica nos filtros
        val mine = recentRank.keys + data.pinned
        if (f == LibFilter.ALBUMS) lib.albums.forEach { a -> list += vm.albumShelf(a) to a.tracks.maxOf { it.dateAdded } }
        else if (f == null) lib.albums.filter { "ALBUM:${it.id}" in mine }.forEach { a -> list += vm.albumShelf(a) to a.tracks.maxOf { it.dateAdded } }
        if (f == LibFilter.ARTISTS) lib.artists.forEach { a -> list += vm.artistShelf(a) to a.tracks.maxOf { it.dateAdded } }
        else if (f == null) lib.artists.filter { "ARTIST:${it.name}" in mine }.forEach { a -> list += vm.artistShelf(a) to a.tracks.maxOf { it.dateAdded } }
        if (f == null) {
            // mixes por gênero que você tocou
            recentRank.keys.filter { it.startsWith("GENRE:") }.forEach { key ->
                PlayContext.fromKey(key, key.substringAfter(':'))?.let { ctx -> vm.shelfFor(ctx, lib, data)?.let { list += it to 0L } }
            }
        }
        val sorted = when (settings.sort) {
            LibrarySort.RECENT -> list.sortedWith(compareBy({ recentRank[it.first.ctx.key] ?: Int.MAX_VALUE }, { -it.second }))
            LibrarySort.ADDED -> list.sortedByDescending { it.second }
            LibrarySort.ALPHA -> list.sortedBy { normalize(it.first.ctx.title) }
        }.map { it.first }
        val pinned = data.pinned.mapNotNull { key -> sorted.firstOrNull { it.ctx.key == key } }
        pinned + sorted.filterNot { s -> pinned.any { it.ctx.key == s.ctx.key } }
    }

    fun togglePin(s: Shelf) {
        val was = data.pinned.contains(s.ctx.key)
        vm.togglePin(s.ctx.key)
        Toast.makeText(context, if (was) "Desafixado" else "Fixado no topo", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Biblioteca") {
            GlassCircleButton(Icons.Filled.Add, "Criar playlist", { creating = true }, size = 42.dp)
            ProfileAvatar(vm, 42.dp, Modifier.clickable { showProfile = true })
        }
        // filtros em pílulas de vidro
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = PM.s4, vertical = PM.s2),
            horizontalArrangement = Arrangement.spacedBy(PM.s2), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (f != null) GlassCircleButton(Icons.Filled.Close, "Limpar filtro", { filter = null }, size = 38.dp)
            LibFilter.entries.forEach { lf ->
                if (f == null || f == lf) GlassPill(lf.label, { filter = if (f == lf) null else lf.name }, selected = f == lf)
            }
        }

        if (f != LibFilter.SONGS) {
            Row(
                Modifier.fillMaxWidth().padding(start = PM.s4, end = PM.s4, top = 4.dp, bottom = PM.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ordenação: pílula com o critério + círculo com a setinha
                Box {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassPill(settings.sort.label, { sortMenu = true })
                        Spacer(Modifier.width(6.dp))
                        GlassCircleButton(Icons.Filled.KeyboardArrowDown, "Ordenar", { sortMenu = true }, size = 38.dp)
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }, containerColor = PM.Surface) {
                        LibrarySort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label, color = if (s == settings.sort) PM.Gold else PM.Text) },
                                onClick = { vm.updateSettings { it.copy(sort = s) }; sortMenu = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                GlassSegmented(
                    options = listOf(Icons.AutoMirrored.Filled.List to "Ver em lista", Icons.Filled.GridView to "Ver em grade"),
                    selected = if (settings.gridMode) 1 else 0,
                    onSelect = { i -> vm.updateSettings { it.copy(gridMode = i == 1) } },
                )
            }
        }

        when {
            f == LibFilter.SONGS -> SongsWithIndex(vm, nav, bottomSpace, player.currentId, data.liked.toSet())
            settings.gridMode -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = PM.s4, end = PM.s4, bottom = bottomSpace + 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shelves, key = { it.ctx.key }) { s ->
                    // cartões grandes que ocupam a coluna inteira
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        ShelfCard(s, maxWidth, { nav.open(s.ctx) }, { togglePin(s) })
                    }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomSpace + 24.dp)) {
                items(shelves, key = { it.ctx.key }) { s ->
                    ShelfRow(s, data.pinned.contains(s.ctx.key), { nav.open(s.ctx) }, { togglePin(s) })
                }
            }
        }
    }

    if (creating) {
        NameDialog("Dê um nome à sua playlist", "", "Criar", onDismiss = { creating = false }) { name ->
            val id = vm.createPlaylist(name)
            creating = false
            nav.open(PlayContext(ContextType.PLAYLIST, id, name))
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) PM.Gold else PM.SurfaceHigh, RoundedCornerShape(50))
            .border(1.dp, if (selected) PM.Gold else PM.Silver.copy(alpha = 0.35f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) PM.Black else PM.Text)
    }
}

private const val LETTERS = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ"

/** Lista de todas as músicas com a barra A–Z na lateral para pular rápido. */
@Composable
private fun SongsWithIndex(vm: AppViewModel, nav: Nav, bottomSpace: Dp, currentId: Long?, liked: Set<Long>) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val tracks = lib.tracks
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var barHeight by remember { mutableIntStateOf(1) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }

    val firstIndex = remember(tracks) {
        val map = HashMap<Char, Int>()
        tracks.forEachIndexed { i, t ->
            val c = normalize(t.title).firstOrNull()?.uppercaseChar() ?: '#'
            val key = if (c in 'A'..'Z') c else '#'
            if (!map.containsKey(key)) map[key] = i
        }
        map
    }

    fun jump(y: Float) {
        val idx = ((y / barHeight) * LETTERS.length).toInt().coerceIn(0, LETTERS.length - 1)
        val letter = LETTERS[idx]
        activeLetter = letter
        // vai para a letra pedida ou a próxima que existir
        val target = LETTERS.substring(idx).firstNotNullOfOrNull { firstIndex[it] } ?: return
        scope.launch { state.scrollToItem(target + 1) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomSpace + 24.dp)) {
            item {
                Text(
                    "${tracks.size} músicas • toque para tocar",
                    style = MaterialTheme.typography.bodySmall, color = PM.TextDim,
                    modifier = Modifier.padding(horizontal = PM.s4, vertical = PM.s2),
                )
            }
            items(tracks, key = { it.id }) { t ->
                TrackRow(
                    t, isCurrent = currentId == t.id, liked = liked.contains(t.id),
                    onClick = { vm.playSingle(t, tracks, "Todas as músicas") },
                    onMore = { nav.trackMenu(t, null) },
                    modifier = Modifier.padding(end = 20.dp),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.72f)
                .width(24.dp)
                .onSizeChanged { barHeight = it.height.coerceAtLeast(1) }
                .pointerInput(firstIndex) {
                    detectTapGestures(onPress = {
                        jump(it.y)
                        tryAwaitRelease()
                        activeLetter = null
                    })
                }
                .pointerInput(firstIndex) {
                    detectDragGestures(
                        onDragStart = { jump(it.y) },
                        onDrag = { change, _ -> jump(change.position.y) },
                        onDragEnd = { activeLetter = null },
                        onDragCancel = { activeLetter = null },
                    )
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LETTERS.forEach { c ->
                Text(
                    c.toString(),
                    fontSize = 10.sp,
                    fontWeight = if (c == activeLetter) FontWeight.Bold else FontWeight.Normal,
                    color = if (c == activeLetter) PM.Gold else PM.TextDim,
                )
            }
        }
        activeLetter?.let { l ->
            Box(
                Modifier.align(Alignment.Center).size(72.dp).background(PM.Surface.copy(alpha = 0.92f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(l.toString(), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = PM.Gold) }
        }
    }
}
