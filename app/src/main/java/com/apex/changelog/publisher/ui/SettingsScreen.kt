package com.apex.changelog.publisher.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.apex.changelog.publisher.data.GitHubApi
import com.apex.changelog.publisher.data.PublisherPrefs
import com.apex.changelog.publisher.data.SelfUpdate
import com.apex.changelog.publisher.ui.glass.GlassButton
import com.apex.changelog.publisher.ui.glass.GlassCard
import com.apex.changelog.publisher.ui.glass.GlassEntrance
import com.apex.changelog.publisher.ui.glass.GlassIconButton
import com.apex.changelog.publisher.ui.glass.GlassTextField
import com.apex.changelog.publisher.ui.glass.SpacerH
import com.apex.changelog.publisher.ui.glass.SpacerW
import kotlinx.coroutines.launch
import java.io.File

/**
 * 设置页 —— 连接配置（目标仓库 / 分支 / Token）+ 发布器自更新卡。
 *
 * v2.1：
 * - Token 输入不再使用密码键盘（KeyboardType.Ascii）—— 不触发系统安全键盘，
 *   剪贴板粘贴可用；另配「粘贴」快捷键直读剪贴板（部分 IME 禁长按粘贴的兜底）；
 * - 连接字段间距 / FlowRow 防溢出 / imePadding / 状态机 AnimatedContent 过渡；
 * - Token 指引同时覆盖 classic（ghp_，repo scope）与 fine-grained 两种令牌。
 *
 * 自更新（单仓库补丁更新）：version.json 由发版流水线提交到本仓库 main；
 * 补丁 = xdelta3 VCDIFF（App 内合成，无命令行）；SHA-256 全链对账。
 */
@Composable
fun SettingsScreen(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    updateState: UpdateUiState,
    onUpdateState: (UpdateUiState) -> Unit,
    onConnectionChanged: (repo: String, branch: String, token: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GlassEntrance(delayMillis = 0) {
            UpdateCard(backdrop = backdrop, state = updateState, setState = onUpdateState)
        }
        GlassEntrance(delayMillis = 60) {
            ConnectionCard(backdrop = backdrop, onConnectionChanged = onConnectionChanged)
        }
        GlassEntrance(delayMillis = 120) {
            TokenGuideCard(backdrop = backdrop)
        }
        GlassEntrance(delayMillis = 180) {
            LinksCard(backdrop = backdrop)
        }
    }
}

