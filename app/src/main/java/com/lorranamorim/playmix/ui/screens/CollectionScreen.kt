package com.lorranamorim.playmix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorranamorim.playmix.data.ContextType
import com.lorranamorim.playmix.data.PlayContext
import com.lorranamorim.playmix.data.Track
import com.lorranamorim.playmix.ui.AppViewModel
import com.lorranamorim.playmix.ui.Nav
import com.lorranamorim.playmix.ui.components.ContextCover
import com.lorranamorim.playmix.ui.components.Cover
import com.lorranamorim.playmix.ui.components.NameDialog
import com.lorranamorim.playmix.ui.components.SectionTitle
import com.lorranamorim.playmix.ui.components.ShelfCard
import com.lorranamorim.playmix.ui.components.TrackRow
import com.lorranamorim.playmix.ui.components.rememberCoverColor
import com.lorranamorim.playmix.ui.theme.GoldBrush
import com.lorranamorim.playmix.ui.theme.PM

@Composable
fun CollectionScreen(vm: AppViewModel, nav: Nav, type: ContextType, id: String, bottomSpace: Dp) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()

    val shelf = remember(lib, data, type, id) { vm.shelfFor(PlayContext(type, id, id), lib, data) }
    val tracks = remember(lib, data, type, id) {
        val base = vm.tracksFor(PlayContext(type, id, id))
        if (type == ContextType.ARTIST) {
            // populares primeiro: as mais tocadas por você
            base.sortedByDescending { data.stats[it.id]?.count ?: 0 }
        } else base
    }

    if (shelf == null) {
        Column(Modifier.fillMaxSize().background(PM.Bg).statusBarsPadding()) {
            IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Text("Não encontrado", modifier = Modifier.padding(PM.s4), color = PM.TextDim)
        }
        return
    }

    val ctx = shelf.ctx
    val headerColor = when (type) {
        ContextType.LIKED -> PM.GoldDeep
        else -> rememberCoverColor(tracks.firstOrNull()?.artUri)
    }
    val totalMs = tracks.sumOf { it.durationMs }
    val pinned = data.pinned.contains(ctx.key)
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var addingSongs by remember { mutableStateOf(false) }
    val isPlaylist = type == ContextType.PLAYLIST
    val playlistId = if (isPlaylist) id else null

    LazyColumn(
        Modifier.fillMaxSize().background(PM.Bg),
        contentPadding = PaddingValues(bottom = bottomSpace + 24.dp),
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(headerColor.copy(alpha = 0.85f), headerColor.copy(alpha = 0.35f), PM.Bg)),
                ),
            ) {
                Column(Modifier.statusBarsPadding().fillMaxWidth()) {
                    IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = PM.Text) }
                    Box(Modifier.fillMaxWidth().padding(vertical = PM.s2), contentAlignment = Alignment.Center) {
                        ContextCover(type, if (type == ContextType.ARTIST) tracks.take(1) else tracks, 220.dp, ctx.title)
                    }
                    Text(
                        ctx.title, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = PM.s4, vertical = PM.s2),
                        textAlign = if (type == ContextType.ARTIST) TextAlign.Center else TextAlign.Start,
                    )
                    Text(
                        "${shelf.subtitle} • ${formatTotal(totalMs)}",
                        style = MaterialTheme.typography.bodyMedium, color = PM.TextDim,
                        modifier = Modifier.padding(horizontal = PM.s4),
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = PM.s2, vertical = PM.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (type != ContextType.LIKED) {
                    IconButton(onClick = { vm.togglePin(ctx.key) }) {
                        Icon(if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, if (pinned) "Desafixar" else "Fixar", tint = if (pinned) PM.Gold else PM.TextDim)
                    }
                }
                if (isPlaylist) {
                    IconButton(onClick = { addingSongs = true }) { Icon(Icons.Filled.AddCircleOutline, "Adicionar músicas", tint = PM.TextDim) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Mais", tint = PM.TextDim) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Renomear") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menuOpen = false; renaming = true })
                            DropdownMenuItem(text = { Text("Excluir playlist") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menuOpen = false; deleting = true })
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.play(ctx, tracks, -1, shuffle = true) }, enabled = tracks.isNotEmpty()) {
                    Icon(Icons.Filled.Shuffle, "Tocar no aleatório", tint = if (tracks.isEmpty()) PM.Divider else PM.Gold, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(PM.s2))
                Box(
                    Modifier.size(56.dp).background(GoldBrush, CircleShape)
                        .clickable(enabled = tracks.isNotEmpty()) { vm.play(ctx, tracks, 0, shuffle = false) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.PlayArrow, "Tocar", tint = PM.Black, modifier = Modifier.size(32.dp)) }
                Spacer(Modifier.width(PM.s2))
            }
        }

        if (tracks.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(PM.s8), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (type == ContextType.LIKED) "Toque no coração de qualquer música para ela aparecer aqui."
                    else "Esta playlist ainda está vazia.",
                    color = PM.TextDim, textAlign = TextAlign.Center,
                )
                if (isPlaylist) {
                    Spacer(Modifier.height(PM.s4))
                    Button(onClick = { addingSongs = true }, colors = ButtonDefaults.buttonColors(containerColor = PM.Text, contentColor = PM.Black)) {
                        Text("Adicionar músicas")
                    }
                }
            }
        }

        if (type == ContextType.ARTIST && tracks.isNotEmpty()) item { SectionTitle("Populares") }

        val shown = if (type == ContextType.ARTIST) tracks.take(10) else tracks
        itemsIndexed(shown, key = { i, t -> "$i-${t.id}" }) { i, t ->
            TrackRow(
                t,
                isCurrent = player.currentId == t.id,
                liked = data.liked.contains(t.id),
                onClick = { vm.play(ctx, tracks, i, shuffle = player.shuffle) },
                onMore = { nav.trackMenu(t, playlistId) },
                showArt = type != ContextType.ALBUM,
                number = if (type == ContextType.ALBUM) i + 1 else null,
            )
        }

        if (type == ContextType.ARTIST) {
            val artist = lib.artists.firstOrNull { it.name == id }
            if (artist != null && artist.albums.isNotEmpty()) {
                item { SectionTitle("Álbuns", Modifier.padding(top = PM.s4)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = PM.s4), horizontalArrangement = Arrangement.spacedBy(PM.s3)) {
                        items(artist.albums, key = { it.id }) { a ->
                            val s = vm.albumShelf(a)
                            ShelfCard(s, 140.dp, { nav.open(s.ctx) })
                        }
                    }
                }
            }
        }
    }

    if (renaming && playlistId != null) {
        NameDialog("Renomear playlist", ctx.title, "Salvar", onDismiss = { renaming = false }) { name ->
            vm.renamePlaylist(playlistId, name); renaming = false
        }
    }
    if (deleting && playlistId != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = PM.Surface,
            title = { Text("Excluir \"${ctx.title}\"?") },
            text = { Text("As músicas continuam no seu celular. Só a playlist é apagada.") },
            confirmButton = {
                TextButton(onClick = { deleting = false; vm.deletePlaylist(playlistId); nav.back() }) { Text("Excluir", color = PM.Gold) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } },
        )
    }
    if (addingSongs && playlistId != null) {
        AddSongsSheet(vm, playlistId) { addingSongs = false }
    }
}

