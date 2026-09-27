package com.rork.vexaraandroid.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rork.vexaraandroid.R
import com.rork.vexaraandroid.services.AudioEngine
import com.rork.vexaraandroid.services.Haptics
import com.rork.vexaraandroid.services.Sfx
import com.rork.vexaraandroid.ui.theme.Vex
import com.rork.vexaraandroid.ui.theme.displayStyle
import com.rork.vexaraandroid.ui.theme.labelStyle
import java.util.Locale

/** Formats a score with fixed-width arcade zero padding. */
fun arcadeScore(value: Int, digits: Int = 6): String =
    String.format(Locale.US, "%0${digits}d", minOf(value, 9_999_999))

/** Soft neon halo drawn outside a rounded shape (capsule when [corner] is null). */
fun Modifier.neonHalo(color: Color, strength: Float = 1f, corner: Dp? = null): Modifier = drawBehind {
    val steps = 6
    val spread = 2.2.dp.toPx()
    val baseRadius = corner?.toPx() ?: (size.height / 2f)
    for (i in 1..steps) {
        val e = spread * i
        val alpha = (0.1f * strength * (1f - (i - 1).toFloat() / steps)).coerceIn(0f, 1f)
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(-e, -e),
            size = Size(size.width + 2 * e, size.height + 2 * e),
            cornerRadius = CornerRadius(baseRadius + e),
            style = Stroke(width = spread * 1.2f)
        )
    }
}

/** Plays the shared UI tap feedback. */
fun uiTap() {
    Haptics.tap()
    AudioEngine.play(Sfx.UI_TAP)
}

/** Neon-outlined capsule used for every HUD readout. */
@Composable
fun HudPill(accent: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .neonHalo(accent, 0.5f)
            .clip(shape)
            .background(Vex.voidDeep.copy(alpha = 0.62f))
            .border(1.dp, accent.copy(alpha = 0.55f), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { content() }
}

/** The primary arcade action button: a glowing capsule with press feedback. */
@Composable
fun NeonButton(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = Vex.cyan,
    filled: Boolean = false,
    showsChevron: Boolean = true,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "press"
    )
    val shape = RoundedCornerShape(50)
    val background = if (filled) {
        Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.72f)))
    } else {
        Brush.verticalGradient(listOf(Vex.panel.copy(alpha = 0.85f), Vex.voidDeep.copy(alpha = 0.75f)))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .neonHalo(accent, if (filled) 1.3f else 0.6f)
            .clip(shape)
            .background(background)
            .border(1.5.dp, accent.copy(alpha = if (filled) 0.9f else 0.55f), shape)
            .clickable(interactionSource = interaction, indication = null) {
                uiTap()
                onClick()
            }
            .semantics {
                role = Role.Button
                contentDescription = title
            }
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = labelStyle(17f, 2f, color = if (filled) Vex.voidDeep else Vex.textPrimary),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (showsChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (filled) Vex.voidDeep.copy(alpha = 0.8f) else accent,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** The VEXARA marquee with a ruled kicker below. */
@Composable
fun VexaraWordmark(scale: Float = 1f, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics { contentDescription = "Vexara" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((7 * scale).dp)
    ) {
        Text(
            text = "VEXARA",
            maxLines = 1,
            style = displayStyle(
                size = 60f * scale,
                tracking = 10f * scale,
                glow = Vex.magenta,
                glowRadius = 18f * scale,
                intensity = 0.8f
            ).copy(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFFEAFBFF), Vex.cyan, Vex.magenta, Color(0xFF9B1E86))
                )
            )
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((9 * scale).dp)
        ) {
            Box(
                Modifier
                    .width((34 * scale).dp)
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Vex.cyan.copy(alpha = 0f), Vex.cyan.copy(alpha = 0.75f))))
            )
            Text(
                text = "NEON VOID ASSAULT",
                maxLines = 1,
                style = labelStyle(11f * scale, 4f * scale, color = Vex.textSecondary)
            )
            Box(
                Modifier
                    .width((34 * scale).dp)
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Vex.magenta.copy(alpha = 0.75f), Vex.magenta.copy(alpha = 0f))))
            )
        }
    }
}

/** Elevated panel used by summary and menu surfaces. */
@Composable
fun NeonPanel(modifier: Modifier = Modifier, accent: Color = Vex.cyan, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .neonHalo(accent, 0.45f, corner = 18.dp)
            .clip(shape)
            .background(Vex.panel.copy(alpha = 0.72f))
            .border(1.dp, accent.copy(alpha = 0.4f), shape)
    ) { content() }
}

/** Small circular control used for pause and toggle affordances. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    accent: Color = Vex.textPrimary,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Vex.voidDeep.copy(alpha = 0.66f))
            .border(1.dp, accent.copy(alpha = 0.35f), CircleShape)
            .clickable {
                uiTap()
                onClick()
            }
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
    }
}

/** Slow-drifting nebula backdrop used behind every surface. */
@Composable
fun MenuBackdrop(modifier: Modifier = Modifier, imageRes: Int = R.drawable.space_nebula_bg, dim: Float = 0.34f) {
    val transition = rememberInfiniteTransition(label = "backdrop")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = -26f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )
    Box(modifier.fillMaxSize().background(Vex.voidBase)) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.08f
                    scaleY = 1.08f
                    translationY = drift * density
                }
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Vex.voidDeep.copy(alpha = 0.2f),
                            Vex.voidDeep.copy(alpha = (dim + 0.2f).coerceAtMost(1f)),
                            Vex.voidDeep.copy(alpha = (dim + 0.5f).coerceAtMost(1f))
                        )
                    )
                )
        )
    }
}

/** Section spacer helper. */
@Composable
fun VSpace(height: Dp) {
    Spacer(Modifier.height(height))
}
