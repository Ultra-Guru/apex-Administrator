package com.apex.changelog.publisher.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 极光壁纸 —— 全局液态玻璃的「折射底材」。
 *
 * 薄荷 × 薰衣草 × 暖金三团柔光在底色上缓慢漂移（26s 周期），
 * 被上方所有玻璃面（模糊 / 折射 / 色彩提升）实时采样。
 * 亮暗两套浓度：暗色更低饱和，保证玻璃上文字的可读性。
 */
@Composable
fun AuroraWallpaper(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val p = (phase * 2.0 * PI).toFloat()

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                val m = min(w, h)

                drawRect(
                    Brush.verticalGradient(
                        if (dark) {
                            listOf(Color(0xFF0C1210), Color(0xFF0D1420))
                        } else {
                            listOf(Color(0xFFF4FBF7), Color(0xFFEAF3FA))
                        }
                    )
                )

                fun blob(cx: Float, cy: Float, radiusFactor: Float, color: Color) {
                    val center = Offset(cx * w, cy * h)
                    val radius = radiusFactor * m
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color, Color.Transparent),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                }

                if (dark) {
                    blob(
                        0.30f + 0.18f * cos(p),
                        0.28f + 0.10f * sin(p),
                        0.90f,
                        Color(0xFF1F6B52).copy(alpha = 0.50f)
                    )
                    blob(
                        0.72f - 0.15f * sin(p * 0.8f),
                        0.45f + 0.12f * cos(p * 0.9f),
                        0.78f,
                        Color(0xFF4A3D8F).copy(alpha = 0.40f)
                    )
                    blob(
                        0.50f + 0.10f * cos(p * 0.7f),
                        0.86f,
                        0.70f,
                        Color(0xFF8A6A3F).copy(alpha = 0.20f)
                    )
                } else {
                    blob(
                        0.28f + 0.16f * cos(p),
                        0.25f + 0.10f * sin(p),
                        0.95f,
                        Color(0xFF7FD4A8).copy(alpha = 0.48f)
                    )
                    blob(
                        0.74f - 0.14f * sin(p * 0.8f),
                        0.42f + 0.12f * cos(p * 0.9f),
                        0.82f,
                        Color(0xFFC3B5F0).copy(alpha = 0.42f)
                    )
                    blob(
                        0.50f + 0.08f * cos(p * 0.7f),
                        0.88f,
                        0.75f,
                        Color(0xFFF5D8A8).copy(alpha = 0.34f)
                    )
                }
            }
    )
}
