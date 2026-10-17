package com.apex.changelog.publisher.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.apex.changelog.publisher.data.GitHubApi
import com.apex.changelog.publisher.data.PublisherPrefs
import kotlinx.coroutines.launch

/**
 * 设置页 —— 目标仓库（预填主仓库）/ 分支 / GitHub Token（自填 + 测试连接）。
 *
 * Token 勾选指引（fine-grained，github_pat_ 开头）：
 * - Repository access：Only select repositories → 勾选目标仓库；
 * - Contents：Read and write（提交片段必须）；
 * - Metadata：Read-only（系统自动）；
 * - 其它（Workflows / Administration / Secrets / Pull requests）一律不勾。
 */
@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var repo by remember { mutableStateOf(PublisherPrefs.repo(context)) }
    var branch by remember { mutableStateOf(PublisherPrefs.branch(context)) }
    var token by remember { mutableStateOf(PublisherPrefs.token(context)) }
    var showToken by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = repo,
            onValueChange = { repo = it; saved = false },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("目标仓库（owner/name）") }
        )
        OutlinedTextField(
            value = branch,
            onValueChange = { branch = it; saved = false },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("分支（push 该分支即自动发版）") }
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it; saved = false; testResult = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("GitHub Token（fine-grained 推荐）") },
            visualTransformation = if (showToken) VisualTransformation.None
            else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                androidx.compose.material3.TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "隐藏" else "显示")
                }
            },
            supportingText = {
                Text(
                    "仅存本机（已禁用云备份）；泄露可随时在 GitHub 撤销。绝不写入任何提交。",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    PublisherPrefs.saveConnection(context, repo, branch, token)
                    saved = true
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                },
                enabled = repo.contains("/") && branch.isNotBlank()
            ) {
                Text(if (saved) "已保存 ✓" else "保存")
            }
            OutlinedButton(
                onClick = {
                    if (token.isBlank()) {
                        testResult = "请先填入 Token"
                        return@OutlinedButton
                    }
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
                },
                enabled = !testing
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(16.dp).width(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (testing) "测试中…" else "测试连接")
            }
        }

        testResult?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    it,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // ── Token 权限指引 ─────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "fine-grained Token 勾选指引",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "GitHub → Settings → Developer settings → Fine-grained tokens → Generate new token：\n" +
                        "1. Repository access：Only select repositories → 勾选 ${PublisherPrefs.DEFAULT_REPO}\n" +
                        "2. Permissions → Contents：Read and write（提交日志片段必须）\n" +
                        "3. Metadata：Read-only（系统自动勾选）\n" +
                        "4. 其它权限（Workflows / Administration / Secrets 等）一律不勾\n" +
                        "5. Expiration 建议 90 天，到期轮换",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // ── 快捷链接 ───────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "快捷入口",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                LinkRow("创建 fine-grained Token", "https://github.com/settings/personal-access-tokens/new")
                LinkRow("目标仓库", "https://github.com/${PublisherPrefs.DEFAULT_REPO}")
                LinkRow("发布流水线（Actions）", "https://github.com/${PublisherPrefs.DEFAULT_REPO}/actions")
                LinkRow(
                    "主 App 读取的 changelog.json",
                    "https://raw.githubusercontent.com/Ultra-Guru/Android-Guru-Agent-Release/main/changelog.json"
                )
            }
        }
    }
}

@Composable
private fun LinkRow(label: String, url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        androidx.compose.material3.IconButton(onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }) {
            Icon(
                Icons.Outlined.OpenInNew,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
