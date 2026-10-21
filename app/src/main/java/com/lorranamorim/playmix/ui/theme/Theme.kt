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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.lorranamorim.playmix.R
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
    val SilverLight = Color(0xFFE3E3EA)
    val Text = Color(0xFFF5F5F7)
    val TextDim = Color(0xFFB4B4BF) // prata: textos e ícones secundários (contraste ~9:1)
    val Divider = Color(0x33C0C0C8)

    // espaçamento em múltiplos de 4
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s6 = 24.dp
    val s8 = 32.dp

    /** Cantos grandes e arredondados, no estilo "liquid glass". */
    val rCard = 20.dp
    val rTile = 14.dp
    val rSmall = 10.dp
}

/** Cor ambiente: tirada da capa da música que está tocando; tinge o fundo do app inteiro. */
val LocalAmbient = androidx.compose.runtime.staticCompositionLocalOf { PM.GoldDeep }

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

/** Fonte aberta (OFL) no estilo dos apps de música modernos. */
val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

private fun t(size: Int, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f, line: Int = 0) = TextStyle(
    fontFamily = Jakarta,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeight = if (line > 0) line.sp else TextStyle.Default.lineHeight,
)

private val typography = Typography(
    displaySmall = t(36, FontWeight.ExtraBold, -1f, 42),
    headlineLarge = t(32, FontWeight.ExtraBold, -0.8f, 38),
    headlineMedium = t(26, FontWeight.Bold, -0.5f, 32),
    headlineSmall = t(22, FontWeight.Bold, -0.3f, 28),
    titleLarge = t(20, FontWeight.Bold, -0.2f, 26),
    titleMedium = t(16, FontWeight.SemiBold, 0f, 22),
    titleSmall = t(14, FontWeight.SemiBold, 0f, 20),
    bodyLarge = t(16, FontWeight.Normal, 0f, 22),
    bodyMedium = t(14, FontWeight.Normal, 0f, 20),
    bodySmall = t(12, FontWeight.Medium, 0f, 16),
    labelLarge = t(14, FontWeight.SemiBold),
    labelMedium = t(12, FontWeight.SemiBold),
    labelSmall = t(11, FontWeight.SemiBold, 0.5f),
)

@Composable
fun PlayMixTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // texto claro e fonte da marca por padrão em todas as telas
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
