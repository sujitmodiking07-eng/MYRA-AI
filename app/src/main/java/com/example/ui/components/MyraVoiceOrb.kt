package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.example.data.model.OrbStyle
import com.example.ui.theme.MyraCyan
import com.example.ui.theme.MyraEmerald
import com.example.ui.theme.MyraViolet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class OrbVisualState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

@Composable
fun MyraVoiceOrb(
    orbStyle: OrbStyle,
    visualState: OrbVisualState,
    rmsLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 124.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "myra_orb_anim")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (visualState) {
                    OrbVisualState.LISTENING -> 900
                    OrbVisualState.THINKING -> 1100
                    OrbVisualState.SPEAKING -> 750
                    OrbVisualState.IDLE -> 2600
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (visualState) {
                    OrbVisualState.THINKING -> 2400
                    OrbVisualState.LISTENING -> 4200
                    OrbVisualState.SPEAKING -> 3400
                    OrbVisualState.IDLE -> 11000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )
    val primaryColor = when (visualState) {
        OrbVisualState.LISTENING -> Color(0xFFFFD700)
        OrbVisualState.SPEAKING -> Color(0xFFFFC247)
        OrbVisualState.THINKING -> Color(0xFFFFA500)
        OrbVisualState.IDLE -> Color(0xFFFFD700)
    }
    val secondaryColor = Color(0xFFB8860B)
    
    val stateLabel = when (visualState) {
        OrbVisualState.IDLE -> "Tap MYRA voice orb to speak"
        OrbVisualState.LISTENING -> "MYRA is listening, tap to stop"
        OrbVisualState.THINKING -> "MYRA is thinking"
        OrbVisualState.SPEAKING -> "MYRA is speaking, tap to stop"
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = primaryColor),
                onClick = onClick
            )
            .semantics { contentDescription = stateLabel }
            .testTag("myra_voice_orb"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxRadius = this.size.minDimension / 2f
            val rmsBoost = if (visualState == OrbVisualState.LISTENING) rmsLevel * 0.14f else 0f
            val coreRadius = maxRadius * (0.48f + pulse * 0.06f + rmsBoost)

            // Ambient outer halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.28f + pulse * 0.12f),
                        secondaryColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )

            when (orbStyle) {
                OrbStyle.CLASSIC -> {
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.28f),
                        radius = maxRadius * (0.76f + pulse * 0.07f),
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    rotate(rotation, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(Color.Transparent, primaryColor, secondaryColor, Color.Transparent),
                                center = center
                            ),
                            startAngle = 0f,
                            sweepAngle = 210f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                            topLeft = Offset(center.x - maxRadius * 0.68f, center.y - maxRadius * 0.68f),
                            size = androidx.compose.ui.geometry.Size(maxRadius * 1.36f, maxRadius * 1.36f)
                        )
                    }
                }

                OrbStyle.ENERGY -> {
                    // Harmonic plasma wave ring
                    val path = Path()
                    val points = 72
                    val waveAmp = maxRadius * (0.04f + (if (visualState != OrbVisualState.IDLE) 0.05f else 0.02f) + rmsBoost * 0.5f)
                    val baseRing = maxRadius * 0.72f
                    val phase = (rotation / 360f) * (2f * PI.toFloat())
                    for (i in 0..points) {
                        val theta = (i.toFloat() / points) * (2f * PI.toFloat())
                        val wave = sin(theta * 5f + phase) * waveAmp + cos(theta * 3f - phase) * (waveAmp * 0.6f)
                        val r = baseRing + wave
                        val x = center.x + r * cos(theta)
                        val y = center.y + r * sin(theta)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(
                        path = path,
                        brush = Brush.sweepGradient(
                            listOf(primaryColor, secondaryColor, primaryColor),
                            center = center
                        ),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                    rotate(-rotation * 0.6f, pivot = center) {
                        drawCircle(
                            color = secondaryColor.copy(alpha = 0.25f),
                            radius = maxRadius * 0.84f,
                            center = center,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }

                OrbStyle.NEON -> {
                    // Dual-ring neon equalizer ticks
                    val barCount = 24
                    val innerR = maxRadius * 0.64f
                    rotate(rotation * 0.4f, pivot = center) {
                        for (i in 0 until barCount) {
                            val angle = (i * (360f / barCount)) * (PI / 180.0)
                            val waveFactor = (sin(angle * 3.0 + (rotation * PI / 90.0)) + 1.0).toFloat() * 0.5f
                            val barLen = maxRadius * (0.07f + waveFactor * (if (visualState != OrbVisualState.IDLE) 0.16f else 0.08f) + rmsBoost * 0.4f)
                            val start = Offset(
                                center.x + innerR * cos(angle).toFloat(),
                                center.y + innerR * sin(angle).toFloat()
                            )
                            val end = Offset(
                                center.x + (innerR + barLen) * cos(angle).toFloat(),
                                center.y + (innerR + barLen) * sin(angle).toFloat()
                            )
                            drawLine(
                                color = if (i % 2 == 0) primaryColor else secondaryColor,
                                start = start,
                                end = end,
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

            // Luminous inner core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        primaryColor,
                        secondaryColor.copy(alpha = 0.85f)
                    ),
                    center = Offset(center.x - coreRadius * 0.25f, center.y - coreRadius * 0.25f),
                    radius = coreRadius * 1.4f
                ),
                radius = coreRadius,
                center = center
            )
        }

        Icon(
            imageVector = when (visualState) {
                OrbVisualState.LISTENING -> Icons.Filled.Stop
                OrbVisualState.SPEAKING -> Icons.Filled.GraphicEq
                else -> Icons.Filled.Mic
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(28.dp)
        )
    }
}
