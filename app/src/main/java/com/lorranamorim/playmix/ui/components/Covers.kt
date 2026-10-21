package com.lorranamorim.playmix.ui.components

import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lorranamorim.playmix.data.ContextType
import com.lorranamorim.playmix.data.Track
import com.lorranamorim.playmix.ui.theme.PM

/** Capa com reserva bonita quando a música não tem imagem. */
@Composable
fun Cover(
    uri: Uri?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    fallbackIcon: ImageVector = Icons.Filled.MusicNote,
    seed: String = "",
) {
    Box(modifier.clip(shape)) {
        val fallback: @Composable () -> Unit = { CoverFallback(fallbackIcon, seed) }
        if (uri == null) fallback() else SubcomposeAsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { fallback() },
            error = { fallback() },
        )
    }
}

@Composable
private fun CoverFallback(icon: ImageVector, seed: String) {
    val tones = listOf(
        listOf(Color(0xFF3A2F12), Color(0xFF151517)),
        listOf(Color(0xFF2B2B33), Color(0xFF111114)),
        listOf(Color(0xFF4A3A10), Color(0xFF1C1A14)),
        listOf(Color(0xFF30303A), Color(0xFF0E0E10)),
    )
    val pair = tones[(seed.hashCode() and 0x7fffffff) % tones.size]
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(pair)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = PM.Gold.copy(alpha = 0.85f), modifier = Modifier.fillMaxSize(0.42f))
    }
}

/** Capa de playlist: mosaico com até 4 capas. */
@Composable
fun MosaicCover(tracks: List<Track>, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp), seed: String = "") {
    val distinct = tracks.distinctBy { it.albumId }
    Box(modifier.clip(shape)) {
        when {
            distinct.size >= 4 -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Cover(distinct[0].artUri, Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(0.dp), seed = distinct[0].album)
                    Cover(distinct[1].artUri, Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(0.dp), seed = distinct[1].album)
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Cover(distinct[2].artUri, Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(0.dp), seed = distinct[2].album)
                    Cover(distinct[3].artUri, Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(0.dp), seed = distinct[3].album)
                }
            }
            distinct.isNotEmpty() -> Cover(distinct[0].artUri, Modifier.fillMaxSize(), RoundedCornerShape(0.dp), seed = distinct[0].album)
            else -> Cover(null, Modifier.fillMaxSize(), RoundedCornerShape(0.dp), seed = seed)
        }
    }
}

/** Capa certa para cada tipo de item (curtidas, playlist, álbum, artista, mix). */
@Composable
fun ContextCover(type: ContextType, tracks: List<Track>, size: Dp, seed: String, modifier: Modifier = Modifier) {
    when (type) {
        ContextType.LIKED -> Box(
            modifier.size(size).clip(RoundedCornerShape(6.dp))
                .background(Brush.linearGradient(listOf(PM.GoldLight, PM.Gold, PM.GoldDeep))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Favorite, null, tint = PM.Black, modifier = Modifier.size(size * 0.4f)) }
        ContextType.ARTIST -> Cover(tracks.firstOrNull()?.artUri, modifier.size(size), CircleShape, Icons.Filled.Person, seed)
        ContextType.GENRE -> Box(modifier.size(size)) {
            MosaicCover(tracks, Modifier.fillMaxSize(), seed = seed)
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                    .background(Brush.verticalGradient(listOf(Color.Transparent, PM.Gold.copy(alpha = 0.55f)))),
            )
            Icon(Icons.Filled.GraphicEq, null, tint = PM.Text, modifier = Modifier.align(Alignment.BottomStart).size(size * 0.28f))
        }
        else -> MosaicCover(tracks, modifier.size(size), seed = seed)
    }
}

/** Cor dominante da capa, para os degradês. */
@Composable
fun rememberCoverColor(uri: Uri?, default: Color = PM.SurfaceHigh): Color {
    val context = LocalContext.current
    var color by remember(uri) { mutableStateOf(default) }
    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        val req = ImageRequest.Builder(context).data(uri).allowHardware(false).size(96).build()
        val result = context.imageLoader.execute(req)
        val bmp = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val p = Palette.from(bmp).generate()
        val sw = p.vibrantSwatch ?: p.dominantSwatch ?: p.mutedSwatch ?: return@LaunchedEffect
        color = Color(sw.rgb)
    }
    return color
}
