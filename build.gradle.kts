// apex-changelog-publisher —— Android-Guru-Agent 的更新日志发布器（管理端工具）
// 版本与主仓库同栈（AGP 8.7.3 / Kotlin 2.0.21 / Compose BOM 2024.12.01），
// 单模块零 Hilt，MVP 刻意保持最小。
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
