package com.rork.vexaraandroid.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.rork.vexaraandroid.R
import com.rork.vexaraandroid.model.BossKind
import com.rork.vexaraandroid.model.BulletKind
import com.rork.vexaraandroid.model.EnemyKind
import com.rork.vexaraandroid.model.EnemyState
import com.rork.vexaraandroid.model.PI_F
import com.rork.vexaraandroid.model.ParticleKind
import com.rork.vexaraandroid.model.PowerUpKind
import com.rork.vexaraandroid.ui.components.icon
import com.rork.vexaraandroid.ui.theme.DisplayFamily
import com.rork.vexaraandroid.ui.theme.Vex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private class SpriteAtlas(
    val player: ImageBitmap,
    val enemies: Map<EnemyKind, ImageBitmap>,
    val bosses: Map<BossKind, ImageBitmap>
)

private fun deg(radians: Float): Float = radians * 180f / PI_F

/**
 * Immediate-mode renderer for the whole playfield. The world is drawn in dp units
 * (matching the iPhone's points) and redrawn every simulation tick.
 */
@Composable
fun Battlefield(engine: GameEngine, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val atlas = remember(resources) {
        SpriteAtlas(
            player = ImageBitmap.imageResource(resources, R.drawable.starfighter_top_view),
            enemies = EnemyKind.entries.associateWith { ImageBitmap.imageResource(resources, it.sprite) },
            bosses = BossKind.entries.associateWith { ImageBitmap.imageResource(resources, it.sprite) }
        )
    }
    val glyphs: Map<PowerUpKind, VectorPainter> = PowerUpKind.entries.associateWith { rememberVectorPainter(it.icon) }
    val measurer = rememberTextMeasurer()

    Canvas(modifier) {
        if (engine.frameToken < 0) return@Canvas
        val d = density
        val shakeOffset = if (engine.shake > 0.2f) {
            val m = min(engine.shake, 30f)
            Offset(rnd(-m, m) * 0.5f, rnd(-m, m) * 0.5f)
        } else {
            Offset.Zero
        }

        withTransform({
            scale(d, d, Offset.Zero)
            translate(shakeOffset.x, shakeOffset.y)
        }) {
            drawBackdrop(engine)
            drawSiphonBeams(engine)
            drawBossBeam(engine)
            drawBoss(engine, atlas)
            drawEnemies(engine, atlas)
            drawDrops(engine)
            drawBullets(engine)
            drawPlayer(engine, atlas)
            drawParticles(engine)
        }

        // Pixel-space pass so glyphs and text stay crisp.
        translate(shakeOffset.x * d, shakeOffset.y * d) {
            drawDropGlyphs(engine, glyphs, d)
            drawFloatingTexts(engine, measurer, d)
        }

        if (engine.flashAlpha > 0f) {
            drawRect(Vex.textPrimary.copy(alpha = (engine.flashAlpha * 0.45f).coerceIn(0f, 1f)), blendMode = BlendMode.Plus)
        }
    }
}

// Helpers

private fun DrawScope.glow(center: Offset, radius: Float, color: Color, alpha: Float) {
    if (radius <= 0f || alpha <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha.coerceIn(0f, 1f)), color.copy(alpha = 0f)), center, radius),
        radius = radius,
        center = center
    )
}

private fun DrawScope.sprite(
    image: ImageBitmap,
    center: Offset,
    box: Float,
    degrees: Float,
    alpha: Float,
    tint: ColorFilter? = null
) {
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    val w: Float
    val h: Float
    if (iw >= ih) {
        w = box
        h = box * ih / iw
    } else {
        h = box
        w = box * iw / ih
    }
    rotate(degrees, center) {
        translate(center.x - w / 2f, center.y - h / 2f) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(max(1, w.roundToInt()), max(1, h.roundToInt())),
                alpha = alpha.coerceIn(0f, 1f),
                colorFilter = tint
            )
        }
    }
}

// Backdrop

