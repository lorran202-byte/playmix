package com.lorranamorim.playmix.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PM {
    val Black = Color(0xFF000000)
    val Bg = Color(0xFF0A0A0B)
    val Surface = Color(0xFF151517)
    val SurfaceHigh = Color(0xFF222225)
    val Gold = Color(0xFFD4AF37)
    val GoldLight = Color(0xFFF2D27A)
    val GoldDeep = Color(0xFF8C6E1C)
    val Silver = Color(0xFFC0C0C8)
    val Text = Color(0xFFF5F5F7)
    val TextDim = Color(0xFFA7A7AD) // contraste ~8:1 sobre o fundo
    val Divider = Color(0x22FFFFFF)

    // espaçamento em múltiplos de 4
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s6 = 24.dp
    val s8 = 32.dp
}

private val scheme = darkColorScheme(
    primary = PM.Gold,
    onPrimary = PM.Black,
    primaryContainer = PM.GoldDeep,
    onPrimaryContainer = PM.Text,
    secondary = PM.Silver,
    onSecondary = PM.Black,
    background = PM.Bg,
    onBackground = PM.Text,
    surface = PM.Surface,
    onSurface = PM.Text,
    surfaceVariant = PM.SurfaceHigh,
    onSurfaceVariant = PM.TextDim,
    surfaceContainer = PM.Surface,
    surfaceContainerHigh = PM.SurfaceHigh,
    surfaceContainerHighest = PM.SurfaceHigh,
    surfaceContainerLow = PM.Surface,
    outline = PM.Divider,
)

private val typography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun PlayMixTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // texto claro por padrão em todas as telas
        CompositionLocalProvider(LocalContentColor provides PM.Text, content = content)
    }
}

/** Efeito "vidro líquido": fundo translúcido com brilho prateado na borda. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(16.dp), tint: Color = PM.Surface): Modifier =
    this
        .clip(shape)
        .background(
            Brush.verticalGradient(listOf(tint.copy(alpha = 0.42f), tint.copy(alpha = 0.28f))),
            shape,
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.04f), PM.Silver.copy(alpha = 0.10f), PM.Gold.copy(alpha = 0.25f))),
            shape = shape,
        )

val GoldBrush = Brush.linearGradient(listOf(PM.GoldLight, PM.Gold, PM.GoldDeep))