private fun formatTotal(ms: Long): String {
    val min = ms / 60000
    return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSongsSheet(vm: AppViewModel, playlistId: String, onDismiss: () -> Unit) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val inPlaylist = data.playlists.firstOrNull { it.id == playlistId }?.trackIds?.toSet() ?: emptySet()
    val q = normalize(query.trim())
    val list: List<Track> = remember(lib, q) {
        if (q.isEmpty()) lib.tracks else lib.tracks.filter { normalize("${it.title} ${it.artist} ${it.album}").contains(q) }
    }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = PM.Surface) {
        Column(Modifier.navigationBarsPadding()) {
            Text("Adicionar músicas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = PM.s4))
            TextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Buscar no celular") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = PM.SurfaceHigh, unfocusedContainerColor = PM.SurfaceHigh,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = PM.Gold,
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(PM.s4),
            )
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(list, key = { it.id }) { t ->
                    val added = inPlaylist.contains(t.id)
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { if (added) vm.removeFromPlaylist(playlistId, t.id) else vm.addToPlaylist(playlistId, listOf(t.id)) }
                            .padding(horizontal = PM.s4, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(t.artUri, Modifier.size(44.dp), RoundedCornerShape(4.dp), seed = t.album)
                        Spacer(Modifier.width(PM.s3))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                            Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = PM.TextDim)
                        }
                        Icon(
                            if (added) Icons.Filled.CheckCircle else Icons.Filled.Add,
                            if (added) "Adicionada" else "Adicionar",
                            tint = if (added) PM.Gold else PM.TextDim,
                        )
                    }
                }
            }
        }
    }
}