private fun DrawScope.drawBackdrop(engine: GameEngine) {
    for (piece in engine.debris) {
        val w = piece.size
        val h = piece.size * 0.78f
        rotate(deg(piece.rotation), piece.position) {
            drawRoundRect(
                color = Color(0xFF1A0F33).copy(alpha = 0.55f * piece.depth),
                topLeft = Offset(piece.position.x - w / 2f, piece.position.y - h / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(piece.size * 0.3f)
            )
        }
    }
    for (star in engine.stars) {
        val pulse = 0.55f + 0.45f * sin(star.twinkle)
        drawCircle(star.color.copy(alpha = (star.depth * pulse).coerceIn(0f, 1f)), star.size / 2f, star.position)
    }
}

// Player

private fun DrawScope.drawPlayer(engine: GameEngine, atlas: SpriteAtlas) {
    val player = engine.player

    // Rescued wing-mates fly escort.
    if (player.wingmateTimer > 0f && player.alive) {
        for (side in listOf(-1f, 1f)) {
            val position = Offset(player.position.x + side * 40f, player.position.y + 16f)
            drawEngineFlare(engine, Offset(position.x, position.y + 16f), 0.5f)
            drawShip(atlas, position, 0.6f, player.tilt * 0.6f, 0.9f)
        }
    }

    if (!player.alive) return

    // Blink while respawn-invulnerable.
    if (player.invulnerable > 0f && (engine.elapsed * 14f).toInt() % 2 == 0) return

    drawEngineFlare(engine, Offset(player.position.x, player.position.y + 24f), 1f)
    drawShip(atlas, player.position, 1f, player.tilt, 1f)

    if (player.shielded) {
        val radius = 32f
        val pulse = 0.55f + 0.25f * sin(engine.elapsed * 6f)
        drawCircle(Vex.acid.copy(alpha = 0.1f), radius, player.position)
        drawCircle(Vex.acid.copy(alpha = pulse), radius, player.position, style = Stroke(2f))
    }

    // Siphon lock indicator.
    if (player.siphonProgress > 0.02f) {
        val radius = 38f
        drawArc(
            color = Vex.magenta,
            startAngle = -90f,
            sweepAngle = 360f * player.siphonProgress,
            useCenter = false,
            topLeft = Offset(player.position.x - radius, player.position.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(3f, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawShip(atlas: SpriteAtlas, position: Offset, scale: Float, tilt: Float, opacity: Float) {
    val box = 54f * scale
    glow(position, box * 0.62f, Vex.cyan, 0.32f * opacity)
    sprite(atlas.player, position, box, deg(tilt * 0.34f), opacity)
}

private fun DrawScope.drawEngineFlare(engine: GameEngine, position: Offset, scale: Float) {
    val flicker = 0.7f + 0.3f * sin(engine.elapsed * 34f)
    val length = 26f * scale * flicker
    val width = 11f * scale
    for (offset in listOf(-7.5f * scale, 7.5f * scale)) {
        val left = position.x + offset - width / 2f
        val top = position.y - 4f
        drawOval(
            brush = Brush.verticalGradient(
                listOf(Vex.textPrimary.copy(alpha = 0.95f), Vex.cyan.copy(alpha = 0.75f), Vex.cyan.copy(alpha = 0f)),
                startY = top,
                endY = top + length
            ),
            topLeft = Offset(left, top),
            size = Size(width, length)
        )
    }
}

// Enemies

private val hitTint = ColorFilter.tint(Color.White.copy(alpha = 0.55f), BlendMode.SrcAtop)

private fun DrawScope.drawEnemies(engine: GameEngine, atlas: SpriteAtlas) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
    for (enemy in engine.enemies) {
        if (enemy.position.y <= -90f || enemy.position.y >= engine.height + 90f) continue
        val image = atlas.enemies[enemy.kind] ?: continue

        // Materialize-in: eased fade with a scale-up and a brief accent shimmer.
        val fade = min(1f, enemy.spawnFade)
        val eased = 1f - (1f - fade) * (1f - fade)
        val size = enemy.kind.radius * 2.35f * (0.72f + 0.28f * eased)

        if (fade < 1f) {
            val shimmer = size * (1.15f + (1f - eased) * 0.9f)
            drawCircle(enemy.kind.accent.copy(alpha = (1f - eased) * 0.3f), shimmer / 2f, enemy.position)
        }

        val flashing = enemy.hitFlash > 0f
        glow(
            enemy.position,
            size * (if (flashing) 0.8f else 0.66f),
            if (flashing) Vex.textPrimary else enemy.kind.accent,
            (if (flashing) 0.55f else 0.28f) * eased
        )
        sprite(image, enemy.position, size, deg(enemy.rotation * 0.55f), eased, if (flashing) hitTint else null)

        // Captured ship rides under its captor.
        if (enemy.holdsCapturedShip) {
            val below = Offset(enemy.position.x, enemy.position.y + enemy.kind.radius * 1.9f)
            drawLine(Vex.magenta.copy(alpha = 0.6f), enemy.position, below, strokeWidth = 2f, pathEffect = dash)
            glow(below, 26f, Vex.cyan, 0.35f)
            sprite(atlas.player, below, 40f, 180f, 0.9f)
        }

        // Armour pips for multi-hit enemies still holding damage.
        if (enemy.maxHealth > 2 && enemy.health < enemy.maxHealth) {
            val barWidth = enemy.kind.radius * 1.8f
            val fraction = enemy.health.toFloat() / enemy.maxHealth.toFloat()
            val origin = Offset(enemy.position.x - barWidth / 2f, enemy.position.y - enemy.kind.radius - 8f)
            drawRoundRect(Color.Black.copy(alpha = 0.5f), origin, Size(barWidth, 3f), CornerRadius(1.5f))
            drawRoundRect(enemy.kind.accent, origin, Size(barWidth * fraction, 3f), CornerRadius(1.5f))
        }
    }
}

private fun DrawScope.drawSiphonBeams(engine: GameEngine) {
    for (enemy in engine.enemies) {
        if (enemy.state != EnemyState.SIPHON_BEAM) continue
        val halfWidth = 34f
        val top = enemy.position.y + enemy.kind.radius
        val bottom = engine.height
        if (bottom <= top) continue

        val pulse = 0.55f + 0.25f * sin(engine.elapsed * 11f)
        val left = enemy.position.x - halfWidth
        val right = enemy.position.x + halfWidth
        val beamHeight = bottom - top

        drawRect(
            brush = Brush.verticalGradient(
                listOf(Vex.magenta.copy(alpha = 0.55f * pulse), Vex.magenta.copy(alpha = 0.22f * pulse), Vex.magenta.copy(alpha = 0.05f)),
                startY = top,
                endY = bottom
            ),
            topLeft = Offset(left, top),
            size = Size(halfWidth * 2f, beamHeight)
        )

        for (edge in listOf(left, right)) {
            drawLine(Vex.magenta.copy(alpha = 0.8f * pulse), Offset(edge, top), Offset(edge, bottom), strokeWidth = 1.5f)
        }

        val bands = 5
        for (index in 0 until bands) {
            val phase = (engine.elapsed * 0.9f + index.toFloat() / bands) % 1f
            val y = top + beamHeight * phase
            drawRect(Vex.textPrimary.copy(alpha = 0.35f * (1f - phase)), Offset(left, y), Size(halfWidth * 2f, 3f))
        }
    }
}

// Boss

private fun DrawScope.drawBoss(engine: GameEngine, atlas: SpriteAtlas) {
    val boss = engine.boss ?: return
    val image = atlas.bosses[boss.kind] ?: return

    val opacity = if (boss.dying) {
        if ((engine.elapsed * 22f).toInt() % 2 == 0) 1f else 0.35f
    } else {
        1f
    }
    val sway = sin(boss.driftPhase * 0.8f) * 0.045f
    val flashing = boss.hitFlash > 0f

    glow(boss.position, boss.width * 0.6f, if (flashing) Vex.textPrimary else boss.kind.accent, (if (flashing) 0.4f else 0.24f) * opacity)
    sprite(image, boss.position, boss.width, deg(sway), opacity, if (flashing) hitTint else null)

    // Weak-point cores.
    for (core in boss.cores) {
        val center = Offset(boss.position.x + core.offset.x, boss.position.y + core.offset.y)
        val pulse = 0.6f + 0.4f * sin(engine.elapsed * 4f + core.radius)
        val radius = core.radius * (if (core.flash > 0f) 1.25f else 1f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Vex.textPrimary.copy(alpha = 0.95f * pulse), boss.kind.accent.copy(alpha = 0.7f * pulse), boss.kind.accent.copy(alpha = 0f)),
                center,
                radius
            ),
            radius = radius,
            center = center
        )
        drawCircle(boss.kind.accent.copy(alpha = 0.9f), radius, center, style = Stroke(1.5f))
    }

    // Hive pods.
    if (boss.kind == BossKind.HIVE_CORE) {
        for (pod in boss.pods) {
            val center = podPosition(boss, pod)
            val radius = boss.width * 0.11f

            if (pod.health <= 0) {
                drawCircle(Color(0xFF3A2A1A).copy(alpha = 0.6f), radius * 0.5f, center, style = Stroke(1.5f))
                continue
            }

            drawLine(boss.kind.accent.copy(alpha = 0.3f), boss.position, center, strokeWidth = 1.5f)
            drawCircle(if (pod.hitFlash > 0f) Vex.textPrimary else Color(0xFF2A1E12), radius, center)
            drawCircle(boss.kind.accent.copy(alpha = 0.9f), radius, center, style = Stroke(2f))
            drawCircle(boss.kind.accent, radius * 0.45f, center)

            val fraction = pod.health.toFloat() / pod.maxHealth.toFloat()
            val ring = radius + 4f
            drawArc(
                color = boss.kind.accent.copy(alpha = 0.8f),
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(center.x - ring, center.y - ring),
                size = Size(ring * 2f, ring * 2f),
                style = Stroke(2f, cap = StrokeCap.Round)
            )
        }
    }
}

private fun DrawScope.drawBossBeam(engine: GameEngine) {
    val boss = engine.boss ?: return
    val beam = boss.beam ?: return

    val origin = Offset(boss.position.x, boss.position.y + boss.width * 0.18f)
    val length = engine.height * 1.6f
    val end = Offset(origin.x + sin(beam.angle) * length, origin.y + cos(beam.angle) * length)

    if (beam.charge > 0f) {
        // Telegraph line so the sweep is always dodgeable.
        val progress = 1f - beam.charge / 1.1f
        drawLine(
            color = boss.kind.accent.copy(alpha = (0.25f + 0.4f * progress).coerceIn(0f, 1f)),
            start = origin,
            end = end,
            strokeWidth = 2f + 3f * progress,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        )
        val chargeRadius = boss.width * 0.1f * (0.4f + progress)
        if (chargeRadius > 0f) {
            drawCircle(
                brush = Brush.radialGradient(listOf(Vex.textPrimary, boss.kind.accent, boss.kind.accent.copy(alpha = 0f)), origin, chargeRadius),
                radius = chargeRadius,
                center = origin
            )
        }
        return
    }

    if (beam.active <= 0f) return

    val flicker = 0.85f + 0.15f * sin(engine.elapsed * 40f)
    val halfWidth = beam.width * 0.5f * flicker
    val nx = cos(beam.angle) * halfWidth
    val ny = -sin(beam.angle) * halfWidth

    val body = Path().apply {
        moveTo(origin.x + nx, origin.y + ny)
        lineTo(end.x + nx, end.y + ny)
        lineTo(end.x - nx, end.y - ny)
        lineTo(origin.x - nx, origin.y - ny)
        close()
    }
    drawPath(
        path = body,
        brush = Brush.linearGradient(
            listOf(boss.kind.accent.copy(alpha = 0.85f), boss.kind.accent.copy(alpha = 0.45f), boss.kind.accent.copy(alpha = 0.12f)),
            start = origin,
            end = end
        )
    )
    drawLine(Vex.textPrimary.copy(alpha = 0.95f), origin, end, strokeWidth = halfWidth * 0.5f, cap = StrokeCap.Round)
}

// Bullets

private fun DrawScope.drawBullets(engine: GameEngine) {
    for (bullet in engine.bullets) {
        val color = bullet.kind.color
        val length = bullet.kind.length
        val width = bullet.kind.width

        if (bullet.kind == BulletKind.ENEMY_BOMB) {
            val radius = width
            val pulse = 0.7f + 0.3f * sin(engine.elapsed * 14f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Vex.textPrimary, color, color.copy(alpha = 0.1f)),
                    bullet.position,
                    radius * (pulse + 0.4f)
                ),
                radius = radius,
                center = bullet.position
            )
            drawCircle(color.copy(alpha = 0.9f), radius, bullet.position, style = Stroke(1.5f))
            continue
        }

        // Orient the bolt along its velocity so angled shots read correctly.
        val angle = atan2(bullet.vy, bullet.vx)
        rotate(deg(angle - PI_F / 2f), bullet.position) {
            val top = bullet.position.y - length / 2f
            drawRoundRect(
                color = color.copy(alpha = 0.28f),
                topLeft = Offset(bullet.position.x - width * 1.5f, top - 2f),
                size = Size(width * 3f, length + 4f),
                cornerRadius = CornerRadius(width * 1.5f)
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Vex.textPrimary, color, color.copy(alpha = 0.35f)),
                    startY = top,
                    endY = top + length
                ),
                topLeft = Offset(bullet.position.x - width / 2f, top),
                size = Size(width, length),
                cornerRadius = CornerRadius(width / 2f)
            )
        }
    }
}

