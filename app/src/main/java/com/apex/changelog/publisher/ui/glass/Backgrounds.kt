package com.apex.changelog.publisher.ui.glass

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  背景层（v2.2）—— 可自定义背景 + 玻璃联动折射的底材
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 三类背景（均被 layerBackdrop 采样 → 玻璃的模糊 / 折射 / 流光实时取材）：
 * - [DriftingWallpaper]：三团柔光 Lissajous 漂移（极光 + 4 套渐变预设）；
 * - [ImageWallpaper]：相册自定义图片 + 极缓 Ken Burns 推拉 —— 静态图也有
 *   「光在玻璃上流动」的折射动感；
 * - 生命周期感知：App 退到后台（< RESUMED）时相位冻结 —— 不浪费一度电，
 *   回前台无缝续漂（增量推进，无锚点跳变）。
 */

/**
 * 背景风格（可持久化）：极光（默认）/ 渐变预设 / 自定义图片。
 * [key] 即持久化字符串（PublisherPrefs.backgroundStyle）。
 */
sealed interface BackgroundStyle {
    val key: String

    /** 默认极光（薄荷 × 薰衣草 × 暖金）。 */
    data object Aurora : BackgroundStyle {
        override val key: String get() = "aurora"
    }

    /** 内置渐变预设（深海 / 落日 / 兰紫 / 石墨）。 */
    data class Preset(val presetKey: String) : BackgroundStyle {
        override val key: String get() = "preset:$presetKey"
    }

    /** 相册自定义图片（BackgroundStore 持久化，玻璃调谐随图派生）。 */
    data object Image : BackgroundStyle {
        override val key: String get() = "image"
    }

    companion object {
        fun fromKey(key: String?): BackgroundStyle = when {
            key == null || key == Aurora.key -> Aurora
            key.startsWith("preset:") -> Preset(key.removePrefix("preset:"))
            key == Image.key -> Image
            else -> Aurora
        }
    }
}

/** 渐变预设：基底渐变 + 三团柔光（亮暗各一套）+ 派生玻璃调谐。 */
data class GradientPreset(
    val key: String,
    val label: String,
    val baseLight: List<Color>,
    val baseDark: List<Color>,
    val blobsLight: List<Color>,
    val blobsDark: List<Color>,
    val tuning: GlassTuning
)

/** 全部内置背景预设（首个 = 默认极光，与 v2.1 视觉同源）。 */
object GradientPresets {

    /** 薄荷 × 薰衣草 × 暖金（默认 —— 延续 v2.1 极光配色）。 */
    val Aurora = GradientPreset(
        key = "aurora",
        label = "极光",
        baseLight = listOf(Color(0xFFF4FBF7), Color(0xFFEAF3FA)),
        baseDark = listOf(Color(0xFF0C1210), Color(0xFF0D1420)),
        blobsLight = listOf(
            Color(0xFF7FD4A8).copy(alpha = 0.48f),
            Color(0xFFC3B5F0).copy(alpha = 0.42f),
            Color(0xFFF5D8A8).copy(alpha = 0.34f)
        ),
        blobsDark = listOf(
            Color(0xFF1F6B52).copy(alpha = 0.50f),
            Color(0xFF4A3D8F).copy(alpha = 0.40f),
            Color(0xFF8A6A3F).copy(alpha = 0.20f)
        ),
        tuning = GlassTuning.Neutral
    )

    /** 深海：海青 × 冰蓝 × 海沫。 */
    val Ocean = GradientPreset(
        key = "ocean",
        label = "深海",
        baseLight = listOf(Color(0xFFF2FAFB), Color(0xFFE8F3F4)),
        baseDark = listOf(Color(0xFF081216), Color(0xFF0A1520)),
        blobsLight = listOf(
            Color(0xFF6FD6D0).copy(alpha = 0.46f),
            Color(0xFF9CC8F0).copy(alpha = 0.40f),
            Color(0xFFBFF0E4).copy(alpha = 0.36f)
        ),
        blobsDark = listOf(
            Color(0xFF0F5B60).copy(alpha = 0.52f),
            Color(0xFF27577E).copy(alpha = 0.42f),
            Color(0xFF1C7A66).copy(alpha = 0.30f)
        ),
        tuning = GlassTuning(
            cardTintLight = Color(0xFFEFFEFF).copy(alpha = 0.15f),
            cardTintDark = Color(0xFF04141A).copy(alpha = 0.28f),
            barTintLight = Color(0xFFEAFBFF).copy(alpha = 0.22f),
            barTintDark = Color(0xFF051A20).copy(alpha = 0.34f),
            fieldTintLight = Color(0xFFEFFCFF).copy(alpha = 0.12f),
            fieldTintDark = Color(0xFF031014).copy(alpha = 0.22f),
            sheenLight = Color(0xFFDDF6FF),
            sheenDark = Color(0xFF9FE3E0),
            refraction = 0.95f
        )
    )

