package com.apex.changelog.publisher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import com.apex.changelog.publisher.ui.glass.GlassEntrance
import com.apex.changelog.publisher.ui.glass.GlassIconButton
import com.apex.changelog.publisher.ui.glass.GlassTextField
import com.apex.changelog.publisher.ui.glass.SpacerH
import com.apex.changelog.publisher.ui.glass.SpacerW
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 版本号格式（与聚合端 VERSION_RE 同源）：X.Y[.Z[.N]]。 */
private val VERSION_RE = Regex("^\\d+(\\.\\d+)+$")

/** 数值比较 a > b（逐段）；任一无法解析 → null（调用方按未知降级展示）。 */
private fun versionGt(a: String, b: String): Boolean? {
    fun tup(v: String) = v.split(".").mapNotNull { it.toIntOrNull() }.let {
        if (it.isEmpty()) null else it
    }
    val ta = tup(a.trim().removePrefix("v")) ?: return null
    val tb = tup(b.trim().removePrefix("v")) ?: return null
    val n = maxOf(ta.size, tb.size)
    val pa = ta + List(n - ta.size) { 0 }
    val pb = tb + List(n - tb.size) { 0 }
    for (i in 0 until n) {
        if (pa[i] != pb[i]) return pa[i] > pb[i]
    }
    return false
}

/** 末段序号 +1（发版列车近似预测：1.4.9.27 → 1.4.9.28）。 */
private fun bumpLastOrdinal(version: String): String {
    val parts = version.split(".").toMutableList()
    val last = parts.lastOrNull()?.toIntOrNull()
    if (last != null) parts[parts.lastIndex] = (last + 1).toString()
    return parts.joinToString(".")
}

/** 连接快照（AppRoot 提升持有；设置页保存即更新，主页 LaunchedEffect 重拉）。 */
data class Connection(
    val repo: String,
    val branch: String,
    val token: String
) {
    val ready: Boolean get() = token.isNotBlank()
}

/** 版本定位状态（线上最新 + 下版预告）。 */
private sealed interface VersionStatus {
    data object Loading : VersionStatus
    data class Ready(
        val latest: GitHubApi.ChannelVersion?,
        val nextHint: String?
    ) : VersionStatus
    data class Failed(val message: String) : VersionStatus
}

