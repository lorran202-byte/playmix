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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lorranamorim.playmix.ui.components.ProfileAvatar
import com.lorranamorim.playmix.ui.components.ProfileDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import com.lorranamorim.playmix.ui.Shelf
import com.lorranamorim.playmix.ui.components.ContextCover
import com.lorranamorim.playmix.ui.components.SectionTitle
import com.lorranamorim.playmix.ui.components.ShelfCard
import com.lorranamorim.playmix.ui.components.TrackCard
import com.lorranamorim.playmix.ui.theme.PM
import com.lorranamorim.playmix.ui.theme.glass
import java.util.Calendar

@Composable
fun HomeScreen(vm: AppViewModel, nav: Nav, bottomSpace: Dp) {
    val home by vm.home.collectAsStateWithLifecycle()
    val lib by vm.library.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    var showProfile by remember { mutableStateOf(false) }
    if (showProfile) ProfileDialog(vm) { showProfile = false }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to PM.GoldDeep.copy(alpha = 0.28f), 0.35f to PM.Bg),
        ),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomSpace + 24.dp)) {
            item {
                Row(
                    Modifier.statusBarsPadding().fillMaxWidth().padding(start = PM.s4, end = PM.s2, top = PM.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileAvatar(vm, 44.dp, Modifier.clickable { showProfile = true })
                    Spacer(Modifier.width(PM.s3))
                    Column(Modifier.weight(1f).clickable { showProfile = true }) {
                        val name = data.settings.profileName.trim()
                        Text(
                            if (name.isBlank()) greeting() else "${greeting()},",
                            style = if (name.isBlank()) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                            color = if (name.isBlank()) PM.Text else PM.TextDim,
                        )
                        if (name.isNotBlank()) {
                            Text(name, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text("Toque para criar seu perfil", style = MaterialTheme.typography.bodySmall, color = PM.TextDim)
                        }
                    }
                    IconButton(onClick = nav.settings) { Icon(Icons.Outlined.Settings, "Configurações", tint = PM.Text) }
                }
            }

            when {
                !lib.loaded -> item {
                    Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PM.Gold)
                    }
                }
                lib.tracks.isEmpty() -> item { EmptyLibrary { vm.refreshLibrary() } }
                else -> {
                    item { Shortcuts(home.shortcuts, nav) }

                    if (home.recentTracks.isNotEmpty()) item {
                        TrackShelf("Tocadas recentemente", home.recentTracks) { i ->
                            vm.play(PlayContext(ContextType.SEARCH, "recent", "Tocadas recentemente"), home.recentTracks, i)
                        }
                    }
                    if (home.mixes.isNotEmpty()) item {
                        ShelfShelf("Seus mixes por gênero", home.mixes, nav)
                    }
                    if (home.mostPlayed.isNotEmpty()) item {
                        TrackShelf("Suas mais tocadas", home.mostPlayed) { i ->
                            vm.play(PlayContext(ContextType.SEARCH, "most", "Suas mais tocadas"), home.mostPlayed, i)
                        }
                    }
                    if (home.rediscover.isNotEmpty()) item {
                        TrackShelf("Redescubra", home.rediscover) { i ->
                            vm.play(PlayContext(ContextType.SEARCH, "rediscover", "Redescubra"), home.rediscover, i)
                        }
                    }
                    if (home.albums.isNotEmpty()) item {
                        ShelfShelf("Álbuns adicionados recentemente", home.albums.map { vm.albumShelf(it) }, nav)
                    }
                    item {
                        Text(
                            "${lib.tracks.size} músicas no aparelho",
                            style = MaterialTheme.typography.bodySmall, color = PM.TextDim,
                            modifier = Modifier.padding(PM.s4),
                        )
                    }
                }
            }
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}

@Composable
private fun Shortcuts(items: List<Shelf>, nav: Nav) {
    Column(
        Modifier.padding(horizontal = PM.s4, vertical = PM.s2),
        verticalArrangement = Arrangement.spacedBy(PM.s2),
    ) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(PM.s2)) {
                row.forEach { s ->
                    Row(
                        Modifier.weight(1f).height(56.dp).glass(RoundedCornerShape(6.dp)).clickable { nav.open(s.ctx) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ContextCover(s.ctx.type, s.artTracks, 56.dp, s.ctx.title)
                        Text(
                            s.ctx.title, style = MaterialTheme.typography.titleSmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = PM.s2),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TrackShelf(title: String, tracks: List<Track>, onPlay: (Int) -> Unit) {
    Column {
        SectionTitle(title, Modifier.padding(top = PM.s3))
        LazyRow(contentPadding = PaddingValues(horizontal = PM.s4), horizontalArrangement = Arrangement.spacedBy(PM.s3)) {
            itemsIndexed(tracks, key = { i, t -> "$i-${t.id}" }) { i, t -> TrackCard(t, 140.dp) { onPlay(i) } }
        }
    }
}

@Composable
private fun ShelfShelf(title: String, shelves: List<Shelf>, nav: Nav) {
    Column {
        SectionTitle(title, Modifier.padding(top = PM.s3))
        LazyRow(contentPadding = PaddingValues(horizontal = PM.s4), horizontalArrangement = Arrangement.spacedBy(PM.s3)) {
            items(shelves, key = { it.ctx.key }) { s -> ShelfCard(s, 140.dp, { nav.open(s.ctx) }) }
        }
    }
}

@Composable
fun EmptyLibrary(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(PM.s8).padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.LibraryMusic, null, tint = PM.Gold, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(PM.s4))
        Text("Nenhuma música encontrada", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(PM.s2))
        Text(
            "Coloque arquivos de música (MP3, FLAC, M4A, OGG, WAV) em qualquer pasta do celular, como Music ou Download.",
            style = MaterialTheme.typography.bodyMedium, color = PM.TextDim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(PM.s4))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = PM.Gold, contentColor = PM.Black)) {
            Text("Procurar de novo")
        }
    }
}