// Drops

private fun DrawScope.drawDrops(engine: GameEngine) {
    for (drop in engine.drops) {
        val radius = 15f
        val bob = sin(engine.elapsed * 5f + drop.spin) * 2f
        val center = Offset(drop.position.x, drop.position.y + bob)
        val pulse = 0.6f + 0.4f * sin(engine.elapsed * 6f + drop.spin)

        glow(center, radius * 1.8f, drop.kind.accent, 0.35f * pulse)
        val topLeft = Offset(center.x - radius, center.y - radius)
        drawRoundRect(Vex.voidDeep.copy(alpha = 0.88f), topLeft, Size(radius * 2f, radius * 2f), CornerRadius(5f))
        drawRoundRect(drop.kind.accent, topLeft, Size(radius * 2f, radius * 2f), CornerRadius(5f), style = Stroke(2f))
    }
}

private fun DrawScope.drawDropGlyphs(engine: GameEngine, glyphs: Map<PowerUpKind, VectorPainter>, d: Float) {
    val glyphSize = 16f * d
    for (drop in engine.drops) {
        val painter = glyphs[drop.kind] ?: continue
        val bob = sin(engine.elapsed * 5f + drop.spin) * 2f
        val cx = drop.position.x * d
        val cy = (drop.position.y + bob) * d
        translate(cx - glyphSize / 2f, cy - glyphSize / 2f) {
            with(painter) {
                draw(Size(glyphSize, glyphSize), colorFilter = ColorFilter.tint(drop.kind.accent))
            }
        }
    }
}

