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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.apex.changelog.publisher.data.SelfUpdate
import com.apex.changelog.publisher.ui.MainScreen
import com.apex.changelog.publisher.ui.SettingsScreen
import com.apex.changelog.publisher.ui.PublisherTheme
import com.apex.changelog.publisher.ui.UpdateUiState
import com.apex.changelog.publisher.ui.silentCheck
import com.apex.changelog.publisher.ui.glass.AuroraWallpaper
import com.apex.changelog.publisher.ui.glass.GlassIconButton
import com.apex.changelog.publisher.ui.glass.GlassTopBar
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 更新日志发布器（apex-Administrator）—— Android-Guru-Agent 的管理端工具。
 *
 * v2 —— 全局液态玻璃（Kyant0 Backdrop 库）+ 单仓库补丁更新：
 * - 极光壁纸为所有玻璃面提供折射底材（亮暗双态）；
 * - 启动静默检查自更新（网络失败静默降级，不打扰）；
 * - 两页面导航（主页 / 设置），滑动 + 淡入转场。
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
    var screen by rememberSaveable { mutableStateOf(Screen.Main) }
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    val context = LocalContext.current

    // 启动静默自检（失败保持 Idle —— 不打扰）
    LaunchedEffect(Unit) {
        updateState = silentCheck(updateState, SelfUpdate.installed(context))
    }

    Column(Modifier.fillMaxSize()) {
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

        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val dir = if (targetState == Screen.Settings) 1 else -1
                (slideInHorizontally { it / 3 * dir } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 3 * dir } + fadeOut())
            },
            label = "screenNav"
        ) { current ->
            when (current) {
                Screen.Main -> MainScreen(
                    backdrop = backdrop,
                    updateState = updateState,
                    onGoSettings = { screen = Screen.Settings }
                )
                Screen.Settings -> SettingsScreen(
                    backdrop = backdrop,
                    updateState = updateState,
                    onUpdateState = { updateState = it }
                )
            }
        }
    }
}
