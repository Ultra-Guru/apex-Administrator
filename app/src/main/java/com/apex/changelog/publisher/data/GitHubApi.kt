package com.apex.changelog.publisher.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * GitHub REST API 最小客户端 —— 发布器只做三件事：
 * 测连接 / 列未发布片段 / 提交片段文件（Contents API）。
 *
 * 设计约束：
 * - **Token 只从设置页来**（[PublisherPrefs.token]），仅存内存与本机私有存储，
 *   绝不进日志、绝不进提交内容；
 * - **共享 OkHttp**（companion 单例连接池）—— [GitHubApi] 按 token 即建即弃；
 * - **错误面翻译**：401/403/404/422 映射为可操作的中文提示（token 过期 /
 *   权限不足 / 仓库未授权 / 提交被拒），其余透传 GitHub message；
 * - **可取消**：协程取消同步 cancel 底层调用；网络失败折叠为
 *   Result.failure（UI 展示 message，绝不崩溃）。
 */
class GitHubApi(private val token: String) {

    @Serializable
    data class RepoInfo(
        @SerialName("full_name") val fullName: String = "",
        @SerialName("default_branch") val defaultBranch: String = "",
        @SerialName("private") val private: Boolean = false
    )

    @Serializable
    private data class ContentEntry(@SerialName("name") val name: String = "")

    @Serializable
    private data class PutBody(
        val message: String,
        val branch: String,
        val content: String
    )

    @Serializable
    private data class CommitRef(
        @SerialName("html_url") val htmlUrl: String = ""
    )

    @Serializable
    private data class PutResponse(val commit: CommitRef? = null)

    @Serializable
    private data class ApiError(val message: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    /** 测连接：验证 token 有效 + 对目标仓库有读取权（404 = 未授权或不存在）。 */
    suspend fun testConnection(repo: String): Result<RepoInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apiUrl("repos/$repo"))
                .headers(commonHeaders())
                .get()
                .build()
            client.await(request).use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.code == 200) json.decodeFromString<RepoInfo>(body)
                else throw IllegalArgumentException(errorMessage(resp.code, body))
            }
        }
    }

    /**
     * 列出 `.changelog/unreleased/` 已暂存片段（文件名列表 = 下一版内容）。
     * 404（目录尚不存在）折叠为空列表 —— 通道首用属正常态。
     */
    suspend fun listUnreleased(repo: String, branch: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = apiUrl("repos/$repo/contents/.changelog/unreleased")
                    .newBuilder()
                    .addQueryParameter("ref", branch)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .headers(commonHeaders())
                    .get()
                    .build()
                client.await(request).use { resp ->
                    val body = resp.body?.string().orEmpty()
                    when (resp.code) {
                        200 -> json.decodeFromString<List<ContentEntry>>(body).map { it.name }
                        404 -> emptyList()
                        else -> throw IllegalArgumentException(errorMessage(resp.code, body))
                    }
                }
            }
        }

    /**
     * 提交片段文件（PUT Contents API）—— 新文件无需 sha。
     *
     * @return commit 的 html_url（发布器展示 + 可点开）
     */
    suspend fun commitFragment(
        repo: String,
        branch: String,
        path: String,
        message: String,
        markdownContent: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.encodeToString(
                PutBody.serializer(),
                PutBody(
                    message = message,
                    branch = branch,
                    content = Base64.encodeToString(
                        markdownContent.toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP
                    )
                )
            )
            val request = Request.Builder()
                .url(apiUrl("repos/$repo/contents/$path"))
                .headers(commonHeaders())
                .put(payload.toRequestBody("application/json".toMediaType()))
                .build()
            client.await(request).use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.code in 200..299) {
                    json.decodeFromString<PutResponse>(body).commit?.htmlUrl
                        ?: error("提交成功但未返回 commit URL")
                } else {
                    throw IllegalArgumentException(errorMessage(resp.code, body))
                }
            }
        }
    }

    // ── 内部 ─────────────────────────────────────────────────────────────

    private fun apiUrl(path: String) =
        "https://api.github.com".toHttpUrl().newBuilder()
            .addPathSegments(path)
            .build()

    private fun commonHeaders() = Headers.Builder()
        .add("Authorization", "Bearer $token")
        .add("Accept", "application/vnd.github+json")
        .add("X-GitHub-Api-Version", "2022-11-28")
        .add("User-Agent", "apex-changelog-publisher")
        .build()

    /** HTTP 状态 → 可操作中文提示（设置页/发送按钮直接展示）。 */
    private fun errorMessage(code: Int, body: String): String {
        val api = runCatching { json.decodeFromString<ApiError>(body).message }.getOrNull()
        return when (code) {
            401 -> "Token 无效或已过期（classic 以 ghp_ 开头 / fine-grained 以 github_pat_ 开头，注意别混入空格）"
            403 -> "Token 权限不足：fine-grained 需勾选 Contents: Read and write；classic 至少勾 repo scope" +
                (api?.let { " —— $it" } ?: "")
            404 -> "仓库不存在，或 Token 未授权该仓库（fine-grained 需在 Repository access 勾选目标仓库）"
            422 -> "提交被拒（分支受保护 / 文件已存在 / 路径非法）" +
                (api?.let { " —— $it" } ?: "")
            else -> "HTTP $code" + (api?.let { " —— $it" } ?: "")
        }
    }

    private suspend fun OkHttpClient.await(request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            })
        }

    companion object {
        /** 进程级共享客户端（连接池/线程池复用，token 变更不影响）。 */
        private val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
