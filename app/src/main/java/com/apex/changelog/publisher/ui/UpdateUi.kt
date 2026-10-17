package com.apex.changelog.publisher.ui

import com.apex.changelog.publisher.data.SelfUpdate
import java.io.File

/**
 * 发布器自更新（单仓库补丁更新）的 UI 状态机 ——
 * 由 MainActivity 持有并自动静默检查，主页横幅与设置页更新卡共用。
 */
sealed interface UpdateUiState {

    /** 尚未检查（启动自动检查中 / 失败静默）。 */
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data object UpToDate : UpdateUiState

    /** 有新版本。viaPatch = 本机版本正好是补丁基底（走增量，否则全量）。 */
    data class Available(
        val manifest: SelfUpdate.Manifest,
        val viaPatch: Boolean
    ) : UpdateUiState

    /** 下载中（补丁或整包）。 */
    data class Downloading(
        val viaPatch: Boolean,
        val bytes: Long,
        val total: Long
    ) : UpdateUiState

    /** 校验 / 补丁合成中。 */
    data class Working(val label: String) : UpdateUiState

    /** 产物就绪，等待用户点「立即安装」。 */
    data class Ready(val apk: File) : UpdateUiState

    data class Failed(val message: String) : UpdateUiState
}

/**
 * 静默检查（启动调用）：有更新 → Available；无更新 → UpToDate；
 * 失败保持 [current]（不打扰 —— 网络失败静默降级）。
 */
suspend fun silentCheck(
    current: UpdateUiState,
    installed: SelfUpdate.Installed
): UpdateUiState {
    val manifest = SelfUpdate.fetchManifest().getOrNull() ?: return current
    if (manifest.versionCode <= 0) return current
    return if (manifest.versionCode.toLong() > installed.versionCode) {
        val viaPatch = manifest.patch
            ?.let { it.fromVersionCode.toLong() == installed.versionCode }
            ?: false
        UpdateUiState.Available(manifest, viaPatch)
    } else {
        UpdateUiState.UpToDate
    }
}
