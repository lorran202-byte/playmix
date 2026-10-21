package com.lorranamorim.playmix.ui.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorranamorim.playmix.data.Track
import com.lorranamorim.playmix.ui.AppViewModel
import com.lorranamorim.playmix.ui.theme.PM

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(
    track: Track,
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onGoAlbum: (Long) -> Unit,
    onGoArtist: (String) -> Unit,
    playlistId: String? = null,
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val liked = data.liked.contains(track.id)
    var showAdd by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = PM.Surface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = PM.s4)) {
            Row(Modifier.padding(horizontal = PM.s4, vertical = PM.s2), verticalAlignment = Alignment.CenterVertically) {
                Cover(track.artUri, Modifier.size(52.dp), RoundedCornerShape(4.dp), seed = track.album)
                Spacer(Modifier.width(PM.s3))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${track.artist} • ${track.album}", style = MaterialTheme.typography.bodyMedium, color = PM.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = PM.Divider, modifier = Modifier.padding(vertical = PM.s2))
            MenuItem(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (liked) "Remover das Curtidas" else "Curtir") {
                vm.toggleLike(track.id); onDismiss()
            }
            MenuItem(Icons.Filled.SkipNext, "Tocar a seguir") { vm.playerConn.playNext(track); onDismiss() }
            MenuItem(Icons.AutoMirrored.Filled.QueueMusic, "Adicionar à fila") { vm.playerConn.addToQueue(track); onDismiss() }
            MenuItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Adicionar à playlist") { showAdd = true }
            if (playlistId != null) {
                MenuItem(Icons.Filled.PlaylistRemove, "Remover desta playlist") { vm.removeFromPlaylist(playlistId, track.id); onDismiss() }
            }
            MenuItem(Icons.Filled.Album, "Ir para o álbum") { onDismiss(); onGoAlbum(track.albumId) }
            MenuItem(Icons.Filled.Person, "Ir para o artista") { onDismiss(); onGoArtist(track.primaryArtist) }
            MenuItem(Icons.Filled.Share, "Compartilhar arquivo") {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, track.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(Intent.createChooser(send, track.title)) }
                onDismiss()
            }
        }
    }

    if (showAdd) {
        AddToPlaylistDialog(vm, listOf(track.id)) { showAdd = false; onDismiss() }
    }
}

@Composable
fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = PM.s4, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = PM.TextDim)
        Spacer(Modifier.width(PM.s4))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun AddToPlaylistDialog(vm: AppViewModel, trackIds: List<Long>, onDone: () -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        NameDialog("Nova playlist", "", "Criar", onDismiss = onDone) { name ->
            vm.createPlaylist(name, trackIds)
            onDone()
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDone,
        containerColor = PM.Surface,
        title = { Text("Adicionar à playlist") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = PM.s3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Add, null, tint = PM.Gold)
                        Spacer(Modifier.width(PM.s3))
                        Text("Nova playlist", color = PM.Gold, style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(data.playlists, key = { it.id }) { p ->
                    Text(
                        p.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable {
                            vm.addToPlaylist(p.id, trackIds)
                            onDone()
                        }.padding(vertical = PM.s3),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text("Cancelar") } },
    )
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PM.Surface,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Nome da playlist") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PM.Gold, cursorColor = PM.Gold),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text(confirm, color = PM.Gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
