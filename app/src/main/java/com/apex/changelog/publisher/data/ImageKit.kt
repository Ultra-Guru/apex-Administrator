package com.apex.changelog.publisher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlin.math.min

/**
 * 配图工具箱 —— photo picker 落盘 + 上传前压缩。
 *
 * 设计约束：
 * - **选中即落盘**：photo picker 的临时授权不保证跨进程存活（旋转 / 低内存
 *   重建即失效），复制进 cacheDir 后状态才稳；移除即删文件；
 * - **体积自律**：最长边 ≤ [MAX_DIMENSION] px、JPEG [JPEG_QUALITY] ——
 *   单图典型 < 300 KB。changelog.json 配图走 raw CDN，图大了主 App 弹窗
 *   加载就慢了；
 * - **格式收敛**：统一重编码为 .jpg（杜绝 SVG / 动图等渲染面外溢，
 *   主 App 端 Coil 按普通位图加载即可）。
 */
object ImageKit {

    /** 单条日志配图上限（与主 App 弹窗图廊 3×3 网格对齐）。 */
    const val MAX_IMAGES = 9

    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 85

    /**
     * 选中图片落盘 `cacheDir/picked/`。
     * 返回 null = 该 Uri 当前不可读（授权已失效等），调用方静默跳过。
     */
    fun stage(context: Context, uri: Uri): File? = runCatching {
        val dir = File(context.cacheDir, "picked").apply { mkdirs() }
        val out = File(dir, "pick-${System.currentTimeMillis()}-${(100..999).random()}.img")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        out
    }.getOrNull()

    /**
     * 上传前压缩：最长边 ≤ 1600px + JPEG 85，输出 `cacheDir/upload/*.jpg`。
     * 解码失败（损坏文件 / 前台被杀）回退原文件直传 —— 发布列车不断。
     */
    fun compress(context: Context, source: File): File = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return source

        // 先按 2 的幂采样粗缩（内存峰值可控），再精缩到目标边
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= MAX_DIMENSION) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return source

        val scale = min(1f, MAX_DIMENSION.toFloat() / maxOf(decoded.width, decoded.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }
        val out = File(File(context.cacheDir, "upload").apply { mkdirs() },
            "img-${System.currentTimeMillis()}-${(100..999).random()}.jpg")
        out.outputStream().use { fos ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
        }
        if (scaled !== decoded) decoded.recycle()
        out
    }.getOrDefault(source)

    /** 发送成功后的缓存清理（picked + upload 两目录整清，失败路径保留供重试）。 */
    fun clearCache(context: Context) {
        listOf("picked", "upload").forEach { name ->
            runCatching {
                File(context.cacheDir, name).deleteRecursively()
            }
        }
    }

}
