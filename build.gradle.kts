// apex-Administrator —— Android-Guru-Agent 的更新日志发布器（管理端工具）
//
// 工具链（全部坐标经 GMaven / Maven Central 实时核实，并对照已验证的
// backdrop 消费组合）：
//   • Kotlin 2.4.21 —— backdrop 2.0.1 以 Kotlin 2.4.10 元数据发布，消费端需 ≥2.3；
//   • AGP 9.1.0 —— Compose 1.12.x（BOM 2026.09.00）与 backdrop/shapes 的
//     AAR 元数据要求 minCompileSdk 37 + minAndroidGradlePluginVersion 9.1.0；
//   • R8 9.4.24 显式固定 —— Kotlin 2.4 元数据需要 R8 ≥ 9.1.29（classpath
//     覆盖 AGP 内嵌版本，DEX/压缩链路全程兼容）。
buildscript {
    repositories {
        google()
    }
    dependencies {
        classpath("com.android.tools:r8:9.4.24")
    }
}

plugins {
    id("com.android.application") version "9.1.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
}
