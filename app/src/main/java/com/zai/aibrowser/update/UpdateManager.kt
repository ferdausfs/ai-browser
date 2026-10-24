package com.zai.aibrowser.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.zai.aibrowser.util.SemVer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Talks to the GitHub Releases API and handles download + install hand-off.
 *
 * Security notes:
 * - Only HTTPS, only the app's own repository (see [UpdateConfig]).
 * - No credentials of any kind are embedded in the app; public releases need no auth.
 * - The APK is handed to the platform package installer via FileProvider; the user
 *   must explicitly approve ("install unknown apps" consent + install dialog).
 */
class UpdateManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLatestRelease(): GithubRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(UpdateConfig.LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub API error: HTTP ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty response from GitHub")
            parseRelease(JSONObject(body))
        }
    }

    private fun parseRelease(obj: JSONObject): GithubRelease {
        val assetsJson = obj.optJSONArray("assets")
        val assets = ArrayList<GithubAsset>()
        if (assetsJson != null) {
            for (i in 0 until assetsJson.length()) {
                val a = assetsJson.getJSONObject(i)
                assets.add(
                    GithubAsset(
                        name = a.getString("name"),
                        size = a.getLong("size"),
                        downloadUrl = a.getString("browser_download_url")
                    )
                )
            }
        }
        return GithubRelease(
            tagName = obj.getString("tag_name"),
            name = obj.optString("name", obj.getString("tag_name")),
            body = obj.optString("body", ""),
            htmlUrl = obj.optString("html_url", UpdateConfig.RELEASES_PAGE_URL),
            assets = assets
        )
    }

    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean =
        SemVer.isNewer(remoteTag, currentVersion)

    fun downloadedApkFile(release: GithubRelease): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        return File(dir, "ai-browser-${release.tagName}.apk")
    }

    /** Emits download progress (0..100). */
    fun downloadApk(release: GithubRelease): Flow<Int> = flow {
        val asset = release.apkAsset
            ?: throw IllegalStateException("The latest release has no APK asset")
        val request = Request.Builder().url(asset.downloadUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed: HTTP ${response.code}")
            }
            val body = response.body
                ?: throw IllegalStateException("Empty download body")
            val total = body.contentLength()
            val outFile = downloadedApkFile(release)
            body.byteStream().use { input ->
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var totalRead = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (total > 0) {
                            val percent = ((totalRead * 100) / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                emit(percent)
                            }
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
