package com.apex.changelog.publisher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.apex.changelog.publisher.ui.MainScreen
import com.apex.changelog.publisher.ui.SettingsScreen

/**
 * 更新日志发布器 —— Android-Guru-Agent 的管理端工具（双页面 MVP）：
 *
 * - 主页：大输入框写日志 → 「发送」直推开发仓库 `.changelog/unreleased/`
 *   （push main 自动触发发布流水线 → 新版本 APK 内置本条日志 →
 *   主 App 升级后首启弹窗展示 —— 完整闭环的起点）；
 * - 设置页：目标仓库（预填）/ 分支 / GitHub Token（自填 + 测试连接）。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                PublisherApp()
            }
        }
    }
}

/** 两页面极简导航（工具级 MVP，不引导航库）。 */
private enum class Screen { Main, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublisherApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.Main) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("更新日志发布器") },
                navigationIcon = {
                    if (screen == Screen.Settings) {
                        IconButton(onClick = { screen = Screen.Main }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                },
                actions = {
                    if (screen == Screen.Main) {
                        IconButton(onClick = { screen = Screen.Settings }) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (screen) {
            Screen.Main -> MainScreen(contentPadding = padding)
            Screen.Settings -> SettingsScreen(contentPadding = padding)
        }
    }
}
