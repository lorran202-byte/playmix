package com.lorranamorim.playmix.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lorranamorim.playmix.data.ContextType
import com.lorranamorim.playmix.data.LyricLine
import com.lorranamorim.playmix.data.PlayContext
import com.lorranamorim.playmix.data.Track
import com.lorranamorim.playmix.playback.SleepTimer
import com.lorranamorim.playmix.playback.isUserQueued
import com.lorranamorim.playmix.ui.AppViewModel
import com.lorranamorim.playmix.ui.LyricsState
import com.lorranamorim.playmix.ui.components.Cover
import com.lorranamorim.playmix.ui.components.formatTime
import com.lorranamorim.playmix.ui.components.rememberCoverColor
import com.lorranamorim.playmix.ui.theme.PM
import com.lorranamorim.playmix.ui.theme.glass
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Posição atual da música, atualizada várias vezes por segundo. */
@Composable
fun rememberPositionMs(vm: AppViewModel): Long {
    var pos by remember { mutableLongStateOf(vm.playerConn.positionMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            pos = vm.playerConn.positionMs()
            delay(250)
        }
    }
    return pos
}

private val MediaItem.titleText get() = mediaMetadata.title?.toString() ?: ""
private val MediaItem.artistText get() = mediaMetadata.artist?.toString() ?: ""

// ======================= MINI PLAYER =======================

@Composable
fun MiniPlayer(vm: AppViewModel, onExpand: () -> Unit) {
    val player by vm.player.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val item = player.current ?: return
    val id = player.currentId
    val pos = rememberPositionMs(vm)
    val tint = rememberCoverColor(item.mediaMetadata.artworkUri, PM.SurfaceHigh)
    var dragX by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .padding(horizontal = PM.s2)
            .fillMaxWidth()
            .height(60.dp)
            .glass(RoundedCornerShape(10.dp), tint = tint)
            .clickable(onClick = onExpand)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragX < -90f) vm.playerConn.next() else if (dragX > 90f) vm.playerConn.previous()
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                ) { _, delta -> dragX += delta }
            },
    ) {
        Row(Modifier.fillMaxSize().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(item.mediaMetadata.artworkUri, Modifier.size(44.dp), RoundedCornerShape(4.dp), seed = item.mediaMetadata.albumTitle?.toString() ?: "")
            Spacer(Modifier.width(PM.s3))
            Column(Modifier.weight(1f)) {
                Text(item.titleText, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.artistText, style = MaterialTheme.typography.bodySmall, color = PM.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (id != null) {
                val liked = data.liked.contains(id)
                IconButton(onClick = { vm.toggleLike(id) }) {
                    Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Curtir", tint = if (liked) PM.Gold else PM.Text)
                }
            }
            IconButton(onClick = { vm.playerConn.togglePlay() }) {
                Icon(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (player.isPlaying) "Pausar" else "Tocar", tint = PM.Text, modifier = Modifier.size(30.dp))
            }
        }
        val dur = player.durationMs
        val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
        Box(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp).fillMaxWidth().height(2.dp)
                .background(Color.White.copy(alpha = 0.15f)),
        ) {
            Box(Modifier.fillMaxWidth(frac).height(2.dp).background(PM.Gold))
        }
    }
}

// ======================= TOCANDO AGORA =======================

