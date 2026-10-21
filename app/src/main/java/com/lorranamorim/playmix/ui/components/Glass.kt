package com.lorranamorim.playmix.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lorranamorim.playmix.ui.theme.PM

/** Borda de vidro: brilho branco no alto, quase some no meio, um toque dourado embaixo. */
val GlassRim = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.04f), PM.Gold.copy(alpha = 0.22f)),
)

/** Superfície de vidro fosco (sem desfoque real; para desfoque use haze). */
fun Modifier.frost(shape: Shape, alpha: Float = 0.10f): Modifier =
    this
        .clip(shape)
        .background(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = alpha + 0.04f), Color.White.copy(alpha = alpha * 0.6f))),
            shape,
        )
        .border(0.8.dp, GlassRim, shape)

/** Botão redondo de vidro, usado nos cabeçalhos e no player. */
@Composable
fun GlassCircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = PM.Text,
    enabled: Boolean = true,
) {
    Box(
        modifier.size(size).frost(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (enabled) tint else tint.copy(alpha = 0.4f), modifier = Modifier.size(size * 0.48f))
    }
}

/** Pílula de vidro com texto (filtros, ordenação, "Tocar tudo"). */
@Composable
fun GlassPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leading: ImageVector? = null,
) {
    val bg by animateColorAsState(if (selected) PM.Gold else Color.Transparent, label = "pill")
    Row(
        modifier
            .frost(RoundedCornerShape(50))
            .background(bg, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = if (selected) PM.Black else PM.Text, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) PM.Black else PM.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Cabeçalho das telas: título grande à esquerda, botões redondos de vidro à direita. */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.statusBarsPadding().fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Grupo segmentado de vidro (ex.: lista/grade): o item escolhido ganha um círculo claro. */
@Composable
fun GlassSegmented(
    options: List<Pair<ImageVector, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.frost(RoundedCornerShape(50)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, (icon, desc) ->
            val on = i == selected
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(if (on) PM.Text else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, desc, tint = if (on) PM.Black else PM.Text, modifier = Modifier.size(20.dp)) }
        }
    }
}
