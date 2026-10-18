package com.apex.changelog.publisher.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  全局液态玻璃设计系统 —— Kyant0 Backdrop（Compose 最先进的液态玻璃库）
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * v2.2 动态玻璃 + 背景联动折射（本轮用户需求核心）：
 * - **流光扫掠**（[drawSheenSweep]）：全部玻璃面共有一道斜向光带随
 *   [LocalGlassClock] 缓慢扫过（9s 周期，24fps）—— 纯 onDrawSurface
 *   渐变绘制，任意 API 级别可见（不依赖 RenderEffect），与折射叠加
 *   呈现「光在玻璃上流动」的液态质感；
 * - **背景联动**：表面色 / 流光色 / 折射强度全部读 [LocalGlassTuning]
 *   （背景派生），换背景 = 换玻璃 —— 极光 / 深海 / 落日 / 相册图各异；
 * - **性能分级收敛**：lens 折射只保留英雄面（dark 底部导航栏）；卡片 /
 *   按钮 / 输入框仅 blur + vibrancy + 流光 —— 同屏 N 面玻璃的
 *   RenderEffect 负载较 v2.1 再降（按钮去 lens 是最大单项）。
 *
 * v2.1 保留：级联入场（[GlassEntrance]）+ 按压弹簧缩放 —— 「液面呼吸感」。
 */

/**
 * 流光扫掠 —— 玻璃面上的斜向光带（主带 + 弱拖尾），随 [clock] 相位 0..1
 * 横向扫过。时钟为 null（预览/降级）或折射强度近零时静默跳过，零开销。
 * 在 onDrawSurface 内调用 —— 读 State 触发的仅是重绘（不重组不重测量）。
 */
internal fun DrawScope.drawSheenSweep(
    clock: State<Float>?,
    sheen: Color,
    refraction: Float
) {
    if (clock == null || refraction <= 0.02f) return
    val t = clock.value
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val band = w * 0.75f
    val x = -band + (w + 2f * band) * t

    // 主光带（对角）
    drawRect(
        brush = Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to sheen.copy(alpha = (0.16f * refraction).coerceIn(0.02f, 0.22f)),
            1f to Color.Transparent,
            start = Offset(x, 0f),
            end = Offset(x + band, h)
        )
    )
    // 弱拖尾（错开 55% 带宽 —— 双带交叠出「液面波光」层次）
    val x2 = x - band * 0.55f
    drawRect(
        brush = Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to sheen.copy(alpha = (0.07f * refraction).coerceIn(0.01f, 0.10f)),
            1f to Color.Transparent,
            start = Offset(x2, 0f),
            end = Offset(x2 + band, h)
        )
    )
}

/** 通用玻璃卡片容器（数量最多 —— 不用 lens，仅 blur + 流光，性能优先）。 */
@Composable
fun GlassCard(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    tint: Color = Color.Unspecified,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = isSystemInDarkTheme()
    val tuning = LocalGlassTuning.current
    val clock = LocalGlassClock.current
    val surface = if (tint.isSpecified) tint else tuning.cardTint(dark)
    Column(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(cornerRadius) },
                effects = {
                    vibrancy()
                    blur(6f.dp.toPx())
                },
                highlight = { Highlight.Default },
                shadow = {
                    Shadow(
                        radius = 22.dp,
                        offset = DpOffset(0.dp, 7.dp),
                        color = Color.Black.copy(alpha = if (dark) 0.45f else 0.10f)
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 16.dp,
                        offset = DpOffset(0.dp, 8.dp),
                        color = Color.Black.copy(alpha = if (dark) 0.28f else 0.07f)
                    )
                },
                onDrawSurface = {
                    drawRect(surface)
                    drawSheenSweep(clock, tuning.sheen(dark), tuning.refraction)
                }
            )
            .padding(padding),
        content = content
    )
}

