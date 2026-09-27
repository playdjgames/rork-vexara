package com.rork.vexaraandroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rork.vexaraandroid.R

/** Central visual tokens for the neon synthwave identity (mirrors the iPhone Theme). */
object Vex {
    val voidDeep = Color(0xFF0B0518)
    val voidBase = Color(0xFF160A2E)
    val panel = Color(0xFF1F0F3D)

    val cyan = Color(0xFF2FE8FF)
    val magenta = Color(0xFFFF2FD4)
    val ember = Color(0xFFFF7A1A)
    val amber = Color(0xFFFFB020)
    val violet = Color(0xFFB983FF)
    val acid = Color(0xFF4DFFB0)
    val danger = Color(0xFFFF3B5C)

    val textPrimary = Color(0xFFF5F0FF)
    val textSecondary = Color(0xFFB9A6D9)

    /** Standard playfield aspect. Wide screens letterbox to keep an arcade-shaped battlefield. */
    const val PLAYFIELD_ASPECT = 0.58f
}

val DisplayFamily = FontFamily(Font(R.font.nunito_black, FontWeight.Black))
val LabelFamily = FontFamily(Font(R.font.nunito_extrabold, FontWeight.ExtraBold))
val BodyFamily = FontFamily(Font(R.font.nunito_semibold, FontWeight.SemiBold))
val NumeralFamily = FontFamily(Font(R.font.jetbrains_mono_bold, FontWeight.Bold))

private fun glowShadow(glow: Color?, radius: Float, intensity: Float): Shadow? =
    glow?.let {
        Shadow(
            color = it.copy(alpha = (0.85f * intensity).coerceIn(0f, 1f)),
            offset = Offset.Zero,
            blurRadius = radius * 2.6f
        )
    }

fun displayStyle(
    size: Float,
    tracking: Float = 0f,
    color: Color = Vex.textPrimary,
    glow: Color? = null,
    glowRadius: Float = 10f,
    intensity: Float = 1f
): TextStyle = TextStyle(
    fontFamily = DisplayFamily,
    fontWeight = FontWeight.Black,
    fontSize = size.sp,
    letterSpacing = tracking.sp,
    color = color,
    shadow = glowShadow(glow, glowRadius, intensity)
)

fun labelStyle(
    size: Float,
    tracking: Float = 0f,
    color: Color = Vex.textPrimary,
    glow: Color? = null,
    glowRadius: Float = 10f,
    intensity: Float = 1f
): TextStyle = TextStyle(
    fontFamily = LabelFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = size.sp,
    letterSpacing = tracking.sp,
    color = color,
    shadow = glowShadow(glow, glowRadius, intensity)
)

fun numeralStyle(
    size: Float,
    color: Color = Vex.textPrimary,
    glow: Color? = null,
    glowRadius: Float = 10f,
    intensity: Float = 1f
): TextStyle = TextStyle(
    fontFamily = NumeralFamily,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    color = color,
    shadow = glowShadow(glow, glowRadius, intensity)
)

fun bodyStyle(size: Float, color: Color = Vex.textSecondary): TextStyle = TextStyle(
    fontFamily = BodyFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = size.sp,
    lineHeight = (size * 1.3f).sp,
    color = color
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Vex.cyan,
            secondary = Vex.magenta,
            background = Vex.voidDeep,
            surface = Vex.panel,
            onBackground = Vex.textPrimary,
            onSurface = Vex.textPrimary
        ),
        content = content
    )
}
