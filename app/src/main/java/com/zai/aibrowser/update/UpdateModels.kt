package com.zai.aibrowser.update

data class GithubAsset(
    val name: String,
    val size: Long,
    val downloadUrl: String
)

data class GithubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val assets: List<GithubAsset>
) {
    val apkAsset: GithubAsset?
        get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class UpToDate(val currentVersion: String) : UpdateState()
    data class Available(val release: GithubRelease, val currentVersion: String) : UpdateState()
    data class Downloading(val release: GithubRelease, val progressPercent: Int) : UpdateState()
    data class Downloaded(val release: GithubRelease, val filePath: String) : UpdateState()
    data class Error(val message: String) : UpdateState()
}