/**
 * 玻璃按钮（胶囊形，按压缩放 + 流光）。
 * tint/fill 同时为 Unspecified 时为「素玻璃」按钮（次级操作用）。
 * v2.2：去 lens（性能 —— 主屏按钮数量最多），动态感由流光 + 按压承载。
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    fill: Color = Color.Unspecified,
    height: Dp = 52.dp,
    content: @Composable RowScope.() -> Unit
) {
    val dark = isSystemInDarkTheme()
    val tuning = LocalGlassTuning.current
    val clock = LocalGlassClock.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 420f
        ),
        label = "glassPressScale"
    )
    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(3f.dp.toPx())
                },
                highlight = { Highlight.Default },
                shadow = {
                    Shadow(
                        radius = 16.dp,
                        offset = DpOffset(0.dp, 5.dp),
                        color = Color.Black.copy(alpha = if (enabled) 0.14f else 0.06f)
                    )
                },
                innerShadow = {
                    InnerShadow(radius = 10.dp, offset = DpOffset(0.dp, 5.dp))
                },
                layerBlock = {
                    scaleX = scale
                    scaleY = scale
                },
                onDrawSurface = {
                    val effTint = if (enabled) tint else Color.Unspecified
                    val effFill = if (enabled) fill else Color.Unspecified
                    if (effTint.isSpecified) {
                        drawRect(effTint, blendMode = BlendMode.Hue)
                        drawRect(effTint.copy(alpha = 0.68f))
                    }
                    if (effFill.isSpecified) {
                        drawRect(effFill)
                    }
                    drawSheenSweep(clock, tuning.sheen(dark), tuning.refraction)
                }
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .then(Modifier.height(height))
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * 玻璃图标按钮（小型操作）—— 按压弹簧缩放 + 流光；小面不用 lens。
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    backdrop: LayerBackdrop,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = Color.Unspecified,
    size: Dp = 44.dp
) {
    val dark = isSystemInDarkTheme()
    val tuning = LocalGlassTuning.current
    val clock = LocalGlassClock.current
    val effectiveTint = if (tint.isSpecified) tint else MaterialTheme.colorScheme.onSurface
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 500f
        ),
        label = "iconPressScale"
    )
    Box(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                },
                highlight = { Highlight.Default },
                innerShadow = { InnerShadow(radius = 8.dp, offset = DpOffset(0.dp, 4.dp)) },
                layerBlock = {
                    scaleX = scale
                    scaleY = scale
                },
                onDrawSurface = {
                    drawRect(tuning.barTint(dark))
                    drawSheenSweep(clock, tuning.sheen(dark), tuning.refraction)
                }
            )
            .size(size)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = true,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = effectiveTint)
    }
}

/** 玻璃输入框：玻璃卡承载 + 透明填充的 OutlinedTextField。 */
@Composable
fun GlassTextField(
    backdrop: LayerBackdrop,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    supporting: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    GlassCard(
        backdrop = backdrop,
        modifier = modifier,
        cornerRadius = 22.dp,
        tint = LocalGlassTuning.current.fieldTint(dark),
        padding = PaddingValues(6.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            label = label?.let { text -> { Text(text) } },
            supportingText = supporting?.let { text ->
                { Text(text, style = MaterialTheme.typography.labelSmall) }
            },
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            trailingIcon = trailing,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = scheme.primary.copy(alpha = 0.75f),
                unfocusedBorderColor = scheme.outlineVariant.copy(alpha = 0.9f),
                focusedLabelColor = scheme.primary,
                unfocusedLabelColor = scheme.outline,
                focusedTextColor = scheme.onBackground,
                unfocusedTextColor = scheme.onBackground,
                cursorColor = scheme.primary,
                focusedSupportingTextColor = scheme.outline,
                unfocusedSupportingTextColor = scheme.outline,
                focusedTrailingIconColor = scheme.primary,
                unfocusedTrailingIconColor = scheme.outline
            )
        )
    }
}

/** 行内通知条（更新可用 / 结果反馈等）。 */
@Composable
fun GlassBanner(
    backdrop: LayerBackdrop,
    icon: ImageVector,
    iconTint: Color,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    GlassCard(backdrop = backdrop, modifier = modifier, cornerRadius = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            SpacerW(12.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            if (actionLabel != null && onAction != null) {
                GlassButton(
                    onClick = onAction,
                    backdrop = backdrop,
                    height = 40.dp,
                    tint = iconTint.copy(alpha = 0.9f),
                    fill = iconTint.copy(alpha = 0.16f)
                ) {
                    Text(
                        actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = iconTint
                    )
                }
            }
        }
    }
}

/** 小间距工具（避免每个调用点重复 import Spacer）。 */
@Composable
fun SpacerW(width: Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.width(width))
}

@Composable
fun SpacerH(height: Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(height))
}

/** 卡内分割线（玻璃上低对比）。 */
@Composable
fun GlassDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    )
}

/**
 * 玻璃面级联入场 —— 淡入 + 上滑 + 微缩放（一次性，spring 收尾）。
 * [delayMillis] 错峰：同屏卡片依次 60ms 级联，营造「液面逐层浮起」的动态。
 * 代价极低（单面单次属性动画，不触发布局测量），与性能分级兼容。
 */
@Composable
fun GlassEntrance(
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    val state = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis.toLong())
        state.targetState = true
    }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(animationSpec = spring(stiffness = 380f)) +
            slideInVertically(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 320f),
                initialOffsetY = { it / 10 }
            ) +
            scaleIn(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                initialScale = 0.96f
            )
    ) {
        content()
    }
}
