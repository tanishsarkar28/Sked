package com.sked.sked_app.telemetry

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object InstallTracker {

    private const val TAG = "InstallTracker"
    private const val PREFS_NAME = "SkedInstallTelemetry"
    private const val KEY_REGISTERED = "has_registered_install"
    private const val KEY_INSTALL_TIME = "install_timestamp"

    // High-availability integer counter namespace for Sked installs
    private const val HIT_URL = "https://abacus.jasoncameron.dev/hit/sked_official_app/installs"
    private const val GET_URL = "https://abacus.jasoncameron.dev/get/sked_official_app/installs"

    /**
     * Called on app startup. If this is the first launch on this device,
     * sends an anonymous +1 ping to the global install counter.
     */
    fun registerInstallIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyRegistered = prefs.getBoolean(KEY_REGISTERED, false)

        if (!alreadyRegistered) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val url = URL(HIT_URL)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "Sked-Install-Tracker")
                    }

                    if (conn.responseCode in 200..299) {
                        prefs.edit()
                            .putBoolean(KEY_REGISTERED, true)
                            .putLong(KEY_INSTALL_TIME, System.currentTimeMillis())
                            .apply()
                        Log.i(TAG, "Install registered successfully.")
                    } else {
                        Log.w(TAG, "Install registration returned code: ${conn.responseCode}")
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not register install ping: ${e.message}")
                }
            }
        }
    }

    /**
     * Fetches current total install count from the server.
     * Returns the integer value, or -1 if unreachable.
     */
    suspend fun getLiveInstallCount(): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = URL(GET_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Sked-Admin-Console")
            }

            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = JSONObject(body)
                json.optInt("value", 0)
            } else {
                conn.disconnect()
                -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve install count", e)
            -1
        }
    }
}
