package com.apex.changelog.publisher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.apex.changelog.publisher.data.PublisherPrefs
import com.apex.changelog.publisher.data.SelfUpdate
import com.apex.changelog.publisher.ui.Connection
import com.apex.changelog.publisher.ui.MainScreen
import com.apex.changelog.publisher.ui.SettingsScreen
import com.apex.changelog.publisher.ui.PublisherTheme
import com.apex.changelog.publisher.ui.UpdateUiState
import com.apex.changelog.publisher.ui.silentCheck
import com.apex.changelog.publisher.ui.glass.AuroraWallpaper
import com.apex.changelog.publisher.ui.glass.GlassEntrance
import com.apex.changelog.publisher.ui.glass.GlassIconButton
import com.apex.changelog.publisher.ui.glass.GlassTopBar
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 更新日志发布器（apex-Administrator）—— Android-Guru-Agent 的管理端工具。
 *
 * v2.1 —— 性能分级液态玻璃 + 全局动态 + 连接状态提升：
 * - 极光壁纸 24fps 节流（底材重录降 60%+），lens 收敛到英雄面（顶栏/主按钮）；
 * - 卡片级联入场 / 按压弹簧反馈 / 页面弹簧转场 —— 「液面呼吸感」；
 * - 连接（仓库/分支/Token）提升到 AppRoot 单一事实源：设置页保存即生效，
 *   主页 LaunchedEffect(connection) 自动重拉版本定位与暂存列表（旧版
 *   remember 无键 → 设置改完返回主页不刷新的缺陷根治）；
 * - imePadding：键盘弹出时输入区上移（旧版被键盘遮挡的布局缺陷）。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}

@Composable
private fun AppRoot() {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) PublisherTheme.dark else PublisherTheme.light
    ) {
        val backdrop = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize()) {
            // 折射底材：极光壁纸（被全部玻璃面采样）
            AuroraWallpaper(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            )
            PublisherApp(backdrop)
        }
    }
}

/** 两页面导航（工具级，不引入导航库）。 */
private enum class Screen { Main, Settings }

@Composable
private fun PublisherApp(backdrop: LayerBackdrop) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.Main) }
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    // 连接单一事实源：进入时读一次，设置页保存回调更新 —— 主页即时反应
    var connection by remember {
        mutableStateOf(
            Connection(
                repo = PublisherPrefs.repo(context),
                branch = PublisherPrefs.branch(context),
                token = PublisherPrefs.token(context)
            )
        )
    }

    // 启动静默自检（失败保持 Idle —— 不打扰）
    LaunchedEffect(Unit) {
        updateState = silentCheck(updateState, SelfUpdate.installed(context))
    }

    Column(Modifier.fillMaxSize()) {
        GlassEntrance(delayMillis = 0) {
            GlassTopBar(
                backdrop = backdrop,
                title = "更新日志发布器",
                subtitle = when (screen) {
                    Screen.Main -> "Ultra-Guru/Android-Guru-Agent · 写好即发"
                    Screen.Settings -> "连接 · Token · 软件更新"
                },
                onBack = if (screen == Screen.Settings) {
                    { screen = Screen.Main }
                } else null,
                actions = {
                    if (screen == Screen.Main) {
                        GlassIconButton(
                            onClick = { screen = Screen.Settings },
                            backdrop = backdrop,
                            icon = Icons.Filled.Settings,
                            contentDescription = "设置"
                        )
                    }
                }
            )
        }

        AnimatedContent(
            targetState = screen,
            modifier = Modifier.fillMaxSize().weight(1f),
            transitionSpec = {
                val dir = if (targetState == Screen.Settings) 1 else -1
                (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 3 * dir } +
                    fadeIn(spring(stiffness = 420f))) togetherWith
                    (slideOutHorizontally(spring(dampingRatio = 0.85f, stiffness = 380f)) { -it / 3 * dir } +
                        fadeOut(spring(stiffness = 420f)))
            },
            label = "screenNav"
        ) { current ->
            when (current) {
                Screen.Main -> MainScreen(
                    backdrop = backdrop,
                    connection = connection,
                    updateState = updateState,
                    onGoSettings = { screen = Screen.Settings }
                )
                Screen.Settings -> SettingsScreen(
                    backdrop = backdrop,
                    updateState = updateState,
                    onUpdateState = { updateState = it },
                    onConnectionChanged = { repo, branch, token ->
                        connection = Connection(repo, branch, token)
                    }
                )
            }
        }
    }
}
