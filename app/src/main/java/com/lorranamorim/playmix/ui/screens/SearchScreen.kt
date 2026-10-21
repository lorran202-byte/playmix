package com.lorranamorim.playmix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorranamorim.playmix.data.ContextType
import com.lorranamorim.playmix.data.PlayContext
import com.lorranamorim.playmix.ui.AppViewModel
import com.lorranamorim.playmix.ui.Nav
import com.lorranamorim.playmix.ui.components.SectionTitle
import com.lorranamorim.playmix.ui.components.ShelfRow
import com.lorranamorim.playmix.ui.components.TrackRow
import com.lorranamorim.playmix.ui.theme.PM
import java.text.Normalizer

private val tileColors = listOf(
    Color(0xFF8C6E1C), Color(0xFF3F4A5C), Color(0xFF6B2E3A), Color(0xFF2E5A4C),
    Color(0xFF5B3F7A), Color(0xFF7A4B1E), Color(0xFF2F4F7F), Color(0xFF4A4A52),
)

fun normalize(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

@Composable
fun SearchScreen(vm: AppViewModel, nav: Nav, bottomSpace: Dp) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current

    val q = normalize(query.trim())
    val results = remember(q, lib, data) {
        if (q.isEmpty()) null else {
            val words = q.split(' ').filter { it.isNotBlank() }
            fun match(vararg fields: String) = normalize(fields.joinToString(" ")).let { f -> words.all { f.contains(it) } }
            SearchResults(
                tracks = lib.tracks.filter { match(it.title, it.artist, it.album) }.take(30),
                artists = lib.artists.filter { match(it.name) }.take(8).map { vm.artistShelf(it) },
                albums = lib.albums.filter { match(it.title, it.artist) }.take(8).map { vm.albumShelf(it) },
                playlists = data.playlists.filter { match(it.name) }.map { vm.playlistShelf(it, lib) },
            )
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(PM.Bg), contentPadding = PaddingValues(bottom = bottomSpace + 24.dp)) {
        item {
            Text(
                "Buscar", style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.statusBarsPadding().padding(start = PM.s4, top = PM.s3, bottom = PM.s3),
            )
        }
        item {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("O que você quer ouvir?", color = Color(0xFF444448)) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color(0xFF222225)) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Limpar", tint = Color(0xFF222225)) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = PM.Text, unfocusedContainerColor = PM.Text,
                    focusedTextColor = PM.Black, unfocusedTextColor = PM.Black,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = PM.GoldDeep,
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = PM.s4),
            )
        }

        if (results == null) {
            item { SectionTitle("Navegar por tudo", Modifier.padding(top = PM.s3)) }
            val tiles = buildList {
                add(PlayContext(ContextType.ALL, "all", "Todas as músicas"))
                add(PlayContext(ContextType.LIKED, "liked", "Curtidas"))
                lib.genres.forEach { add(PlayContext(ContextType.GENRE, it, it)) }
            }
            items(tiles.chunked(2)) { row ->
                Row(Modifier.padding(horizontal = PM.s4, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { ctx ->
                        val color = tileColors[(ctx.title.hashCode() and 0x7fffffff) % tileColors.size]
                        Box(
                            Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.6f))))
                                .clickable { nav.open(ctx) }
                                .padding(PM.s3),
                        ) {
                            Text(ctx.title, style = MaterialTheme.typography.titleMedium, color = PM.Text)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            if (results.isEmpty) item {
                Text(
                    "Nada encontrado para \"$query\"", color = PM.TextDim,
                    modifier = Modifier.fillMaxWidth().padding(PM.s8), style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (results.tracks.isNotEmpty()) {
                item { SectionTitle("Músicas") }
                items(results.tracks, key = { "t${it.id}" }) { t ->
                    TrackRow(
                        t, isCurrent = player.currentId == t.id, liked = data.liked.contains(t.id),
                        onClick = { focus.clearFocus(); vm.playSingle(t, results.tracks, "Busca") },
                        onMore = { nav.trackMenu(t, null) },
                    )
                }
            }
            if (results.artists.isNotEmpty()) {
                item { SectionTitle("Artistas") }
                items(results.artists, key = { "a${it.ctx.key}" }) { s -> ShelfRow(s, false, { nav.open(s.ctx) }) }
            }
            if (results.albums.isNotEmpty()) {
                item { SectionTitle("Álbuns") }
                items(results.albums, key = { "al${it.ctx.key}" }) { s -> ShelfRow(s, false, { nav.open(s.ctx) }) }
            }
            if (results.playlists.isNotEmpty()) {
                item { SectionTitle("Playlists") }
                items(results.playlists, key = { "p${it.ctx.key}" }) { s -> ShelfRow(s, false, { nav.open(s.ctx) }) }
            }
        }
    }
}

private data class SearchResults(
    val tracks: List<com.lorranamorim.playmix.data.Track>,
    val artists: List<com.lorranamorim.playmix.ui.Shelf>,
    val albums: List<com.lorranamorim.playmix.ui.Shelf>,
    val playlists: List<com.lorranamorim.playmix.ui.Shelf>,
) {
    val isEmpty get() = tracks.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()
}
