package com.apex.changelog.publisher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.apex.changelog.publisher.ui.glass.GlassTuning
import com.apex.changelog.publisher.ui.glass.decodeUriBounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.min

/**
 * 自定义背景的持久化与调谐派生（v2.2 背景 = 玻璃联动折射的数据面）。
 *
 * - 相册选图 → 有界解码（≤1440px）→ JPEG 90 落 filesDir/backgrounds/
 *   custom.jpg（体积与内存双收敛，原始 URI 不持久化 —— 授权会过期）；
 * - 玻璃调谐派生（零第三方 Palette 依赖）：32×32 缩略图 HSV 色相直方图
 *   （24 桶 × 15°）按 vibrant 权重 s²·(1-|2v-1|) 投票 →
 *     · 主导色 → 玻璃表面色（tint，亮暗主题各一套）；
 *     · 强调色（与主导色相距 ≥ 60° 的次强桶）→ 流光高光色（sheen）；
 *     · 明度极差（对比度）→ 折射强度（refraction 0.45..1.0）。
 *   单次分析 ~1ms（1024 像素），仅在换图时执行一次 —— 绝不进帧循环。
 */
object BackgroundStore {

    private const val MAX_DIM = 1440
    private const val THUMB = 32
    private const val HUE_BUCKETS = 24

    fun imageFile(context: Context): File =
        File(context.filesDir, "backgrounds/custom.jpg")

    /** 相册选图持久化 → true（可切换 Image 风格）；解码/写盘失败 → false。 */
    suspend fun saveImage(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = decodeUriBounded(context.contentResolver, uri, MAX_DIM)
                ?: return@withContext false
            val f = imageFile(context)
            f.parentFile?.mkdirs()
            FileOutputStream(f).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            true
        }.getOrDefault(false)
    }

    /** 已存背景 → (位图, 随图派生的玻璃调谐)；无文件 / 解码失败 → null。 */
    suspend fun loadImageWithTuning(context: Context): Pair<ImageBitmap, GlassTuning>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val f = imageFile(context)
                if (!f.isFile) return@withContext null
                val bmp = BitmapFactory.decodeFile(f.absolutePath)
                    ?: return@withContext null
                bmp.asImageBitmap() to tuningForBitmap(bmp)
            }.getOrNull()
        }

    /** 移除自定义图（回退极光由调用方切换风格）。 */
    fun clear(context: Context) {
        runCatching { imageFile(context).delete() }
    }

    // ── 调谐派生 ─────────────────────────────────────────────────────────

    /** 位图 → [GlassTuning]（主导色 tint / 强调色 sheen / 对比度 refraction）。 */
    fun tuningForBitmap(bmp: Bitmap): GlassTuning {
        val small = runCatching {
            Bitmap.createScaledBitmap(bmp, THUMB, THUMB, true)
        }.getOrNull() ?: return GlassTuning.Neutral

        val hsv = FloatArray(3)
        // 桶：[weight, sumSat, sumVal, count]
        val buckets = Array(HUE_BUCKETS) { FloatArray(4) }
        var minV = 1f
        var maxV = 0f

        for (y in 0 until THUMB) {
            for (x in 0 until THUMB) {
                android.graphics.Color.colorToHSV(small.getPixel(x, y), hsv)
                val s = hsv[1]
                val v = hsv[2]
                if (v < 0.06f) continue // 近黑像素不投票（阴影不该定调）
                val weight = s * s * (1f - abs(2f * v - 1f)) + 0.02f
                val b = (hsv[0] / (360f / HUE_BUCKETS)).toInt().coerceIn(0, HUE_BUCKETS - 1)
                buckets[b][0] += weight
                buckets[b][1] += s
                buckets[b][2] += v
                buckets[b][3] += 1f
                if (v < minV) minV = v
                if (v > maxV) maxV = v
            }
        }

        val order = buckets.indices.sortedByDescending { buckets[it][0] }
        val mainIdx = order.firstOrNull()?.takeIf { buckets[it][0] > 0f }
            ?: return GlassTuning.Neutral
        val accentIdx = order.drop(1).firstOrNull { idx ->
            val d = abs(idx - mainIdx)
            min(d, HUE_BUCKETS - d) >= 4 // 色相距 ≥ 60°
        }

        val mainH = mainIdx * (360f / HUE_BUCKETS) + 7.5f
        val mainS = buckets[mainIdx][1] / buckets[mainIdx][3].coerceAtLeast(1f)
        val mainV = buckets[mainIdx][2] / buckets[mainIdx][3].coerceAtLeast(1f)

        val accentH: Float
        val accentS: Float
        val accentV: Float
        if (accentIdx != null) {
            accentH = accentIdx * (360f / HUE_BUCKETS) + 7.5f
            accentS = buckets[accentIdx][1] / buckets[accentIdx][3].coerceAtLeast(1f)
            accentV = buckets[accentIdx][2] / buckets[accentIdx][3].coerceAtLeast(1f)
        } else {
            // 单色图：强调色 = 主色相 +150° 旋转（保证流光与底色可分辨）
            accentH = (mainH + 150f) % 360f
            accentS = mainS.coerceAtLeast(0.4f)
            accentV = 0.9f
        }

        val contrast = (maxV - minV).coerceIn(0f, 1f)

        return GlassTuning(
            cardTintLight = hsv(mainH, mainS * 0.35f, 0.98f, 0.15f),
            cardTintDark = hsv(mainH, mainS * 0.85f, (mainV * 0.30f).coerceAtLeast(0.06f), 0.28f),
            barTintLight = hsv(mainH, mainS * 0.40f, 0.97f, 0.22f),
            barTintDark = hsv(mainH, mainS * 0.90f, (mainV * 0.33f).coerceAtLeast(0.07f), 0.34f),
            fieldTintLight = hsv(mainH, mainS * 0.30f, 0.99f, 0.12f),
            fieldTintDark = hsv(mainH, mainS * 0.80f, (mainV * 0.26f).coerceAtLeast(0.05f), 0.22f),
            sheenLight = hsv(accentH, (accentS * 0.65f).coerceAtMost(0.70f), 1f),
            sheenDark = hsv(accentH, (accentS * 0.90f).coerceAtMost(0.85f), 0.88f),
            refraction = 0.45f + 0.55f * contrast
        )
    }

    /** HSV → Compose Color（s/v 越界自动钳制）。 */
    private fun hsv(h: Float, s: Float, v: Float, alpha: Float = 1f): Color =
        Color(
            android.graphics.Color.HSVToColor(
                floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
            )
        ).copy(alpha = alpha)
}
