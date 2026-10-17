package com.apex.changelog.publisher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.apex.changelog.publisher.update.VcdiffDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 单仓库补丁更新 —— 客户端（发布器自更新通道）。
 *
 * 与主仓库双仓库体系同源的机制，收敛为单仓库：
 * - version.json 由发版流水线提交到**本仓库 main**（`[skip ci]` 防自触发），
 *   URL：`https://raw.githubusercontent.com/{repo}/main/version.json`；
 * - 增量补丁 = xdelta3 VCDIFF（上一版 APK → 本版 APK），App 内
 *   [VcdiffDecoder] 原位合成（无命令行 / 无 root）；
 * - 补丁仅对「补丁基底版本」可用（fromVersionCode 精确匹配），其余版本
 *   自动回退全量 APK；
 * - 双重校验：下载产物 SHA-256 对账 version.json 指纹 + Android 安装器
 *   自带签名连续性检查；
 * - 安装走 FileProvider + ACTION_VIEW，需「未知来源」授权
 *   （canRequestPackageInstalls 引导）。
 */
object SelfUpdate {

    /** 发布器自身更新源（仓库已更名）。 */
    const val UPDATE_REPO = "Ultra-Guru/apex-Administrator"

    /** 主 App 通道之外，发布器自身的 version.json 固定读取点。 */
    const val MANIFEST_URL = "https://raw.githubusercontent.com/$UPDATE_REPO/main/version.json"

    private val json = Json { ignoreUnknownKeys = true }

    // ── 数据模型（version.json，宽松解析：缺字段用默认值）─────────────

    @Serializable
    data class DownloadAsset(
        val url: String = "",
        val sizeBytes: Long = 0,
        val sha256: String = ""
    )

    @Serializable
    data class PatchAsset(
        val fromTag: String = "",
        val fromVersionCode: Int = 0,
        val url: String = "",
        val sizeBytes: Long = 0,
        val sha256: String = ""
    )

    @Serializable
    data class Manifest(
        val versionName: String = "",
        val versionCode: Int = 0,
        val tag: String = "",
        val publishedAt: String = "",
        val releasePage: String = "",
        val notes: String? = null,
        val commitSha: String? = null,
        val download: DownloadAsset = DownloadAsset(),
        val patch: PatchAsset? = null
    )

    /** 本机安装信息。 */
    data class Installed(val versionName: String, val versionCode: Long)

    @Suppress("DEPRECATION")
    fun installed(context: Context): Installed {
        val pm = context.packageManager
        val pi = pm.getPackageInfo(context.packageName, 0)
        val vc = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        return Installed(pi.versionName ?: "?", vc)
    }

    /** 拉取远端清单（no-cache）。失败折叠为 Result.failure（静默降级）。 */
    suspend fun fetchManifest(): Result<Manifest> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(MANIFEST_URL)
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "apex-Administrator-self-update")
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                json.decodeFromString<Manifest>(resp.body?.string().orEmpty())
            }
        }
    }

    /**
     * 下载资产到 [dest]（流式 + 进度回调 + 终局 SHA-256 对账）。
     * 进度回调在 IO 线程 —— UI 侧自行切主线程。
     */
    suspend fun downloadToFile(
        url: String,
        dest: File,
        expectedSize: Long,
        expectedSha256: String,
        onProgress: (bytes: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            dest.parentFile?.mkdirs()
            dest.delete()
            val request = Request.Builder().url(url).header("User-Agent", "apex-Administrator-self-update").get().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("空响应体")
                val total = if (expectedSize > 0) expectedSize else body.contentLength()
                var read = 0L
                var lastTick = 0L
                body.byteStream().use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (read - lastTick >= 256 * 1024 || read == total) {
                                lastTick = read
                                onProgress(read, total)
                            }
                        }
                    }
                }
                onProgress(read, total)
                if (expectedSize > 0 && read != expectedSize) {
                    throw IOException("体积对账失败：预期 $expectedSize，实际 $read")
                }
                val digest = sha256(dest)
                if (digest != expectedSha256) {
                    throw IOException("SHA-256 对账失败（下载损坏或被篡改）")
                }
                dest
            }
        }
    }

    /** 应用 VCDIFF 补丁：已安装 APK + 补丁 → 合成新 APK（SHA-256 终局对账）。 */
    suspend fun applyPatch(
        sourceApk: File,
        patchFile: File,
        output: File,
        expectedSize: Long,
        expectedSha256: String,
        onProgress: (decoded: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            output.parentFile?.mkdirs()
            output.delete()
            val patchBytes = patchFile.readBytes()
            val decoder = VcdiffDecoder()
            decoder.decode(
                source = sourceApk,
                patch = patchBytes,
                output = output,
                expectedSize = expectedSize,
                onProgress = VcdiffDecoder.ProgressListener { decoded -> onProgress(decoded) }
            )
            val digest = sha256(output)
            if (digest != expectedSha256) {
                output.delete()
                throw IOException("合成产物 SHA-256 对账失败")
            }
            output
        }
    }

    /**
     * 拉起系统安装器。未获「未知来源」授权时跳授权页并返回 false
     * （调用方提示用户授权后重试）。
     */
    fun installApk(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return false
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }

    /** 更新工作目录（filesDir/updates）。 */
    fun workDir(context: Context): File = File(context.filesDir, "updates").apply { mkdirs() }

    /** 清空更新目录残留（新流程开工前调用）。 */
    fun cleanWorkDir(context: Context) {
        workDir(context).listFiles()?.forEach { it.delete() }
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun mb(bytes: Long): String = "%.1f MB".format(bytes / 1024.0 / 1024.0)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
}
