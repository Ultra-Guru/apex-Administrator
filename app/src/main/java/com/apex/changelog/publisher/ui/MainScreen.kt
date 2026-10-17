package com.apex.changelog.publisher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apex.changelog.publisher.data.GitHubApi
import com.apex.changelog.publisher.data.PublisherPrefs
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主页 —— 大输入框 + 发送按钮 + 已暂存片段 + 本机发送历史。
 *
 * 发送语义（闭环核心）：提交 `.changelog/unreleased/<时间戳>-<slug>.md` 到
 * 目标分支 —— push main 自动触发开发仓库发布流水线（无需手动构建），
 * 新版本 APK 将内置本条日志，主 App 升级后首启弹窗展示。
 */
@Composable
fun MainScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SendResult?>(null) }
    var staged by remember { mutableStateOf<List<String>?>(null) }
    var loadingStaged by remember { mutableStateOf(false) }

    val repo = remember { PublisherPrefs.repo(context) }
    val branch = remember { PublisherPrefs.branch(context) }
    val token = remember { PublisherPrefs.token(context) }

    fun refreshStaged() {
        if (token.isBlank()) {
            staged = emptyList()
            return
        }
        loadingStaged = true
        scope.launch {
            staged = GitHubApi(token).listUnreleased(repo, branch)
                .getOrElse { emptyList() }
            loadingStaged = false
        }
    }

    LaunchedEffect(Unit) { refreshStaged() }

    fun send() {
        if (sending) return
        if (token.isBlank()) {
            result = SendResult.Failure("尚未配置 Token —— 点右上角设置，填入 GitHub fine-grained token（Contents: Read and write）")
            return
        }
        val content = body.trim()
        if (content.isEmpty()) {
            result = SendResult.Failure("日志内容为空")
            return
        }
        sending = true
        result = null
        scope.launch {
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val slug = title.trim()
                .replace(Regex("[^0-9A-Za-z._-]+"), "-")
                .trim('-')
                .take(40)
                .ifEmpty { "note" }
            val path = ".changelog/unreleased/$ts-$slug.md"
            val commitTitle = title.trim().ifBlank {
                content.lineSequence().firstOrNull()?.take(40) ?: "更新日志"
            }
            // 提交信息走主仓库 conventional 规范（guard-rails 在 push main 时校验）
            val message = "docs(changelog): $commitTitle（via 发布器）"

            GitHubApi(token).commitFragment(repo, branch, path, message, content)
                .onSuccess { url ->
                    PublisherPrefs.addHistory(
                        context,
                        PublisherPrefs.HistoryItem(
                            timeMs = System.currentTimeMillis(),
                            title = commitTitle,
                            ok = true,
                            url = url
                        )
                    )
                    result = SendResult.Success(commitTitle, url)
                    title = ""
                    body = ""
                }
                .onFailure { e ->
                    PublisherPrefs.addHistory(
                        context,
                        PublisherPrefs.HistoryItem(
                            timeMs = System.currentTimeMillis(),
                            title = commitTitle,
                            ok = false
                        )
                    )
                    result = SendResult.Failure(e.message ?: "未知错误")
                }
            sending = false
            refreshStaged()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 目标信息卡 ──────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("目标仓库", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline)
                Text(
                    "$repo @ $branch",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "发送 = 提交日志片段到开发仓库 → 自动触发发版流水线 → 新版 APK 内置本条日志 → 主 App 升级后首启弹窗展示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // ── 已暂存片段（下一版内容）──────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "下一版已暂存日志（${staged?.size ?: "…"}）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (loadingStaged) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp).width(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { refreshStaged() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    }
                }
                when {
                    token.isBlank() -> Text(
                        "先去右上角设置页配置 Token",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    staged == null -> Text(
                        "加载中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    staged!!.isEmpty() -> Text(
                        "暂无片段 —— 下一版将使用兜底文案；写一条吧",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    else -> staged!!.forEach { name ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Description,
                                contentDescription = null,
                                modifier = Modifier.height(16.dp).width(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                name,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // ── 编辑区：标题 + 正文 ─────────────────────────────────────
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("标题（可选 —— 用于文件名与提交信息）") }
        )
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp),
            label = { Text("更新日志（Markdown）") },
            supportingText = {
                Text(
                    "以「- 」开头的列表项会成为主 App 弹窗的要点；当前 ${body.length} 字"
                )
            }
        )

        Button(
            onClick = { send() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !sending && body.isNotBlank()
        ) {
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp).width(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text("提交中…")
            } else {
                Icon(Icons.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("发送 → 触发新版本构建")
            }
        }

        // ── 结果反馈卡 ──────────────────────────────────────────────
        result?.let { r ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    when (r) {
                        is SendResult.Success -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "已提交：${r.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "发版流水线已自动触发（push main 即发版，约 10-20 分钟出包）。新版上线后，主 App 升级首启即弹窗展示本条日志。",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { openUrl(context, r.url) }) {
                                    Text("查看提交")
                                }
                                Button(onClick = {
                                    openUrl(context, "https://github.com/$repo/actions")
                                }) {
                                    Text("构建进度")
                                }
                            }
                        }

                        is SendResult.Failure -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "提交失败",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                r.message,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (token.isBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.height(16.dp).width(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "去右上角「设置」填入 Token",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 本机发送历史 ────────────────────────────────────────────
        val history = remember { PublisherPrefs.history(context) }
        if (history.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "本机发送历史（最近 ${history.size} 条）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    history.asReversed().take(10).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                                Text(
                                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                        .format(Date(item.timeMs)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Text(
                                if (item.ok) "✓" else "✗",
                                color = if (item.ok) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** 发送结果三态。 */
private sealed interface SendResult {
    data class Success(val title: String, val commitUrl: String) : SendResult
    data class Failure(val message: String) : SendResult
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
