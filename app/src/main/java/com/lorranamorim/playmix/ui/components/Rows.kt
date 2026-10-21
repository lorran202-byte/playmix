package com.lorranamorim.playmix.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import com.lorranamorim.playmix.ui.theme.GoldBrush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lorranamorim.playmix.data.ContextType
import com.lorranamorim.playmix.data.Track
import com.lorranamorim.playmix.ui.Shelf
import com.lorranamorim.playmix.ui.theme.PM

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    liked: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    showArt: Boolean = true,
    number: Int? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = PM.s4, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArt) {
            Cover(track.artUri, Modifier.size(48.dp), RoundedCornerShape(4.dp), seed = track.album)
            Spacer(Modifier.width(PM.s3))
        } else if (number != null) {
            Text(
                "$number", style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) PM.Gold else PM.TextDim, modifier = Modifier.width(28.dp),
            )
            Spacer(Modifier.width(PM.s2))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    Icon(Icons.Filled.GraphicEq, null, tint = PM.Gold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrent) PM.Gold else PM.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (liked) {
                    Icon(Icons.Filled.Favorite, null, tint = PM.Gold, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PM.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Filled.MoreVert, "Mais opções", tint = PM.TextDim)
        }
    }
}

/** Linha da biblioteca (playlist, álbum, artista). */
@Composable
fun ShelfRow(shelf: Shelf, pinned: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickableCompat(onClick, onLongClick)
            .padding(horizontal = PM.s4, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContextCover(shelf.ctx.type, shelf.artTracks, 60.dp, shelf.ctx.title)
        Spacer(Modifier.width(PM.s3))
        Column(Modifier.weight(1f)) {
            Text(
                shelf.ctx.title, style = MaterialTheme.typography.bodyLarge, color = PM.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pinned) {
                    Icon(Icons.Filled.PushPin, null, tint = PM.Gold, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(shelf.subtitle, style = MaterialTheme.typography.bodyMedium, color = PM.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Cartão quadrado (grade e carrosséis). */
@Composable
fun ShelfCard(shelf: Shelf, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Column(
        Modifier.width(size).combinedClickableCompat(onClick, onLongClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ContextCover(shelf.ctx.type, shelf.artTracks, size, shelf.ctx.title)
        Text(
            shelf.ctx.title, style = MaterialTheme.typography.titleSmall, color = PM.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            shelf.subtitle, style = MaterialTheme.typography.bodySmall, color = PM.TextDim,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TrackCard(track: Track, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(Modifier.width(size).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Cover(track.artUri, Modifier.size(size), RoundedCornerShape(6.dp), seed = track.album)
        Text(track.title, style = MaterialTheme.typography.titleSmall, color = PM.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = PM.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = PM.Text,
        modifier = modifier.padding(horizontal = PM.s4, vertical = PM.s3),
    )
}

@Composable
fun Avatar(letter: String, modifier: Modifier = Modifier) {
    Box(
        modifier.size(32.dp).clip(CircleShape).background(GoldBrush),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = PM.Black, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)