// Particles

private fun DrawScope.drawParticles(engine: GameEngine) {
    for (particle in engine.particles) {
        val progress = max(0f, particle.life / particle.maxLife)
        when (particle.kind) {
            ParticleKind.RING -> {
                val radius = particle.size * (1f - progress) + 6f
                drawCircle(
                    color = particle.color.copy(alpha = (progress * 0.8f).coerceIn(0f, 1f)),
                    radius = radius,
                    center = particle.position,
                    style = Stroke(2.5f * progress + 0.5f)
                )
            }
            else -> {
                val size = particle.size * (0.4f + progress * 0.6f)
                drawCircle(particle.color.copy(alpha = progress.coerceIn(0f, 1f)), size / 2f, particle.position)
            }
        }
    }
}

private fun DrawScope.drawFloatingTexts(engine: GameEngine, measurer: TextMeasurer, d: Float) {
    for (item in engine.floatingTexts) {
        val progress = max(0f, item.life / item.maxLife).coerceIn(0f, 1f)
        val layout = measurer.measure(
            text = item.text,
            style = TextStyle(
                fontFamily = DisplayFamily,
                fontWeight = FontWeight.Black,
                fontSize = (item.fontSize * d / fontScale / density).sp,
                color = item.color
            )
        )
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                item.position.x * d - layout.size.width / 2f,
                item.position.y * d - layout.size.height / 2f
            ),
            alpha = progress
        )
    }
}
