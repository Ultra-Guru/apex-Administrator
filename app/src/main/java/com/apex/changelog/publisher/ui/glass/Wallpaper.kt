package com.apex.changelog.publisher.ui.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
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
 *
 * v2.1 性能：相位以 ~24fps 节流更新（42ms 步进）—— 26s 慢漂移下与满帧
 * 无可感知差异，而底材层重录频次降 60%+，全部玻璃面随之减负。
 * 卡顿主因（v2.0）= 壁纸每帧重录 × N 个玻璃面每帧重渲染（blur+lens
 * RenderEffect 逐面逐帧执行）。相位锚定 System.nanoTime，配置重建 /
 * 重组后不跳变；模 1 循环保持连续漂移。
 */
@Composable
fun AuroraWallpaper(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    var phase by remember { mutableFloatStateOf(0f) }

    // ~24fps 相位驱动：42ms 步进；相位只在实际变化时才使 drawBehind
    // 失效重绘（下游玻璃面同步减负）。锚定 nanoTime —— 重组后不跳变。
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val anchor = System.nanoTime()
        while (true) {
            val elapsed = (System.nanoTime() - anchor).coerceAtLeast(0L)
            phase = (elapsed / 26_000_000_000f) % 1f
            delay(42)
        }
    }

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