// ── 自更新卡 ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpdateCard(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    state: UpdateUiState,
    setState: (UpdateUiState) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed = remember { SelfUpdate.installed(context) }

    fun check() {
        setState(UpdateUiState.Checking)
        scope.launch {
            val current = SelfUpdate.installed(context)
            val next = silentCheck(UpdateUiState.Idle, current)
            setState(
                if (next == UpdateUiState.Idle) {
                    UpdateUiState.Failed("无法获取 version.json —— 检查网络；或更新通道尚未自举（main 上还没有 version.json，首次发版后生效）")
                } else next
            )
        }
    }

    fun downloadPatch(manifest: SelfUpdate.Manifest) {
        val patch = manifest.patch ?: run {
            setState(UpdateUiState.Failed("清单无补丁资产"))
            return
        }
        SelfUpdate.cleanWorkDir(context)
        setState(UpdateUiState.Working("准备下载补丁…"))
        scope.launch {
            val patchFile = File(SelfUpdate.workDir(context), "patch.vcdiff")
            SelfUpdate.downloadToFile(patch.url, patchFile, patch.sizeBytes, patch.sha256) { b, t ->
                setState(UpdateUiState.Downloading(true, b, t))
            }.onSuccess {
                setState(UpdateUiState.Working("设备上合成新 APK（VCDIFF）…"))
                val sourceApk = File(context.applicationInfo.sourceDir)
                val out = File(SelfUpdate.workDir(context), "update.apk")
                SelfUpdate.applyPatch(sourceApk, it, out, manifest.download.sizeBytes, manifest.download.sha256) { decoded ->
                    val percent = if (manifest.download.sizeBytes > 0) (decoded * 100 / manifest.download.sizeBytes).toInt() else 0
                    setState(UpdateUiState.Working("合成新 APK…$percent%"))
                }.onSuccess { ready ->
                    setState(UpdateUiState.Ready(ready))
                }.onFailure { e ->
                    setState(UpdateUiState.Failed("补丁合成失败：${e.message}"))
                }
            }.onFailure { e ->
                setState(UpdateUiState.Failed("补丁下载失败：${e.message}"))
            }
        }
    }

    fun downloadFull(manifest: SelfUpdate.Manifest) {
        SelfUpdate.cleanWorkDir(context)
        setState(UpdateUiState.Working("准备下载完整包…"))
        scope.launch {
            val out = File(SelfUpdate.workDir(context), "update.apk")
            SelfUpdate.downloadToFile(manifest.download.url, out, manifest.download.sizeBytes, manifest.download.sha256) { b, t ->
                setState(UpdateUiState.Downloading(false, b, t))
            }.onSuccess {
                setState(UpdateUiState.Ready(it))
            }.onFailure { e ->
                setState(UpdateUiState.Failed("下载失败：${e.message}"))
            }
        }
    }

    fun install(apk: File) {
        val ok = SelfUpdate.installApk(context, apk)
        if (!ok) {
            Toast.makeText(
                context,
                "请授予「未知来源应用」安装权限后，再点「立即安装」",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "软件更新",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "单仓库补丁更新 · ${SelfUpdate.UPDATE_REPO}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                "本机 v${installed.versionName}（${installed.versionCode}）",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline
            )
        }
        SpacerH(10.dp)

        // 状态机过渡：淡入 + 轻上滑（检查→可用→下载→就绪的形态切换动态）
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(spring(stiffness = 420f)) +
                    slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 12 }) togetherWith
                    fadeOut(spring(stiffness = 420f))
            },
            label = "updateState"
        ) { s ->
            when (s) {
                is UpdateUiState.Idle, is UpdateUiState.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassButton(
                            onClick = { check() },
                            backdrop = backdrop,
                            enabled = s !is UpdateUiState.Checking,
                            height = 44.dp,
                            tint = MaterialTheme.colorScheme.primary,
                            fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                        ) {
                            if (s is UpdateUiState.Checking) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.height(16.dp).width(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    "检查中…",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            } else {
                                Text(
                                    "检查更新",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        SpacerW(10.dp)
                        Text(
                            "启动时已自动静默检查；失败不打扰",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                is UpdateUiState.UpToDate -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    SpacerW(8.dp)
                    Text(
                        "已是最新（v${installed.versionName}）",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                is UpdateUiState.Available -> {
                    val m = s.manifest
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            SpacerW(8.dp)
                            Text(
                                "新版本 v${m.versionName}（${m.versionCode}）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        m.notes?.let { notes ->
                            SpacerH(6.dp)
                            Text(
                                notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SpacerH(10.dp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (s.viaPatch && m.patch != null) {
                                GlassButton(
                                    onClick = { downloadPatch(m) },
                                    backdrop = backdrop,
                                    height = 46.dp,
                                    tint = MaterialTheme.colorScheme.primary,
                                    fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                ) {
                                    Text(
                                        "增量更新（${SelfUpdate.mb(m.patch.sizeBytes)}）",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                GlassButton(
                                    onClick = { downloadFull(m) },
                                    backdrop = backdrop,
                                    height = 46.dp
                                ) {
                                    Text(
                                        "完整包 ${SelfUpdate.mb(m.download.sizeBytes)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else {
                                GlassButton(
                                    onClick = { downloadFull(m) },
                                    backdrop = backdrop,
                                    height = 46.dp,
                                    tint = MaterialTheme.colorScheme.primary,
                                    fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                ) {
                                    Text(
                                        "下载完整包（${SelfUpdate.mb(m.download.sizeBytes)}）",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                GlassButton(
                                    onClick = { check() },
                                    backdrop = backdrop,
                                    height = 46.dp
                                ) {
                                    Text("重新检查", style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        if (!s.viaPatch) {
                            SpacerH(4.dp)
                            Text(
                                "本机版本与补丁基底不匹配 —— 增量仅对上一版本可用，已自动回退全量。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                is UpdateUiState.Downloading -> {
                    val fraction = if (s.total > 0) (s.bytes.toFloat() / s.total).coerceIn(0f, 1f) else 0f
                    Column {
                        Text(
                            if (s.viaPatch) "下载补丁… ${SelfUpdate.mb(s.bytes)} / ${SelfUpdate.mb(s.total)}"
                            else "下载完整包… ${SelfUpdate.mb(s.bytes)} / ${SelfUpdate.mb(s.total)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        SpacerH(8.dp)
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        SpacerH(6.dp)
                        Text(
                            "SHA-256 终局对账；断点续传请用「重新检查」后重试（仅重下未完成资产）。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                is UpdateUiState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.height(18.dp).width(18.dp),
                        strokeWidth = 2.dp
                    )
                    SpacerW(10.dp)
                    Text(
                        s.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                is UpdateUiState.Ready -> Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        SpacerW(8.dp)
                        Text(
                            "产物就绪（${SelfUpdate.mb(s.apk.length())}）· SHA-256 已对账",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    SpacerH(10.dp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton(
                            onClick = { install(s.apk) },
                            backdrop = backdrop,
                            height = 46.dp,
                            tint = MaterialTheme.colorScheme.primary,
                            fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                        ) {
                            Text(
                                "立即安装",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        GlassButton(
                            onClick = { check() },
                            backdrop = backdrop,
                            height = 46.dp
                        ) {
                            Text(
                                "重新检查",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                is UpdateUiState.Failed -> Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        SpacerW(8.dp)
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    SpacerH(8.dp)
                    GlassButton(
                        onClick = { check() },
                        backdrop = backdrop,
                        height = 44.dp
                    ) {
                        Text(
                            "重试",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ── 连接配置卡 ───────────────────────────────────────────────────────────

@Composable
private fun ConnectionCard(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    onConnectionChanged: (repo: String, branch: String, token: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var repo by remember { mutableStateOf(PublisherPrefs.repo(context)) }
    var branch by remember { mutableStateOf(PublisherPrefs.branch(context)) }
    var token by remember { mutableStateOf(PublisherPrefs.token(context)) }
    var showToken by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
        Text(
            "连接配置",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        SpacerH(10.dp)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassTextField(
                backdrop = backdrop,
                value = repo,
                onValueChange = { repo = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = "目标仓库（owner/name）",
                singleLine = true
            )
            GlassTextField(
                backdrop = backdrop,
                value = branch,
                onValueChange = { branch = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = "分支（push 该分支即自动发版）",
                singleLine = true
            )
            // Token：普通 ASCII 键盘（不触发系统安全键盘 —— 安全键盘禁粘贴）；
            // 遮蔽仅做视觉转换，复制粘贴全链路可用；「粘贴」键直读剪贴板兜底。
            GlassTextField(
                backdrop = backdrop,
                value = token,
                onValueChange = { token = it; saved = false; testResult = null },
                modifier = Modifier.fillMaxWidth(),
                label = "GitHub Token（classic ghp_ / fine-grained 皆可）",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    Row {
                        TextButton(onClick = {
                            val pasted = clipboard.getText()?.text?.trim().orEmpty()
                            if (pasted.isNotEmpty()) {
                                token = pasted
                                saved = false
                                testResult = null
                            } else {
                                Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("粘贴") }
                        TextButton(onClick = { showToken = !showToken }) {
                            Text(if (showToken) "隐藏" else "显示")
                        }
                    }
                }
            )
        }
        SpacerH(4.dp)
        Text(
            "Token 仅存本机（allowBackup=false 无云备份）；泄露可随时在 GitHub 撤销；绝不写入任何提交。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        SpacerH(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                onClick = {
                    val r = repo.trim(); val b = branch.trim(); val t = token.trim()
                    PublisherPrefs.saveConnection(context, r, b, t)
                    saved = true
                    onConnectionChanged(r, b, t)
                    Toast.makeText(context, "已保存 —— 返回主页自动拉取版本", Toast.LENGTH_SHORT).show()
                },
                backdrop = backdrop,
                enabled = repo.contains("/") && branch.isNotBlank(),
                height = 46.dp,
                tint = MaterialTheme.colorScheme.primary,
                fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            ) {
                Text(
                    if (saved) "已保存 ✓" else "保存",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            GlassButton(
                onClick = {
                    if (token.isBlank()) {
                        testResult = "请先填入 Token"
                    } else {
                        testing = true
                        testResult = null
                        scope.launch {
                            GitHubApi(token.trim())
                                .testConnection(repo.trim())
                                .onSuccess { info ->
                                    testResult = "✅ 连接成功：${info.fullName}（默认分支 ${info.defaultBranch}）"
                                }
                                .onFailure { e ->
                                    testResult = "❌ ${e.message}"
                                }
                            testing = false
                        }
                    }
                },
                backdrop = backdrop,
                enabled = !testing,
                height = 46.dp
            ) {
                if (testing) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.height(16.dp).width(16.dp),
                        strokeWidth = 2.dp
                    )
                    SpacerW(6.dp)
                }
                Text(
                    if (testing) "测试中…" else "测试连接",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        testResult?.let {
            SpacerH(10.dp)
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Token 指引卡 ─────────────────────────────────────────────────────────

@Composable
private fun TokenGuideCard(backdrop: com.kyant.backdrop.backdrops.LayerBackdrop) {
    GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Token 创建指引（两种令牌都支持）",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        SpacerH(6.dp)
        Text(
            "Classic（ghp_ 开头）—— GitHub → Settings → Developer settings →\n" +
                "Personal access tokens (classic) → Generate new token：\n" +
                "1. 勾选 scope：repo（完整仓库读写，提交日志片段必须）\n" +
                "   —— 顺带勾 workflow 则可一并改工作流文件（可选）\n" +
                "2. Expiration 建议 90 天，到期轮换\n" +
                "\n" +
                "Fine-grained（github_pat_ 开头）—— Personal access tokens：\n" +
                "1. Repository access：Only select repositories → 勾选 ${PublisherPrefs.DEFAULT_REPO}\n" +
                "2. Permissions → Contents：Read and write（提交日志片段必须）\n" +
                "3. Metadata：Read-only（系统自动勾选）；其它权限一律不勾",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── 快捷链接卡 ───────────────────────────────────────────────────────────

@Composable
private fun LinksCard(backdrop: com.kyant.backdrop.backdrops.LayerBackdrop) {
    GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
        Text(
            "快捷入口",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        SpacerH(6.dp)
        LinkRow(backdrop = backdrop, label = "创建 Token（classic）", url = "https://github.com/settings/tokens/new?scopes=repo")
        LinkRow(backdrop = backdrop, label = "创建 Token（fine-grained）", url = "https://github.com/settings/personal-access-tokens/new")
        LinkRow(backdrop = backdrop, label = "目标仓库", url = "https://github.com/${PublisherPrefs.DEFAULT_REPO}")
        LinkRow(backdrop = backdrop, label = "发布流水线（Actions）", url = "https://github.com/${PublisherPrefs.DEFAULT_REPO}/actions")
        LinkRow(backdrop = backdrop, label = "主 App 读取的 changelog.json", url = "https://raw.githubusercontent.com/Ultra-Guru/Android-Guru-Agent-Release/main/changelog.json")
        LinkRow(backdrop = backdrop, label = "发布器 Releases（自更新产物）", url = "https://github.com/${SelfUpdate.UPDATE_REPO}/releases")
        LinkRow(backdrop = backdrop, label = "发布器 version.json", url = SelfUpdate.MANIFEST_URL)
    }
}

@Composable
private fun LinkRow(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    label: String,
    url: String
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        GlassIconButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
            backdrop = backdrop,
            icon = Icons.Outlined.OpenInNew,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            size = 36.dp
        )
    }
}
