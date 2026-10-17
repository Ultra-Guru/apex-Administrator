package com.apex.changelog.publisher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.apex.changelog.publisher.ui.glass.GlassBanner
import com.apex.changelog.publisher.ui.glass.GlassButton
import com.apex.changelog.publisher.ui.glass.GlassCard
import com.apex.changelog.publisher.ui.glass.GlassDivider
import com.apex.changelog.publisher.ui.glass.GlassIconButton
import com.apex.changelog.publisher.ui.glass.GlassTextField
import com.apex.changelog.publisher.ui.glass.SpacerH
import com.apex.changelog.publisher.ui.glass.SpacerW
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主页 —— 撰写日志 + 发送（触发发版流水线）。
 *
 * 发送语义（闭环核心，v1 语义原样保留）：提交 `.changelog/unreleased/<时间戳>-<slug>.md`
 * 到目标分支 —— push main 自动触发开发仓库发布流水线，新版本 APK 内置本条日志，
 * 主 App 升级后首启弹窗展示。
 *
 * v2：全局液态玻璃化 + UX 打磨（更新横幅 / 分组标签 / 暂存折叠 / 结果卡操作）。
 */
@Composable
fun MainScreen(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    updateState: UpdateUiState,
    onGoSettings: () -> Unit
) {
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
            staged = GitHubApi(token).listUnreleased(repo, branch).getOrElse { emptyList() }
            loadingStaged = false
        }
    }

    LaunchedEffect(Unit) { refreshStaged() }

    fun send() {
        if (sending) return
        if (token.isBlank()) {
            result = SendResult.Failure("尚未配置 Token —— 点右上角设置，填入 GitHub token（Contents: Read and write）")
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
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 更新可用横幅（单仓库补丁更新通道）────────────────────────
        if (updateState is UpdateUiState.Available) {
            GlassBanner(
                backdrop = backdrop,
                icon = Icons.Outlined.CloudDownload,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "新版本 v${updateState.manifest.versionName} 可用",
                subtitle = if (updateState.viaPatch) "支持增量补丁（仅下载差量）· 前往设置安装"
                else "前往设置页安装",
                actionLabel = "去更新",
                onAction = onGoSettings
            )
        }

        // ── 目标信息卡 ──────────────────────────────────────────────
        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Text(
                "目标仓库",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                "$repo @ $branch",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            SpacerH(6.dp)
            Text(
                "发送 = 提交日志片段到开发仓库 → 自动触发发版流水线 → 新版 APK 内置本条日志 → 主 App 升级后首启弹窗展示",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        // ── 已暂存片段（下一版内容）──────────────────────────────────
        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "下一版已暂存日志（${staged?.size ?: "…"}）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (loadingStaged) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp).width(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    GlassIconButton(
                        onClick = { refreshStaged() },
                        backdrop = backdrop,
                        icon = Icons.Filled.Refresh,
                        contentDescription = "刷新",
                        size = 38.dp
                    )
                }
            }
            SpacerH(6.dp)
            when {
                token.isBlank() -> HintText("先去右上角设置页配置 Token")
                staged == null -> HintText("加载中…")
                staged!!.isEmpty() -> HintText("暂无片段 —— 下一版将使用兜底文案；写一条吧")
                else -> {
                    staged!!.take(6).forEach { name ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.height(16.dp).width(16.dp)
                            )
                            SpacerW(8.dp)
                            Text(
                                name,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (staged!!.size > 6) {
                        SpacerH(4.dp)
                        HintText("…另有 ${staged!!.size - 6} 条，构建时自动聚合进下一版")
                    }
                }
            }
        }

        // ── 编辑区：标题 + 正文 ─────────────────────────────────────
        GlassTextField(
            backdrop = backdrop,
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = "标题（可选 —— 用于文件名与提交信息）",
            singleLine = true
        )
        GlassTextField(
            backdrop = backdrop,
            value = body,
            onValueChange = { body = it },
            modifier = Modifier.fillMaxWidth(),
            label = "更新日志（Markdown）",
            minLines = 7,
            supporting = "以「- 」开头的列表项会成为主 App 弹窗要点；当前 ${body.length} 字"
        )

        // ── 发送 ────────────────────────────────────────────────────
        GlassButton(
            onClick = { send() },
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            enabled = body.isNotBlank(),
            tint = MaterialTheme.colorScheme.primary,
            fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        ) {
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp).width(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text("提交中…", color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    "发送 → 触发新版本构建",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ── 结果反馈卡 ──────────────────────────────────────────────
        result?.let { r ->
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                when (r) {
                    is SendResult.Success -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            SpacerW(8.dp)
                            Text(
                                "已提交：${r.title}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        SpacerH(6.dp)
                        Text(
                            "发版流水线已自动触发（约 10-20 分钟出包）。新版上线后，主 App 升级首启即弹窗展示本条日志。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SpacerH(10.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassButton(
                                onClick = { openUrl(context, r.commitUrl) },
                                backdrop = backdrop,
                                height = 44.dp
                            ) {
                                Text(
                                    "查看提交",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            GlassButton(
                                onClick = { openUrl(context, "https://github.com/$repo/actions") },
                                backdrop = backdrop,
                                height = 44.dp
                            ) {
                                Text(
                                    "构建进度",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
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
                            SpacerW(8.dp)
                            Text(
                                "提交失败",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        SpacerH(6.dp)
                        Text(
                            r.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (token.isBlank()) {
                            SpacerH(6.dp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.height(16.dp).width(16.dp)
                                )
                                SpacerW(4.dp)
                                Text(
                                    "去右上角「设置」填入 Token",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 本机发送历史 ────────────────────────────────────────────
        val history = remember { PublisherPrefs.history(context) }
        if (history.isNotEmpty()) {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "本机发送历史（最近 ${history.size} 条）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                SpacerH(6.dp)
                history.asReversed().take(8).forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    if (index < minOf(history.size, 8) - 1) GlassDivider()
                }
            }
        }
    }
}

/** 玻璃卡内浅提示行。 */
@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline
    )
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
