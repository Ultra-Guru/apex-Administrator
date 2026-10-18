package com.apex.changelog.publisher.ui.glass

import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  玻璃调谐（v2.2）—— 液态玻璃随背景联动折射的参数面
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 背景（用户需求）：背景可自定义后，玻璃不能再是一套死参数 —— 「液态玻璃
 * 会随着背景的变化而发出不同的折射」。本模型把背景派生参数收敛为四个维度：
 *
 * - [cardTintLight] / [cardTintDark]：玻璃表面色（亮/暗主题各一）。自定义
 *   图片背景时向背景主导色靠拢 —— 玻璃像是从背景里「长」出来的；
 * - [sheenLight] / [sheenDark]：流光高光色（扫过玻璃面的光带）。取背景
 *   的强调色 —— 换背景 = 换一道光；
 * - [refraction]：折射强度 0..1。背景明暗对比越强，玻璃边缘的 lens
 *   折射越明显（对比弱的单色背景自动收敛，不会假得刺眼）。
 *
 * 传递链：BackgroundStore.analyze（图片）/ GradientPreset（预设）→
 * [LocalGlassTuning]（AppRoot 提供，背景切换即变）→ 全部 Glass* 组件读取。
 * 中性默认 = v2.1 的极光配色，未提供时（预览/降级）零差异。
 */
data class GlassTuning(
    val cardTintLight: Color = Color.White.copy(alpha = 0.15f),
    val cardTintDark: Color = Color.Black.copy(alpha = 0.26f),
    val barTintLight: Color = Color.White.copy(alpha = 0.22f),
    val barTintDark: Color = Color.Black.copy(alpha = 0.32f),
    val fieldTintLight: Color = Color.White.copy(alpha = 0.12f),
    val fieldTintDark: Color = Color.Black.copy(alpha = 0.22f),
    val sheenLight: Color = Color(0xFFFFFFFF),
    val sheenDark: Color = Color(0xFFBFE9D6),
    /** 折射强度 0..1 —— lens 参数与流光不透明度的乘性因子。 */
    val refraction: Float = 0.85f
) {
    fun cardTint(dark: Boolean): Color = if (dark) cardTintDark else cardTintLight
    fun barTint(dark: Boolean): Color = if (dark) barTintDark else barTintLight
    fun fieldTint(dark: Boolean): Color = if (dark) fieldTintDark else fieldTintLight
    fun sheen(dark: Boolean): Color = if (dark) sheenDark else sheenLight

    companion object {
        /** 中性默认（与 v2.1 视觉零差异）—— 极光壁纸与所有降级路径共用。 */
        val Neutral = GlassTuning(
            cardTintLight = Color.White.copy(alpha = 0.15f),
            cardTintDark = Color.Black.copy(alpha = 0.26f),
            barTintLight = Color.White.copy(alpha = 0.22f),
            barTintDark = Color.Black.copy(alpha = 0.32f),
            fieldTintLight = Color.White.copy(alpha = 0.12f),
            fieldTintDark = Color.Black.copy(alpha = 0.22f),
            sheenLight = Color(0xFFFFFFFF),
            sheenDark = Color(0xFFBFE9D6),
            refraction = 0.85f
        )
    }
}

/** 玻璃调谐注入点 —— AppRoot 按当前背景提供；组件缺省回退 [GlassTuning.Neutral]。 */
val LocalGlassTuning = staticCompositionLocalOf { GlassTuning.Neutral }

/**
 * 流光时钟（0..1 循环相位）—— 全 App 单一时钟驱动所有玻璃面的光带扫掠。
 * null = 静态（预览 / 时钟未就绪）：组件画一次性固定高光，零重绘开销。
 *
 * 由 AppRoot 的 rememberGlassClock() 以 ~24fps 推进（9s 一个扫掠周期），
 * 与壁纸漂移共用帧预算；实现见 [rememberGlassClock]。
 */
val LocalGlassClock = staticCompositionLocalOf<State<Float>?> { null }
