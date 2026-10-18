package com.apex.changelog.publisher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.apex.changelog.publisher.data.BackgroundStore
import com.apex.changelog.publisher.data.PublisherPrefs
import com.apex.changelog.publisher.data.SelfUpdate
import com.apex.changelog.publisher.ui.Connection
import com.apex.changelog.publisher.ui.MainScreen
import com.apex.changelog.publisher.ui.SettingsScreen
import com.apex.changelog.publisher.ui.PublisherTheme
import com.apex.changelog.publisher.ui.UpdateUiState
import com.apex.changelog.publisher.ui.silentCheck
import com.apex.changelog.publisher.ui.glass.BackgroundStyle
import com.apex.changelog.publisher.ui.glass.DriftingWallpaper
import com.apex.changelog.publisher.ui.glass.GlassDarkNavBar
import com.apex.changelog.publisher.ui.glass.GradientPresets
import com.apex.changelog.publisher.ui.glass.ImageWallpaper
import com.apex.changelog.publisher.ui.glass.LocalGlassClock
import com.apex.changelog.publisher.ui.glass.LocalGlassTuning
import com.apex.changelog.publisher.ui.glass.NavBarItem
import com.apex.changelog.publisher.ui.glass.rememberGlassClock
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 更新日志发布器（apex-Administrator）—— Android-Guru-Agent 的管理端工具。
 *
 * v2.2 —— 动态液态玻璃 + dark 底部导航 + 背景自定义：
 * - **页面切换改 dark 底部导航栏**（GlassDarkNavBar）：恒深色炭玻璃药丸，
 *   全 App 唯一 lens 折射英雄面 —— 背景光斑在栏缘实时折射；顶栏退役，
 *   屏幕让给内容；
 * - **背景自定义**：极光 / 深海 / 落日 / 兰紫 / 石墨 五套漂移预设 + 相册
 *   图片（Ken Burns 缓推）—— 全部被玻璃面采样；
 * - **玻璃随背景联动折射**：BackgroundStore 从背景派生主导色/强调色/对比度
 *   → GlassTuning（表面色/流光色/折射强度）经 CompositionLocal 注入全部
 *   玻璃面 —— 换背景 = 换玻璃；
 * - **动态玻璃**：全玻璃面流光扫掠（9s 周期共享时钟 24fps 推进，退后台
 *   冻结）；壁纸漂移同样生命周期感知 —— 暗屏零开销；
 * - **性能**：按钮全面去 lens（v2.1 卡顿主因的最后一处），折射收敛到
 *   导航栏单一英雄面。
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
        val context = LocalContext.current

        // ── 背景状态（单一事实源）：风格 + 图片位图 + 派生玻璃调谐 ──
        var bgStyle by remember {
            mutableStateOf(BackgroundStyle.fromKey(PublisherPrefs.backgroundStyle(context)))
        }
        var imageBg by remember { mutableStateOf<ImageBitmap?>(null) }
        var tuning by remember { mutableStateOf(GradientPresets.Aurora.tuning) }

        LaunchedEffect(bgStyle) {
            if (bgStyle is BackgroundStyle.Image) {
                val loaded = withContext(Dispatchers.IO) {
                    BackgroundStore.loadImageWithTuning(context)
                }
                if (loaded != null) {
                    imageBg = loaded.first
                    tuning = loaded.second
                } else {
                    // 图片缺失/损坏 → 回退极光（并修正持久化，下次启动不再尝试）
                    imageBg = null
                    bgStyle = BackgroundStyle.Aurora
                    PublisherPrefs.saveBackgroundStyle(context, BackgroundStyle.Aurora.key)
                    tuning = GradientPresets.Aurora.tuning
                }
            } else {
                imageBg = null
                tuning = GradientPresets.byKey(
                    (bgStyle as? BackgroundStyle.Preset)?.presetKey
                ).tuning
            }
        }

        // ── 流光时钟：全部玻璃面共享（9s 扫掠，24fps，退后台冻结）──
        val clock = rememberGlassClock()

        CompositionLocalProvider(
            LocalGlassClock provides clock,
            LocalGlassTuning provides tuning
        ) {
            Box(Modifier.fillMaxSize()) {
                // 折射底材：随风格切换的背景层（被全部玻璃面采样）
                BackgroundLayer(
                    style = bgStyle,
                    image = imageBg,
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop)
                )
                PublisherApp(
                    backdrop = backdrop,
                    onBackgroundChange = { style ->
                        bgStyle = style
                        PublisherPrefs.saveBackgroundStyle(context, style.key)
                    }
                )
            }
        }
    }
}

/** 背景层分发：预设 → 漂移壁纸；图片 → Ken Burns（缺图回退极光）。 */
@Composable
private fun BackgroundLayer(style: BackgroundStyle, image: ImageBitmap?, modifier: Modifier) {
    when (style) {
        is BackgroundStyle.Preset ->
            DriftingWallpaper(GradientPresets.byKey(style.presetKey), modifier)
        is BackgroundStyle.Image ->
            if (image != null) ImageWallpaper(image, modifier)
            else DriftingWallpaper(GradientPresets.Aurora, modifier)
        BackgroundStyle.Aurora ->
            DriftingWallpaper(GradientPresets.Aurora, modifier)
    }
}

/** 两页面导航（dark 底部导航栏切换，工具级不引入导航库）。 */
private enum class Screen { Main, Settings }

@Composable
private fun PublisherApp(
    backdrop: LayerBackdrop,
    onBackgroundChange: (BackgroundStyle) -> Unit
) {
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
        // ── 内容区（页面自身带 statusBarsPadding 头部）──
        AnimatedContent(
            targetState = screen,
            modifier = Modifier.fillMaxSize().weight(1f),
            transitionSpec = {
                // 淡入淡出 + 轻缩放 —— 与液态玻璃的柔和气质一致
                (fadeIn(spring(stiffness = 420f)) +
                    scaleIn(
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                        initialScale = 0.98f
                    )) togetherWith
                    (fadeOut(spring(stiffness = 420f)) +
                        scaleOut(
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                            targetScale = 0.98f
                        ))
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
                    },
                    onBackgroundChange = onBackgroundChange
                )
            }
        }

        // ── dark 底部导航栏：页面切换锚点 + 折射英雄面 ──
        GlassDarkNavBar(
            backdrop = backdrop,
            items = listOf(
                NavBarItem("main", "日志", Icons.Outlined.EditNote),
                NavBarItem("settings", "设置", Icons.Outlined.Settings)
            ),
            selectedKey = when (screen) {
                Screen.Main -> "main"
                Screen.Settings -> "settings"
            },
            onSelect = { key ->
                screen = if (key == "settings") Screen.Settings else Screen.Main
            }
        )
    }
}
