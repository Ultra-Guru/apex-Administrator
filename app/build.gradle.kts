plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.apex.changelog.publisher"
    // backdrop / shapes / Compose 1.12.x 的 AAR 元数据均要求 minCompileSdk 37；
    // 字符串哈希形式（AGP 9.1 验证过的写法）+ gradle.properties 里的
    // suppressUnsupportedCompileSdk / overridePathCheck 双开关。
    compileSdkVersion("android-37.0")

    defaultConfig {
        // applicationId 不变 —— 已安装的发布器直接收到覆盖更新（含补丁更新）。
        applicationId = "com.apex.changelog.publisher"
        minSdk = 26
        targetSdk = 36

        // 版本地板值：CI 发版流水线会用 -PappVersionCode/-PappVersionName
        // 覆盖（versionCode = max(远端 version.json, 地板) 自增 —— 防撞号，
        // 与主仓库同款对账逻辑；versionName 由流水线追加构建序号）。
        // v2.3.0 地板：配图通道 + 效果预览已在线上以 2.2.0.6 误发过一版
        // （彼时未 bump 基名）—— 地板抬到 2.3.0 让自更新通道正确播报。
        val ciVersionCode = providers.gradleProperty("appVersionCode").orElse("").get().toIntOrNull()
        val ciVersionName = providers.gradleProperty("appVersionName").orElse("").get().ifBlank { null }
        versionCode = ciVersionCode ?: 7
        versionName = ciVersionName ?: "2.3.0"
    }

    // ── 签名（meng411722）────────────────────────────────────────────
    // 密钥文件走 GitHub Secrets（KEYSTORE_BASE64 → CI 解码为仓库根 keystore.jks，
    // 不入库）；密码/别名经 env 注入，本地兜底值仅配合本地 keystore.jks 使用 ——
    // 密钥文件本身才是秘密，密码字面量无密钥文件时不可利用。
    // 无密钥时回退 debug 签名（PR 校验构建 / 本地开发不断链）。
    val ksFile: File? = listOfNotNull(
        System.getenv("KEYSTORE_FILE")?.let(::File),
        rootProject.file("keystore.jks")
    ).firstOrNull { it.exists() }

    signingConfigs {
        create("publisher") {
            // v1/v2/v3 三方案全开（与主仓库同款）：minSdk≥24 时 AGP 默认只签 v2/v3，
            // 无 JAR 签名块 —— CI 的 keytool -printcert -jarfile（签名指纹提取）
            // 将读不到任何签名者而失败。v1 仅增 ~50KB，换来全工具链可验。
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            if (ksFile != null) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "meng411722"
                keyAlias = System.getenv("KEY_ALIAS") ?: "meng411722"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "meng411722"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ksFile != null) {
                signingConfigs.getByName("publisher")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        // 全限定引用，避免脚本顶部 import 位置歧义
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // material3 1.12 不再传递 animation-core —— animateFloatAsState/spring 所在构件，显式声明
    implementation("androidx.compose.animation:animation-core")

    // 液态玻璃（最先进的 Compose 液态玻璃库：Kyant0 Backdrop）。
    // backdrop 对 shapes 是 implementation 级依赖（不传递到编译类路径），
    // 代码直接 import com.kyant.shapes.* —— 必须显式声明。
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")

    // AndroidX
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    // 网络 / 序列化
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // 单仓库补丁更新：VCDIFF（xdelta3）补丁的 LZMA(.xz) 二级压缩段解码
    implementation("org.tukaani:xz:1.12")
}
