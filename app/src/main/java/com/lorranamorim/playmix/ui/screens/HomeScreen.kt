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
import com.lorranamorim.playmix.ui.theme.GoldBrush
import com.lorranamorim.playmix.ui.components.GlassCircleButton
import com.lorranamorim.playmix.ui.components.GlassPill
import com.lorranamorim.playmix.ui.components.GlassRim
import com.lorranamorim.playmix.ui.components.frost
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import java.util.Calendar

@Composable
fun HomeScreen(vm: AppViewModel, nav: Nav, bottomSpace: Dp) {
    val home by vm.home.collectAsStateWithLifecycle()
    val lib by vm.library.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    var showProfile by remember { mutableStateOf(false) }
    if (showProfile) ProfileDialog(vm) { showProfile = false }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomSpace + 24.dp)) {
            item {
                // marca à esquerda, foto de perfil à direita
                Row(
                    Modifier.statusBarsPadding().fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "PlayMix",
                        style = MaterialTheme.typography.headlineLarge.copy(brush = GoldBrush),
                        modifier = Modifier.weight(1f),
                    )
                    ProfileAvatar(vm, 42.dp, Modifier.clickable { showProfile = true })
                }
                val name = data.settings.profileName.trim()
                Text(
                    if (name.isBlank()) greeting() else "${greeting()}, ${name.substringBefore(' ')}",
                    style = MaterialTheme.typography.titleMedium, color = PM.TextDim,
                    modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 6.dp).clickable { showProfile = true },
                )
            }

            when {
                !lib.loaded -> item {
                    Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PM.Gold)
                    }
                }
                lib.tracks.isEmpty() -> item { EmptyLibrary { vm.refreshLibrary() } }
                else -> {
                    // destaque: cartões grandes com os mixes do dia
                    val heroes = home.mixes.ifEmpty { home.shortcuts.filter { it.ctx.type != ContextType.LIKED && it.ctx.type != ContextType.ARTIST } }.take(8)
                    if (heroes.isNotEmpty()) item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = PM.s3, bottom = PM.s3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Seu mix do dia", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                            GlassPill("Tocar tudo", {
                                vm.playContext(PlayContext(ContextType.ALL, "all", "Todas as músicas"), shuffle = true)
                            }, leading = Icons.Filled.Shuffle)
                        }
                        LazyRow(contentPadding = PaddingValues(horizontal = PM.s4), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(heroes, key = { "hero" + it.ctx.key }) { s -> HeroCard(s, onOpen = { nav.open(s.ctx) }, onPlay = { vm.playContext(s.ctx) }) }
                        }
                    }

                    item { SectionTitle("Atalhos", Modifier.padding(top = PM.s4)) }
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
                        Modifier.weight(1f).height(60.dp).frost(RoundedCornerShape(PM.rTile), 0.07f).clickable { nav.open(s.ctx) }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ContextCover(s.ctx.type, s.artTracks, 48.dp, s.ctx.title)
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
            itemsIndexed(tracks, key = { i, t -> "$i-${t.id}" }) { i, t -> TrackCard(t, 150.dp) { onPlay(i) } }
        }
    }
}

@Composable
private fun ShelfShelf(title: String, shelves: List<Shelf>, nav: Nav) {
    Column {
        SectionTitle(title, Modifier.padding(top = PM.s3))
        LazyRow(contentPadding = PaddingValues(horizontal = PM.s4), horizontalArrangement = Arrangement.spacedBy(PM.s3)) {
            items(shelves, key = { it.ctx.key }) { s -> ShelfCard(s, 150.dp, { nav.open(s.ctx) }) }
        }
    }
}

/** Cartão grande do destaque: capa de ponta a ponta, título por cima e botão de play de vidro. */
@Composable
private fun HeroCard(s: Shelf, onOpen: () -> Unit, onPlay: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier.size(280.dp).clip(shape).border(0.8.dp, GlassRim, shape).clickable(onClick = onOpen),
    ) {
        ContextCover(s.ctx.type, s.artTracks, 280.dp, s.ctx.title)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.35f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)),
            ),
        )
        Column(Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Text(s.ctx.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.subtitle, style = MaterialTheme.typography.bodySmall, color = PM.SilverLight, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            GlassCircleButton(Icons.Filled.PlayArrow, "Tocar", onPlay, size = 46.dp)
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