@Composable
fun NowPlayingScreen(
    vm: AppViewModel,
    onCollapse: () -> Unit,
    onTrackMenu: (Track) -> Unit,
    onOpen: (PlayContext) -> Unit,
) {
    val player by vm.player.collectAsStateWithLifecycle()
    val lib by vm.library.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val item = player.current ?: return
    val track = player.currentId?.let { lib.byId[it] }
    val pos = rememberPositionMs(vm)
    val color = rememberCoverColor(item.mediaMetadata.artworkUri, PM.SurfaceHigh)
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val dur = player.durationMs.takeIf { it > 0 } ?: track?.durationMs ?: 0L

    BackHandler(enabled = showLyrics) { showLyrics = false }

    Box(
        Modifier.fillMaxSize()
            .background(PM.Bg)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.75f), color.copy(alpha = 0.25f), PM.Bg)))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()),
        ) {
            // topo
            Row(Modifier.fillMaxWidth().padding(horizontal = PM.s2), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) { Icon(Icons.Filled.KeyboardArrowDown, "Minimizar", tint = PM.Text, modifier = Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TOCANDO DE", style = MaterialTheme.typography.labelMedium, color = PM.TextDim, letterSpacing = 1.5.sp)
                    Text(
                        player.contextTitle.ifBlank { "Sua biblioteca" },
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { track?.let(onTrackMenu) }, enabled = track != null) {
                    Icon(Icons.Filled.MoreVert, "Mais opções", tint = PM.Text)
                }
            }

            // capa
            Cover(
                item.mediaMetadata.artworkUri,
                Modifier.padding(horizontal = PM.s6, vertical = PM.s6).fillMaxWidth().aspectRatio(1f),
                RoundedCornerShape(10.dp),
                seed = item.mediaMetadata.albumTitle?.toString() ?: "",
            )

            // título + curtir
            Row(Modifier.fillMaxWidth().padding(horizontal = PM.s6), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.titleText, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.artistText, style = MaterialTheme.typography.bodyLarge, color = PM.TextDim, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = track != null) {
                            track?.let { onOpen(PlayContext(ContextType.ARTIST, it.artist, it.artist)) }
                        },
                    )
                }
                if (track != null) {
                    val liked = data.liked.contains(track.id)
                    IconButton(onClick = { vm.toggleLike(track.id) }) {
                        Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Curtir", tint = if (liked) PM.Gold else PM.Text, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // barra de progresso
            val frac = dragFrac ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
            SeekBar(
                fraction = frac,
                onDrag = { dragFrac = it },
                onRelease = {
                    dragFrac?.let { vm.playerConn.seekTo((it * dur).toLong()) }
                    dragFrac = null
                },
                modifier = Modifier.padding(horizontal = PM.s6).padding(top = PM.s4),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = PM.s6)) {
                Text(formatTime((frac * dur).toLong()), style = MaterialTheme.typography.bodySmall, color = PM.TextDim)
                Spacer(Modifier.weight(1f))
                Text("-" + formatTime(dur - (frac * dur).toLong()), style = MaterialTheme.typography.bodySmall, color = PM.TextDim)
            }

            // controles fixos (nunca mudam de lugar)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = PM.s4, vertical = PM.s2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.playerConn.toggleShuffle() }) {
                    Icon(Icons.Filled.Shuffle, "Aleatório", tint = if (player.shuffle) PM.Gold else PM.Text, modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = { vm.playerConn.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Anterior", tint = PM.Text, modifier = Modifier.size(40.dp))
                }
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(PM.Text).clickable { vm.playerConn.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (player.isPlaying) "Pausar" else "Tocar", tint = PM.Black, modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { vm.playerConn.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, "Próxima", tint = PM.Text, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { vm.playerConn.cycleRepeat() }) {
                    Icon(
                        if (player.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        "Repetir", tint = if (player.repeatMode != Player.REPEAT_MODE_OFF) PM.Gold else PM.Text, modifier = Modifier.size(26.dp),
                    )
                }
            }

            // timer + fila
            Row(Modifier.fillMaxWidth().padding(horizontal = PM.s4), verticalAlignment = Alignment.CenterVertically) {
                val sleep = player.sleep
                Row(
                    Modifier.clip(RoundedCornerShape(50)).clickable { showSleep = true }.padding(PM.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Bedtime, "Timer de soneca", tint = if (sleep != SleepTimer.Off) PM.Gold else PM.TextDim, modifier = Modifier.size(22.dp))
                    if (sleep != SleepTimer.Off) {
                        Spacer(Modifier.width(6.dp))
                        Text(sleepLabel(sleep), style = MaterialTheme.typography.labelMedium, color = PM.Gold)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, "Fila", tint = PM.Text)
                }
            }

            // cartão da letra
            LyricsCard(lyrics, pos, onOpen = { showLyrics = true }, onRetry = { vm.retryLyrics() }, tint = color)
            Spacer(Modifier.height(PM.s8))
        }

        AnimatedVisibility(visible = showLyrics, enter = fadeIn(), exit = fadeOut()) {
            LyricsFull(lyrics, pos, color, item, onClose = { showLyrics = false }, onSeek = { vm.playerConn.seekTo(it) })
        }
    }

    if (showQueue) QueueSheet(vm) { showQueue = false }
    if (showSleep) SleepDialog(vm, player.sleep) { showSleep = false }
}

private fun sleepLabel(s: SleepTimer): String = when (s) {
    SleepTimer.Off -> ""
    SleepTimer.EndOfTrack -> "Fim da faixa"
    is SleepTimer.At -> {
        val left = ((s.endsAtMs - System.currentTimeMillis()) / 60000L).coerceAtLeast(0) + 1
        "$left min"
    }
}

