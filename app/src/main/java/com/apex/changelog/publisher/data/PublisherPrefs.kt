package com.apex.changelog.publisher.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 发布器本机配置与发送历史 —— SharedPreferences（应用私有目录）。
 *
 * Token 安全口径（MVP）：
 * - 仅存本机应用私有存储；Manifest 已 `allowBackup=false`（无云备份/无设备迁移）；
 * - 要求使用 fine-grained token（只授权目标仓库、仅 Contents 读写），
 *   泄露面与可撤销性由 GitHub 侧兜底；
 * - 绝不写入日志 / 绝不出现在任何提交内容中（提交体只有日志 Markdown）。
 */
object PublisherPrefs {

    /** 目标仓库默认值 —— 主项目开发仓库（发布流水线：push main 即自动发版）。 */
    const val DEFAULT_REPO = "Ultra-Guru/Android-Guru-Agent"

    /** 默认分支：直推 main = 自动触发发版流水线（点击发送即出新版本）。 */
    const val DEFAULT_BRANCH = "main"

    private const val PREFS = "publisher_prefs"
    private const val KEY_REPO = "repo"
    private const val KEY_BRANCH = "branch"
    private const val KEY_TOKEN = "token"
    private const val KEY_HISTORY = "history"

    private val json = Json { ignoreUnknownKeys = true }

    fun repo(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REPO, DEFAULT_REPO)?.ifBlank { DEFAULT_REPO } ?: DEFAULT_REPO

    fun branch(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BRANCH, DEFAULT_BRANCH)?.ifBlank { DEFAULT_BRANCH } ?: DEFAULT_BRANCH

    /** GitHub fine-grained token（空 = 未配置，发送前会提示去设置页）。 */
    fun token(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, "") ?: ""

    fun saveConnection(context: Context, repo: String, branch: String, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_REPO, repo.trim())
            .putString(KEY_BRANCH, branch.trim())
            .putString(KEY_TOKEN, token.trim())
            .apply()
    }

    /** 单条发送历史（本机留痕，仅展示用）。 */
    @Serializable
    data class HistoryItem(
        val timeMs: Long,
        val title: String,
        val ok: Boolean,
        val url: String? = null
    )

    fun history(context: Context): List<HistoryItem> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, null) ?: return emptyList()
        json.decodeFromString<List<HistoryItem>>(raw)
    }.getOrDefault(emptyList())

    fun addHistory(context: Context, item: HistoryItem) {
        val next = (history(context) + item).takeLast(30)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_HISTORY, json.encodeToString(next))
            .apply()
    }
}
