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
import com.lorranamorim.playmix.ui.components.GlassCircleButton
import com.lorranamorim.playmix.ui.components.MosaicCover
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape

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
        Modifier.fillMaxSize().background(
            // a página inteira fica na cor da capa, como nas páginas de artista dos players modernos
            Brush.verticalGradient(0f to headerColor, 0.5f to headerColor.copy(alpha = 0.55f), 1f to PM.Bg),
        ),
        contentPadding = PaddingValues(bottom = bottomSpace + 24.dp),
    ) {
        item {
            // capa de ponta a ponta no topo, sumindo na cor da página; nome grande centralizado por cima
            Box(Modifier.fillMaxWidth().height(400.dp)) {
                when (type) {
                    ContextType.LIKED -> Box(
                        Modifier.fillMaxSize().fadeBottom(0.45f).background(Brush.linearGradient(listOf(PM.GoldLight, PM.Gold, PM.GoldDeep))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Favorite, null, tint = PM.Black, modifier = Modifier.size(120.dp)) }
                    else -> MosaicCover(
                        if (type == ContextType.ARTIST || type == ContextType.ALBUM) tracks.take(1) else tracks,
                        Modifier.fillMaxSize().fadeBottom(0.45f),
                        RectangleShape,
                        seed = ctx.title,
                    )
                }
                Box(
                    Modifier.fillMaxWidth().height(140.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
                )
                Row(
                    Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = PM.s4, vertical = PM.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", nav.back, size = 42.dp)
                    Spacer(Modifier.weight(1f))
                    if (isPlaylist) {
                        Box {
                            GlassCircleButton(Icons.Filled.MoreHoriz, "Mais", { menuOpen = true }, size = 42.dp)
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = PM.Surface) {
                                DropdownMenuItem(text = { Text("Renomear") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menuOpen = false; renaming = true })
                                DropdownMenuItem(text = { Text("Excluir playlist") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menuOpen = false; deleting = true })
                            }
                        }
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = PM.s6),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        ctx.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${shelf.subtitle} • ${formatTotal(totalMs)}",
                        style = MaterialTheme.typography.bodyMedium, color = PM.SilverLight, textAlign = TextAlign.Center,
                    )
                }
            }
        }

        item {
            // botões centralizados: fixar · PLAY grande · aleatório (+ adicionar, nas playlists)
            Row(
                Modifier.fillMaxWidth().padding(vertical = PM.s4),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (type != ContextType.LIKED) {
                    GlassCircleButton(
                        if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, if (pinned) "Desafixar" else "Fixar",
                        { vm.togglePin(ctx.key) }, tint = if (pinned) PM.Gold else PM.Text,
                    )
                }
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(PM.Text)
                        .clickable(enabled = tracks.isNotEmpty()) { vm.play(ctx, tracks, 0, shuffle = false) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.PlayArrow, "Tocar", tint = PM.Black, modifier = Modifier.size(40.dp)) }
                GlassCircleButton(
                    Icons.Filled.Shuffle, "Tocar no aleatório",
                    { vm.play(ctx, tracks, -1, shuffle = true) }, enabled = tracks.isNotEmpty(),
                )
                if (isPlaylist) GlassCircleButton(Icons.Filled.Add, "Adicionar músicas", { addingSongs = true })
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
                        Cover(t.artUri, Modifier.size(44.dp), RoundedCornerShape(8.dp), seed = t.album)
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
