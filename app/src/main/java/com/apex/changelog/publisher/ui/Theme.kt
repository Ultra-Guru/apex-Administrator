package com.apex.changelog.publisher.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 发布器主题 —— 与启动图标（薄荷绿发丝 / 紫瞳 / 奶油底）同源的
 * 「薄荷 × 薰衣草」色系，为全局液态玻璃壁纸定调。
 */
object PublisherTheme {

    private val Mint = Color(0xFF2E8B6B)
    private val MintDeep = Color(0xFF1E6B52)
    private val MintContainer = Color(0xFFB7F0D8)
    private val Lavender = Color(0xFF7C6BD8)
    private val LavenderContainer = Color(0xFFE6DFFF)
    private val Gold = Color(0xFFB8862F)

    val light = lightColorScheme(
        primary = Mint,
        onPrimary = Color.White,
        primaryContainer = MintContainer,
        onPrimaryContainer = Color(0xFF0B291F),
        secondary = Lavender,
        onSecondary = Color.White,
        secondaryContainer = LavenderContainer,
        onSecondaryContainer = Color(0xFF271B52),
        tertiary = Gold,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF7E6C4),
        onTertiaryContainer = Color(0xFF402D00),
        background = Color(0xFFF4FBF7),
        onBackground = Color(0xFF14231F),
        surface = Color(0xFFEFF7F2),
        onSurface = Color(0xFF14231F),
        surfaceVariant = Color(0xFFE2EBE6),
        onSurfaceVariant = Color(0xFF41504B),
        outline = Color(0xFF6F7B76),
        outlineVariant = Color(0xFFC0CCC7),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002)
    )

    val dark = darkColorScheme(
        primary = Color(0xFF8FDDBD),
        onPrimary = Color(0xFF05382A),
        primaryContainer = Color(0xFF0F5B45),
        onPrimaryContainer = MintContainer,
        secondary = Color(0xFFC9BFFF),
        onSecondary = Color(0xFF332A63),
        secondaryContainer = Color(0xFF4A4394),
        onSecondaryContainer = LavenderContainer,
        tertiary = Color(0xFFE9C46A),
        onTertiary = Color(0xFF402D00),
        tertiaryContainer = Color(0xFF5C4400),
        onTertiaryContainer = Color(0xFFF7E6C4),
        background = Color(0xFF0C1210),
        onBackground = Color(0xFFE4EFEA),
        surface = Color(0xFF121A17),
        onSurface = Color(0xFFE4EFEA),
        surfaceVariant = Color(0xFF22302B),
        onSurfaceVariant = Color(0xFFB2C1BB),
        outline = Color(0xFF93A29B),
        outlineVariant = Color(0xFF41504B),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    )
}