/**
 * 主页 —— 撰写日志 + 发送（触发发版流水线）。
 *
 * 发送语义（v2 升级）：提交 `.changelog/unreleased/<时间戳>-<slug>.md` 到目标
 * 分支，片段文件头携带元数据标记 —— `source: publisher`（聚合端将其置为一级
 * 置顶日志）+ 可选 `target: X.Y.Z`（面向未来版本：构建未到 → 缓发布，跨版本
 * 滞留到目标版本才收录）。
 *
 * v2.1：版本定位卡（填完 Token 自动拉线上最新 + 下版预告，可选下一版 / 自定义
 * 未来版）；全局液态玻璃性能分级 + 级联入场；布局修复（imePadding / FlowRow）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    connection: Connection,
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
    var versionStatus by remember { mutableStateOf<VersionStatus>(VersionStatus.Loading) }

    // 目标版本选择：下一版（缺省，不写 target = 下次发布即收录）/ 自定义未来版。
    var targetCustom by remember { mutableStateOf(false) }
    var customVersion by remember { mutableStateOf("") }

    // 发送历史读取 nonce（发送成功后 +1 → 历史卡即时刷新）
    var historyNonce by remember { mutableIntStateOf(0) }

    fun reloadChannel() {
        if (!connection.ready) {
            versionStatus = VersionStatus.Ready(null, null)
            return
        }
        versionStatus = VersionStatus.Loading
        scope.launch {
            coroutineScope {
                val latest = async { GitHubApi(connection.token).fetchLatestVersion(connection.repo) }
                val hint = async { GitHubApi(connection.token).fetchNextVersionHint(connection.repo) }
                val l = latest.await().getOrNull()
                val h = hint.await().getOrNull()
                versionStatus = when {
                    l == null && h == null ->
                        VersionStatus.Failed("发布仓库 version.json 与 gradle 预告均不可用 —— 检查网络 / 仓库名（发布仓库 = <目标仓库>-Release）")
                    else -> VersionStatus.Ready(l, h)
                }
            }
        }
    }

    fun reloadStaged() {
        if (!connection.ready) {
            staged = emptyList()
            return
        }
        loadingStaged = true
        scope.launch {
            staged = GitHubApi(connection.token)
                .listUnreleased(connection.repo, connection.branch)
                .getOrElse { emptyList() }
            loadingStaged = false
        }
    }

    // 连接变化（进入主页 / 设置页保存返回）→ 自动拉取版本定位 + 暂存列表
    LaunchedEffect(connection) {
        result = null
        if (connection.ready) {
            reloadStaged()
            reloadChannel()
        } else {
            staged = emptyList()
            versionStatus = VersionStatus.Ready(null, null)
        }
    }

    fun send() {
        if (sending) return
        if (!connection.ready) {
            result = SendResult.Failure("尚未配置 Token —— 点右上角设置，填入 GitHub token（Contents: Read and write）")
            return
        }
        val content = body.trim()
        if (content.isEmpty()) {
            result = SendResult.Failure("日志内容为空")
            return
        }
        val target = customVersion.trim()
        if (targetCustom && !VERSION_RE.matches(target)) {
            result = SendResult.Failure("自定义目标版本号格式应为 X.Y[.Z[.N]]（如 1.4.9.30）—— 当前：${target.ifBlank { "（空）" }}")
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

            // 文件头元数据：source 标记 → 聚合端置为一级置顶日志；
            // 自定义目标版本 → target 标记（构建未到 → 缓发布等未来版本）。
            val fileContent = buildString {
                appendLine("<!-- source: publisher -->")
                if (targetCustom) appendLine("<!-- target: $target -->")
                append(content)
            }

            GitHubApi(connection.token).commitFragment(connection.repo, connection.branch, path, message, fileContent)
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
                    result = SendResult.Success(commitTitle, url, if (targetCustom) target else null)
                    title = ""
                    body = ""
                    targetCustom = false
                    customVersion = ""
                    historyNonce++
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
            reloadStaged()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 更新可用横幅（单仓库补丁更新通道）────────────────────────
        AnimatedVisibility(
            visible = updateState is UpdateUiState.Available,
            enter = fadeIn() + expandVertically()
        ) {
            if (updateState is UpdateUiState.Available) {
                GlassEntrance {
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
            }
        }

        // ── 版本定位卡（自动拉取 + 目标版本选择）─────────────────────
        GlassEntrance(delayMillis = 40) {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "版本定位",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (versionStatus is VersionStatus.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp).width(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        GlassIconButton(
                            onClick = { reloadChannel(); reloadStaged() },
                            backdrop = backdrop,
                            icon = Icons.Filled.Refresh,
                            contentDescription = "刷新版本定位",
                            size = 38.dp
                        )
                    }
                }
                SpacerH(8.dp)
                when (val vs = versionStatus) {
                    is VersionStatus.Loading -> HintText("正在拉取线上版本…")
                    is VersionStatus.Failed -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.height(16.dp).width(16.dp)
                            )
                            SpacerW(6.dp)
                            Text(
                                vs.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is VersionStatus.Ready -> {
                        vs.latest?.let { latest ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.NewReleases,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.height(16.dp).width(16.dp)
                                )
                                SpacerW(6.dp)
                                Text(
                                    "线上最新 v${latest.versionName}（code ${latest.versionCode}）",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                        val hint = vs.nextHint
                        val latestName = vs.latest?.versionName
                        when {
                            hint == null && latestName == null ->
                                HintText("版本通道不可用 —— 点右上角刷新重试")
                            hint == null ->
                                HintText("下版预告不可用（gradle versionName 提取失败）—— 可用「自定义」直接指定")
                            latestName == null ->
                                HintText("下版预告 v$hint（来自 gradle；发布仓库 version.json 尚未自举）")
                            hint == latestName ->
                                HintText("gradle 仍是 v$hint（未 bump）→ 下次发布自动追加构建序号（v$hint.1 起）")
                            versionGt(hint, latestName) == true ->
                                HintText("下版预告 v$hint（PR 已在 gradle 提前 bump）")
                            else ->
                                HintText("发版列车 v$latestName 领先 gradle 默认 v$hint → 下次发布自动追加序号（约 v${bumpLastOrdinal(latestName)}）")
                        }
                    }
                }
                if (!connection.ready) {
                    SpacerH(4.dp)
                    HintText("填完 Token（设置页）后自动拉取最新版本")
                }

                // ── 目标版本选择 ──────────────────────────────────────
                SpacerH(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TargetChip(
                        backdrop = backdrop,
                        selected = !targetCustom,
                        label = "下一版（自动）",
                        onClick = { targetCustom = false }
                    )
                    TargetChip(
                        backdrop = backdrop,
                        selected = targetCustom,
                        label = "自定义未来版",
                        onClick = { targetCustom = true }
                    )
                }
                AnimatedVisibility(
                    visible = targetCustom,
                    enter = fadeIn() + expandVertically()
                ) {
                    Column {
                        SpacerH(10.dp)
                        GlassTextField(
                            backdrop = backdrop,
                            value = customVersion,
                            onValueChange = { customVersion = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = "目标版本（如 1.4.9.30）",
                            singleLine = true,
                            supporting = "构建未到此版本时片段缓发布 —— 滞留到目标版本才收录展示"
                        )
                    }
                }
                if (!targetCustom) {
                    SpacerH(6.dp)
                    HintText("不写 target —— 下次发布即收录，聚合端按来源分为一级（发布器）/ 二级（PR）")
                }
            }
        }

        // ── 已暂存片段（下一版内容）──────────────────────────────────
        GlassEntrance(delayMillis = 80) {
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
                            onClick = { reloadStaged() },
                            backdrop = backdrop,
                            icon = Icons.Filled.Refresh,
                            contentDescription = "刷新",
                            size = 38.dp
                        )
                    }
                }
                SpacerH(6.dp)
                when {
                    !connection.ready -> HintText("先去右上角设置页配置 Token")
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
        }

        // ── 编辑区：标题 + 正文 ─────────────────────────────────────
        GlassEntrance(delayMillis = 120) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    supporting = "以「- 」开头的列表项会成为主 App 弹窗一级要点；当前 ${body.length} 字"
                )
            }
        }

        // ── 发送 ────────────────────────────────────────────────────
        GlassEntrance(delayMillis = 160) {
            GlassButton(
                onClick = { send() },
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
                enabled = body.isNotBlank() && !sending,
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
        }

        // ── 结果反馈卡 ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = result != null,
            enter = fadeIn() + expandVertically()
        ) {
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
                                buildString {
                                    append("发版流水线已自动触发（约 10-20 分钟出包）。")
                                    if (r.target != null) {
                                        append("本条面向 v${r.target} —— 构建未到该版本时缓发布，到版即自动置顶展示。")
                                    } else {
                                        append("新版上线后，主 App 升级首启即弹窗展示本条日志（一级置顶）。")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            SpacerH(10.dp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    onClick = { openUrl(context, "https://github.com/${connection.repo}/actions") },
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
                            if (!connection.ready) {
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
        }

        // ── 本机发送历史 ────────────────────────────────────────────
        val history = remember(historyNonce) { PublisherPrefs.history(context) }
        if (history.isNotEmpty()) {
            // 可观察 locale（配置变更时重组重排，而非直接 Locale.getDefault()）
            val locale = LocalConfiguration.current.locales[0]
            GlassEntrance(delayMillis = 200) {
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
                                    SimpleDateFormat("MM-dd HH:mm", locale)
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
}

/** 目标版本选择胶囊（素玻璃小按钮；选中态着主色淡填充）。 */
@Composable
private fun TargetChip(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    GlassButton(
        onClick = onClick,
        backdrop = backdrop,
        height = 38.dp,
        tint = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else androidx.compose.ui.graphics.Color.Unspecified,
        fill = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Unspecified
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 发送结果三态（Success 附带目标版本 —— 用于反馈文案）。 */
private sealed interface SendResult {
    data class Success(val title: String, val commitUrl: String, val target: String?) : SendResult
    data class Failure(val message: String) : SendResult
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

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
