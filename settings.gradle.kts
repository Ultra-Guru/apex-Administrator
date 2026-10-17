pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
// 仓库已更名为 apex-Administrator（单仓库补丁更新：源码 + 发布产物同仓）
rootProject.name = "apex-Administrator"
include(":app")
