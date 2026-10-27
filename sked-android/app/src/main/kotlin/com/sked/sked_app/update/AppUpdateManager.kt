package com.sked.sked_app.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.graphics.BitmapFactory
import com.sked.sked_app.MainActivity
import com.sked.sked_app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val apkUrl: String,
    val directDownloadUrl: String = "",
    val fileSize: String = "",
    val forceUpdate: Boolean = false
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val currentBytes: Long, val totalBytes: Long) : DownloadState()
    data class ReadyToInstall(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val PREFS_NAME = "SkedUpdatePrefs"
    private const val KEY_LAST_CHECK_TIME = "last_check_time"
    private const val KEY_CUSTOM_URL = "custom_version_url"
    private const val KEY_LAST_NOTIFIED_CODE = "last_notified_update_code"
    const val UPDATE_CHANNEL_ID = "sked_updates"

    fun createUpdateNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you when a new version of Sked is released"
                enableVibration(true)
                setShowBadge(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    fun notifyUpdateAvailable(context: Context, update: UpdateInfo, force: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotified = prefs.getInt(KEY_LAST_NOTIFIED_CODE, 0)
        if (!force && lastNotified == update.versionCode) return

        createUpdateNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_SHOW_UPDATE", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary = if (update.releaseNotes.isNotBlank()) update.releaseNotes.take(120) else "New features and performance improvements."
        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.sked_logo)
        } catch (_: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_sked)
            .setContentTitle("🚀 Sked Update Available (v${update.versionName})")
            .setContentText("Tap to install: $summary")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Version ${update.versionName} is ready to install!\n\n${update.releaseNotes}\n\nTap to download and install."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        try {
            NotificationManagerCompat.from(context).notify(2001, builder.build())
            prefs.edit().putInt(KEY_LAST_NOTIFIED_CODE, update.versionCode).apply()
        } catch (_: Exception) {}
    }

    // Candidate version endpoints (checks primary then fallbacks)
    private val VERSION_URLS = listOf(
        "https://sked-gold.vercel.app/version.json",
        "https://raw.githubusercontent.com/tanishsarkar28/Sked/main/sked-web/public/version.json",
        "http://10.0.2.2:5173/version.json"
    )

    fun getCurrentVersionInfo(context: Context): Pair<Int, String> {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
            Pair(code, pInfo.versionName ?: "1.0")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading package info", e)
            Pair(1, "1.0")
        }
    }

    /**
     * Check remote version.json for updates asynchronously.
     * Returns UpdateInfo if remote versionCode is higher than installed app, null otherwise.
     */
    suspend fun checkForUpdate(context: Context, force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)

        // Passive check throttle: once per 15 minutes unless user forces check
        if (!force && (now - lastCheck) < 15 * 60 * 1000L) {
            return@withContext null
        }
        prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()

        val customUrl = prefs.getString(KEY_CUSTOM_URL, null)
        val targetUrls = if (!customUrl.isNullOrBlank()) listOf(customUrl) + VERSION_URLS else VERSION_URLS

        for (endpoint in targetUrls) {
            try {
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Sked-Android-Client")

                if (conn.responseCode == 200) {
                    val rawJson = conn.inputStream.bufferedReader().use { it.readText() }
                    val obj = JSONObject(rawJson)

                    val remoteVersionCode = obj.optInt("versionCode", 1)
                    val remoteVersionName = obj.optString("versionName", "1.0")
                    val releaseNotes = obj.optString("releaseNotes", "New update available with performance improvements.")
                    val apkUrl = obj.optString("apkUrl", "")
                    val directUrl = obj.optString("directDownloadUrl", "")
                    val fileSize = obj.optString("fileSize", "")
                    val forceUpdate = obj.optBoolean("forceUpdate", false)

                    val (localCode, _) = getCurrentVersionInfo(context)
                    if (remoteVersionCode > localCode) {
                        val updateInfo = UpdateInfo(
                            versionCode = remoteVersionCode,
                            versionName = remoteVersionName,
                            releaseNotes = releaseNotes,
                            apkUrl = apkUrl.ifBlank { directUrl },
                            directDownloadUrl = directUrl,
                            fileSize = fileSize,
                            forceUpdate = forceUpdate
                        )
                        notifyUpdateAvailable(context, updateInfo)
                        return@withContext updateInfo
                    } else {
                        // Already on latest
                        return@withContext null
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed on $endpoint: ${e.message}")
            }
        }

        null
    }

    /**
     * Download the APK stream to cache and update caller with progress (0.0 to 1.0).
     */
    suspend fun downloadApk(
        context: Context,
        updateInfo: UpdateInfo,
        onProgress: (Float, Long, Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val destDir = context.externalCacheDir ?: context.cacheDir
        val destFile = File(destDir, "sked-update-${updateInfo.versionName}.apk")

        if (destFile.exists()) {
            destFile.delete()
        }

        val downloadUrl = updateInfo.apkUrl
        if (downloadUrl.isBlank()) {
            Log.e(TAG, "No APK download URL available")
            return@withContext null
        }

        try {
            val url = URL(downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Sked-Android-Client")
            conn.connect()

            if (conn.responseCode !in 200..299) {
                Log.e(TAG, "Server returned HTTP ${conn.responseCode}")
                return@withContext null
            }

            val totalBytes = conn.contentLength.toLong()
            var currentBytes = 0L

            conn.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        currentBytes += read
                        val progress = if (totalBytes > 0) currentBytes.toFloat() / totalBytes else 0f
                        withContext(Dispatchers.Main) {
                            onProgress(progress, currentBytes, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            if (destFile.exists() && destFile.length() > 0) {
                destFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download APK: ${e.message}", e)
            if (destFile.exists()) destFile.delete()
            null
        }
    }

    /**
     * Launch system package installer via FileProvider.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            Toast.makeText(context, "Downloaded update package is missing or corrupted.", Toast.LENGTH_SHORT).show()
            return false
        }

        // On Android 8.0+ (Oreo), check if app has permission to install unknown apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                try {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    Toast.makeText(
                        context,
                        "Please allow 'Install unknown apps' for Sked, then tap UPDATE again.",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot open unknown app sources settings", e)
                }
                return false
            }
        }

        return try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start install intent", e)
            Toast.makeText(context, "Unable to launch installer: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            false
        }
    }
}
