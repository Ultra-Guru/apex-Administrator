package com.apex.changelog.publisher.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
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
 * 全局液态玻璃设计系统 —— 基于 Kyant0 Backdrop（io.github.kyant0:backdrop，
 * Compose 最先进的液态玻璃库）。
 *
 * 每个玻璃面 = 折射（lens，API 33+）+ 模糊（blur，API 31+）+ 色彩提升
 * （vibrancy）+ 白描边高光（Highlight）+ 外投影（Shadow）+ 顶部内阴影
 * （InnerShadow）+ 半透明表面色（onDrawSurface）。低于对应 API 的设备
 * 自动降级为纯半透明面（库内置处理，无需分支）。
 */
object GlassDefaults {

    fun cardTint(dark: Boolean): Color =
        if (dark) Color.Black.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.15f)

    fun barTint(dark: Boolean): Color =
        if (dark) Color.Black.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.22f)

    fun fieldTint(dark: Boolean): Color =
        if (dark) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f)
}

/** 通用玻璃卡片容器。 */
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
    val surface = if (tint.isSpecified) tint else GlassDefaults.cardTint(dark)
    Column(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(cornerRadius) },
                effects = {
                    vibrancy()
                    blur(6f.dp.toPx())
                    lens(10f.dp.toPx(), 22f.dp.toPx())
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
                onDrawSurface = { drawRect(surface) }
            )
            .padding(padding),
        content = content
    )
}

/**
 * 玻璃按钮（胶囊形，按压缩放 + 色相推移可选）。
 * tint/fill 同时为 Unspecified 时为「素玻璃」按钮（次级操作用）。
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
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
                    lens(12f.dp.toPx(), 26f.dp.toPx())
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

/** 玻璃图标按钮（顶栏动作 / 小型操作）。 */
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
    val effectiveTint = if (tint.isSpecified) tint else MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(8f.dp.toPx(), 20f.dp.toPx())
                },
                highlight = { Highlight.Default },
                innerShadow = { InnerShadow(radius = 8.dp, offset = DpOffset(0.dp, 4.dp)) },
                onDrawSurface = { drawRect(GlassDefaults.barTint(dark)) }
            )
            .size(size)
            .clickable(
                enabled = true,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = effectiveTint)
    }
}

/** 浮动胶囊玻璃顶栏（iOS 26 风格：悬浮于内容之上，边缘留白）。 */
@Composable
fun GlassTopBar(
    backdrop: LayerBackdrop,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val dark = isSystemInDarkTheme()
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(10f.dp.toPx())
                    lens(10f.dp.toPx(), 24f.dp.toPx())
                },
                highlight = { Highlight.Default },
                innerShadow = {
                    InnerShadow(radius = 12.dp, offset = DpOffset(0.dp, 6.dp))
                },
                shadow = {
                    Shadow(
                        radius = 18.dp,
                        offset = DpOffset(0.dp, 6.dp),
                        color = Color.Black.copy(alpha = if (dark) 0.40f else 0.10f)
                    )
                },
                onDrawSurface = { drawRect(GlassDefaults.barTint(dark)) }
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            GlassIconButton(
                onClick = onBack,
                backdrop = backdrop,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回"
            )
            SpacerW(10.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
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
        actions()
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
        tint = GlassDefaults.fieldTint(dark),
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