private fun currentLineIndex(lines: List<LyricLine>, pos: Long): Int {
    var idx = -1
    for (i in lines.indices) if (lines[i].timeMs <= pos + 250) idx = i else break
    return idx
}

@Composable
private fun LyricsCard(state: LyricsState, pos: Long, onOpen: () -> Unit, onRetry: () -> Unit, tint: Color) {
    Column(
        Modifier.padding(horizontal = PM.s4, vertical = PM.s4).fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint)
            .clickable(enabled = state is LyricsState.Ready, onClick = onOpen)
            .padding(PM.s4),
    ) {
        Text("Letra", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(PM.s3))
        when (state) {
            LyricsState.Idle, LyricsState.Loading -> Text("Buscando letra…", color = PM.TextDim)
            LyricsState.NotFound -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Letra não encontrada para esta música.", color = PM.TextDim, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text("Tentar de novo", color = PM.Gold) }
            }
            is LyricsState.Ready -> {
                val l = state.lyrics
                if (l.isSynced) {
                    val cur = currentLineIndex(l.synced, pos)
                    val start = cur.coerceAtLeast(0)
                    l.synced.drop(start).take(4).forEachIndexed { i, line ->
                        Text(
                            line.text.ifBlank { "♪" },
                            style = MaterialTheme.typography.titleLarge,
                            color = if (i == 0 && cur >= 0) PM.Text else PM.Text.copy(alpha = 0.45f),
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                } else {
                    Text(
                        l.plain.orEmpty().lines().take(6).joinToString("\n"),
                        style = MaterialTheme.typography.titleMedium, color = PM.Text.copy(alpha = 0.85f),
                    )
                }
                Spacer(Modifier.height(PM.s2))
                Text("Toque para ver a letra completa", style = MaterialTheme.typography.labelMedium, color = PM.TextDim)
            }
        }
    }
}

@Composable
private fun LyricsFull(state: LyricsState, pos: Long, tint: Color, item: MediaItem, onClose: () -> Unit, onSeek: (Long) -> Unit) {
    val listState = rememberLazyListState()
    val ready = state as? LyricsState.Ready
    val synced = ready?.lyrics?.synced.orEmpty()
    val cur = currentLineIndex(synced, pos)

    LaunchedEffect(cur) {
        if (cur >= 0) listState.animateScrollToItem((cur - 2).coerceAtLeast(0))
    }

    Box(
        Modifier.fillMaxSize()
            .background(tint)
            .background(Brush.verticalGradient(listOf(Color.Transparent, PM.Bg.copy(alpha = 0.6f))))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(PM.s2), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Fechar letra", tint = PM.Text, modifier = Modifier.size(32.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(item.titleText, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.artistText, style = MaterialTheme.typography.bodyMedium, color = PM.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (synced.isNotEmpty()) {
                LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = PM.s6, vertical = PM.s8)) {
                    itemsIndexed(synced) { i, line ->
                        Text(
                            line.text.ifBlank { "♪" },
                            fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
                            color = when {
                                i == cur -> PM.Text
                                i < cur -> PM.Text.copy(alpha = 0.55f)
                                else -> PM.Black.copy(alpha = 0.55f)
                            },
                            modifier = Modifier.fillMaxWidth().clickable { onSeek(line.timeMs) }.padding(vertical = 8.dp),
                        )
                    }
                }
            } else {
                Text(
                    ready?.lyrics?.plain.orEmpty(),
                    fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(PM.s6),
                )
            }
        }
    }
}

