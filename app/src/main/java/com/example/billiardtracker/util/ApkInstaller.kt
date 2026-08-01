package com.example.billiardtracker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

sealed class InstallResult {
    data object Success : InstallResult()
    data object NeedInstallPermission : InstallResult()
    data class Error(val message: String) : InstallResult()
}

/**
 * Downloads an APK from a URL into app cache, then hands it to the system
 * installer via FileProvider. Progress is reported through [onProgress].
 *
 * Modern Android requires the user to allow "Install unknown apps" for our
 * package before an in-app install can succeed. On [Build.VERSION_CODES.O]+
 * we surface that as [InstallResult.NeedInstallPermission] so the UI can
 * redirect to system Settings.
 */
object ApkInstaller {
    private val client by lazy { OkHttpClient() }

    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        versionName: String,
        onProgress: (Float) -> Unit,
    ): InstallResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return@withContext InstallResult.NeedInstallPermission
        }

        try {
            val cacheDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(cacheDir, "billiardtracker-$versionName.apk")
            apkFile.delete()

            val req = Request.Builder().url(apkUrl).build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    return@withContext InstallResult.Error("HTTP ${res.code}")
                }
                val body = res.body ?: return@withContext InstallResult.Error("empty body")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastReported = -1
                        var read = input.read(buffer)
                        while (read != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                // Throttle progress emissions to per-percent to keep
                                // recomposition cost bounded on large APKs.
                                val pct = (downloaded * 100 / total).toInt()
                                if (pct != lastReported) {
                                    lastReported = pct
                                    onProgress(pct / 100f)
                                }
                            }
                            read = input.read(buffer)
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile,
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }
            InstallResult.Success
        } catch (e: Exception) {
            InstallResult.Error(e.message ?: "download failed")
        }
    }

    /** Opens system Settings so the user can grant "Install unknown apps". */
    fun openInstallPermissionSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }
}
