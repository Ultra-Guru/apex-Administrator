package com.apex.changelog.publisher.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
import com.kyant.shapes.RoundedRectangle

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  Dark 底部导航栏（v2.2）—— 页面切换的锚点 & 液态玻璃折射的英雄面
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * - **恒深色**（dark bar）：不随系统亮暗主题切换 —— 深炭玻璃药丸悬浮于
 *   底部，任何壁纸/主题下都是稳定的视觉锚点（用户指定的「dark 栏」）；
 * - **折射英雄面**：全 App 唯一保留 lens 折射的面 —— 边缘 24dp 折射带
 *   实时折射背后漂移的背景光斑，配流光扫掠 = 「液态」的直接观感；
 * - **背景联动**：表面叠一层背景主导色的极淡着色（LocalGlassTuning），
 *   换背景时暗玻璃的色温随之一变 —— 联动而不失「dark」的沉稳；
 * - 选中项 = 亮玻璃药丸（blur + 白色薄雾 + 流光 + 弹簧按压），未选中项
 *   素描灰（零 backdrop 开销）。
 */
data class NavBarItem(
    val key: String,
    val label: String,
    val icon: ImageVector
)

/**
 * @param items 导航项（2-4 个）
 * @param selectedKey 当前选中项 key
 * @param onSelect 点击切换（按压弹簧缩放反馈）
 */
@Composable
fun GlassDarkNavBar(
    backdrop: LayerBackdrop,
    items: List<NavBarItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tuning = LocalGlassTuning.current
    val clock = LocalGlassClock.current

    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(34.dp) },
                effects = {
                    vibrancy()
                    blur(14f.dp.toPx())
                    // 折射英雄面：边缘折射带随背景光斑流动而实时变化
                    lens(9f.dp.toPx(), 24f.dp.toPx())
                },
                highlight = { Highlight.Default },
                shadow = {
                    Shadow(
                        radius = 26.dp,
                        offset = DpOffset(0.dp, 9.dp),
                        color = Color.Black.copy(alpha = 0.55f)
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 14.dp,
                        offset = DpOffset(0.dp, 7.dp),
                        color = Color.Black.copy(alpha = 0.35f)
                    )
                },
                onDrawSurface = {
                    // 恒深色炭黑玻璃基底
                    drawRect(Color(0xFF0D1315).copy(alpha = 0.60f))
                    // 背景主导色的极淡着色 —— 暗玻璃色温随背景联动
                    drawRect(tuning.cardTintDark.copy(alpha = 0.16f))
                    drawSheenSweep(clock, tuning.sheenDark, tuning.refraction)
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            DarkNavItem(
                backdrop = backdrop,
                item = item,
                selected = item.key == selectedKey,
                onClick = { onSelect(item.key) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 单个导航项：选中 = 亮玻璃药丸（流光 + 折射）；未选中 = 素灰文本钮。 */
@Composable
private fun DarkNavItem(
    backdrop: LayerBackdrop,
    item: NavBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tuning = LocalGlassTuning.current
    val clock = LocalGlassClock.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "navPressScale"
    )
    val contentColor = if (selected) Color(0xFFEDFFF2) else Color(0xFF93A29A)

    val surfaceModifier = if (selected) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { RoundedRectangle(24.dp) },
            effects = {
                vibrancy()
                blur(8f.dp.toPx())
            },
            highlight = { Highlight.Default },
            layerBlock = {
                scaleX = scale
                scaleY = scale
            },
            onDrawSurface = {
                drawRect(Color.White.copy(alpha = 0.13f))
                drawSheenSweep(clock, Color.White, tuning.refraction)
            }
        )
    } else {
        Modifier
    }

    Row(
        modifier
            .then(surfaceModifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = true,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            item.label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else null
        )
    }
}