// ======================= FILA =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val player by vm.player.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val current = player.current
    val base = player.index + 1

    // chave estável: id + ocorrência (a mesma música pode estar duas vezes)
    fun keyed(list: List<MediaItem>): List<Pair<String, MediaItem>> {
        val seen = HashMap<String, Int>()
        return list.map { m ->
            val n = seen.getOrDefault(m.mediaId, 0)
            seen[m.mediaId] = n + 1
            "${m.mediaId}#$n" to m
        }
    }

    var upcoming by remember(player.queue, player.index) {
        mutableStateOf(keyed(if (player.index >= 0) player.queue.drop(base) else emptyList()))
    }
    val userCount = upcoming.takeWhile { it.second.isUserQueued }.size

    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey = to.key as? String ?: return@rememberReorderableLazyListState
        val f = upcoming.indexOfFirst { it.first == fromKey }
        val t = upcoming.indexOfFirst { it.first == toKey }
        if (f < 0 || t < 0) return@rememberReorderableLazyListState
        upcoming = upcoming.toMutableList().apply { add(t, removeAt(f)) }
        vm.playerConn.move(base + f, base + t)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = PM.Surface) {
        Column(Modifier.navigationBarsPadding().heightIn(max = 640.dp)) {
            Text("Fila", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = PM.s4))
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = PM.s6)) {
                item(key = "h_now") { QueueHeader("Tocando agora") }
                if (current != null) item(key = "now") { QueueRow(current, isNow = true, onClick = {}, onRemove = null, handle = null) }

                upcoming.forEachIndexed { i, (key, m) ->
                    if (i == 0 && userCount > 0) item(key = "h_user") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QueueHeader("Próximas na fila", Modifier.weight(1f))
                        }
                    }
                    if (i == userCount) item(key = "h_ctx") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QueueHeader("Próximas de: ${player.contextTitle.ifBlank { "sua biblioteca" }}", Modifier.weight(1f))
                            TextButton(onClick = { vm.playerConn.clearUpcoming() }) { Text("Limpar", color = PM.TextDim) }
                        }
                    }
                    item(key = key) {
                        ReorderableItem(reorder, key = key) { dragging ->
                            QueueRow(
                                m,
                                isNow = false,
                                dragging = dragging,
                                onClick = { vm.playerConn.skipTo(base + i) },
                                onRemove = { vm.playerConn.removeAt(base + i) },
                                handle = Modifier.draggableHandle(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(horizontal = PM.s4, vertical = PM.s3))
}

@Composable
private fun QueueRow(
    m: MediaItem,
    isNow: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    handle: Modifier?,
    dragging: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (dragging) PM.SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = PM.s4, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(m.mediaMetadata.artworkUri, Modifier.size(44.dp), RoundedCornerShape(4.dp), seed = m.mediaMetadata.albumTitle?.toString() ?: "")
        Spacer(Modifier.width(PM.s3))
        Column(Modifier.weight(1f)) {
            Text(m.titleText, color = if (isNow) PM.Gold else PM.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(m.artistText, color = PM.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remover da fila", tint = PM.TextDim, modifier = Modifier.size(20.dp)) }
        }
        if (handle != null) {
            IconButton(onClick = {}, modifier = handle) { Icon(Icons.Filled.DragHandle, "Arrastar", tint = PM.TextDim) }
        } else Spacer(Modifier.width(PM.s4))
    }
}

// ======================= TIMER =======================

@Composable
private fun SleepDialog(vm: AppViewModel, current: SleepTimer, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PM.Surface,
        title = { Text("Timer de soneca") },
        text = {
            Column {
                listOf(5, 15, 30, 45, 60).forEach { min ->
                    Text(
                        "$min minutos", style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { vm.playerConn.setSleepMinutes(min); onDismiss() }.padding(vertical = PM.s3),
                    )
                }
                Text(
                    "Fim da faixa", style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clickable { vm.playerConn.setSleepEndOfTrack(); onDismiss() }.padding(vertical = PM.s3),
                )
                if (current != SleepTimer.Off) {
                    Text(
                        "Desligar timer", style = MaterialTheme.typography.bodyLarge, color = PM.Gold,
                        modifier = Modifier.fillMaxWidth().clickable { vm.playerConn.cancelSleep(); onDismiss() }.padding(vertical = PM.s3),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}


/** Barra de progresso fina, no estilo dos players de streaming: toque ou arraste para pular. */
@Composable
private fun SeekBar(fraction: Float, onDrag: (Float) -> Unit, onRelease: () -> Unit, modifier: Modifier = Modifier) {
    var width by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures(onPress = { o ->
                    onDrag((o.x / width).coerceIn(0f, 1f))
                    tryAwaitRelease()
                    onRelease()
                })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; onDrag((o.x / width).coerceIn(0f, 1f)) },
                    onDragEnd = { dragging = false; onRelease() },
                    onDragCancel = { dragging = false; onRelease() },
                ) { change, _ -> onDrag((change.position.x / width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val h = if (dragging) 6.dp else 4.dp
        Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f)))
        Box(Modifier.fillMaxWidth(fraction).height(h).clip(RoundedCornerShape(50)).background(PM.Gold))
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .wrapContentWidth(Alignment.End),
        ) {
            Box(Modifier.offset(x = 6.dp).size(if (dragging) 16.dp else 12.dp).clip(CircleShape).background(PM.Text))
        }
    }
}