    /** 落日：珊瑚 × 琥珀 × 玫红。 */
    val Sunset = GradientPreset(
        key = "sunset",
        label = "落日",
        baseLight = listOf(Color(0xFFFEF8F4), Color(0xFFFBEFEA)),
        baseDark = listOf(Color(0xFF171009), Color(0xFF1A0E12)),
        blobsLight = listOf(
            Color(0xFFF6A98A).copy(alpha = 0.46f),
            Color(0xFFF5C98E).copy(alpha = 0.42f),
            Color(0xFFE58AA6).copy(alpha = 0.38f)
        ),
        blobsDark = listOf(
            Color(0xFF8F4526).copy(alpha = 0.48f),
            Color(0xFF8A5A20).copy(alpha = 0.38f),
            Color(0xFF7E2C4A).copy(alpha = 0.32f)
        ),
        tuning = GlassTuning(
            cardTintLight = Color(0xFFFFF4EC).copy(alpha = 0.15f),
            cardTintDark = Color(0xFF1C0E06).copy(alpha = 0.28f),
            barTintLight = Color(0xFFFFEFDF).copy(alpha = 0.22f),
            barTintDark = Color(0xFF200F07).copy(alpha = 0.34f),
            fieldTintLight = Color(0xFFFFF6EE).copy(alpha = 0.12f),
            fieldTintDark = Color(0xFF180B05).copy(alpha = 0.22f),
            sheenLight = Color(0xFFFFE3C8),
            sheenDark = Color(0xFFF5B98C),
            refraction = 0.9f
        )
    )

    /** 兰紫：紫罗兰 × 洋红 × 冰青。 */
    val Orchid = GradientPreset(
        key = "orchid",
        label = "兰紫",
        baseLight = listOf(Color(0xFFFBF6FE), Color(0xFFF5EFFC)),
        baseDark = listOf(Color(0xFF120B18), Color(0xFF0F0C1E)),
        blobsLight = listOf(
            Color(0xFFC9A8F2).copy(alpha = 0.46f),
            Color(0xFFF0A6D8).copy(alpha = 0.40f),
            Color(0xFFA6E8F0).copy(alpha = 0.38f)
        ),
        blobsDark = listOf(
            Color(0xFF5B3D8F).copy(alpha = 0.48f),
            Color(0xFF8F2C6E).copy(alpha = 0.36f),
            Color(0xFF1F6B77).copy(alpha = 0.30f)
        ),
        tuning = GlassTuning(
            cardTintLight = Color(0xFFF9F0FF).copy(alpha = 0.15f),
            cardTintDark = Color(0xFF140A1C).copy(alpha = 0.28f),
            barTintLight = Color(0xFFF6ECFF).copy(alpha = 0.22f),
            barTintDark = Color(0xFF170B20).copy(alpha = 0.34f),
            fieldTintLight = Color(0xFFFAF2FF).copy(alpha = 0.12f),
            fieldTintDark = Color(0xFF100818).copy(alpha = 0.22f),
            sheenLight = Color(0xFFF4E2FF),
            sheenDark = Color(0xFFD9B8F5),
            refraction = 0.9f
        )
    )

    /** 石墨：银灰 × 暖白 × 炭黑（极简 —— 折射最收敛）。 */
    val Graphite = GradientPreset(
        key = "graphite",
        label = "石墨",
        baseLight = listOf(Color(0xFFFAFAF9), Color(0xFFEFEFEC)),
        baseDark = listOf(Color(0xFF0E0E10), Color(0xFF121214)),
        blobsLight = listOf(
            Color(0xFFE8E8E4).copy(alpha = 0.60f),
            Color(0xFFF6F3EC).copy(alpha = 0.50f),
            Color(0xFFD8D8DC).copy(alpha = 0.44f)
        ),
        blobsDark = listOf(
            Color(0xFF3A3A40).copy(alpha = 0.55f),
            Color(0xFF55504A).copy(alpha = 0.40f),
            Color(0xFF26262C).copy(alpha = 0.45f)
        ),
        tuning = GlassTuning(
            cardTintLight = Color.White.copy(alpha = 0.16f),
            cardTintDark = Color.Black.copy(alpha = 0.30f),
            barTintLight = Color.White.copy(alpha = 0.24f),
            barTintDark = Color.Black.copy(alpha = 0.36f),
            fieldTintLight = Color.White.copy(alpha = 0.13f),
            fieldTintDark = Color.Black.copy(alpha = 0.24f),
            sheenLight = Color.White,
            sheenDark = Color(0xFFD9D9DE),
            refraction = 0.55f
        )
    )

    val all = listOf(Aurora, Ocean, Sunset, Orchid, Graphite)

    fun byKey(key: String?): GradientPreset =
        all.firstOrNull { it.key == key } ?: Aurora
}

/**
 * 柔光漂移壁纸 —— 全部玻璃面的折射底材。
 *
 * 性能（v2.2 收敛）：相位增量推进（无锚点运算）；~24fps 步进；App 退后台
 * （< RESUMED）相位冻结 —— 亮屏才烧 GPU，暗屏零开销。
 */
@Composable
fun DriftingWallpaper(preset: GradientPreset, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    var phase by remember { mutableFloatStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        while (true) {
            delay(42)
            val now = System.nanoTime()
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                // 26s 漂移周期，增量推进 —— 暂停时自然冻结，恢复无缝续漂
                phase = (phase + (now - last) / 26_000_000_000f) % 1f
            }
            last = now
        }
    }

    val p = (phase * 2.0 * PI).toFloat()
    val base = if (dark) preset.baseDark else preset.baseLight
    val blobs = if (dark) preset.blobsDark else preset.blobsLight

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                val m = min(w, h)

                drawRect(Brush.verticalGradient(base))

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

                blob(
                    0.30f + 0.18f * cos(p),
                    0.28f + 0.10f * sin(p),
                    0.90f,
                    blobs[0]
                )
                blob(
                    0.72f - 0.15f * sin(p * 0.8f),
                    0.45f + 0.12f * cos(p * 0.9f),
                    0.78f,
                    blobs[1]
                )
                blob(
                    0.50f + 0.10f * cos(p * 0.7f),
                    0.86f,
                    0.70f,
                    blobs[2]
                )
            }
    )
}

/**
 * 自定义图片背景 —— ContentScale.Crop 全屏 + 极缓 Ken Burns 推拉（40s
 * 往返 ±5%）：静态图也持续变化，玻璃的模糊 / 折射 / 流光随之流动。
 * graphicsLayer 缩放 —— 纯矩阵变换，GPU 开销恒定。
 */
@Composable
fun ImageWallpaper(bitmap: ImageBitmap, modifier: Modifier = Modifier) {
    var kenBurns by remember { mutableFloatStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        while (true) {
            delay(42)
            val now = System.nanoTime()
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                // 40s 一个呼吸往返（0→1→0 三角波），映射 1.0f→1.10f 缩放
                val t = (phaselessTriangle(kenBurns + (now - last) / 40_000_000_000f))
                kenBurns = t
            }
            last = now
        }
    }

    val scale = 1.04f + 0.06f * kenBurns
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    )
}

/** 0..1 三角波（0→1→0 循环）—— Ken Burns 往复推拉。 */
private fun phaselessTriangle(t: Float): Float {
    val x = t % 1f
    return if (x < 0.5f) x * 2f else (1f - x) * 2f
}

/**
 * 流光时钟 —— 全 App 单一时钟驱动所有玻璃面的光带扫掠（9s 周期，~24fps
 * 推进，退后台冻结）。AppRoot 组合一次，经 [LocalGlassClock] 注入全部
 * Glass* 组件 —— N 个玻璃面共享一个 State 读，时钟每跳只触发各面的
 * 重绘（不重组、不重测量）。
 */
@Composable
fun rememberGlassClock(): State<Float> {
    val clock = remember { mutableFloatStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        while (true) {
            delay(42)
            val now = System.nanoTime()
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                clock.floatValue = (clock.floatValue + (now - last) / 9_000_000_000f) % 1f
            }
            last = now
        }
    }
    return clock
}

/** 图片解码（有界）—— 后台线程调用。 */
internal suspend fun decodeImageBounded(
    open: () -> android.content.res.AssetFileDescriptor?,
    maxDim: Int
): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim ||
            bounds.outHeight / (sample * 2) >= maxDim
        ) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        open()?.use {
            BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, opts)
        }
    }.getOrNull()
}

/** Uri 便捷版（相册选图）。 */
internal suspend fun decodeUriBounded(
    resolver: android.content.ContentResolver,
    uri: Uri,
    maxDim: Int
): Bitmap? = decodeImageBounded(
    open = { runCatching { resolver.openAssetFileDescriptor(uri, "r") }.getOrNull() },
    maxDim = maxDim
)
