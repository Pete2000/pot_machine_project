package com.example.plccontroller.data.http

import android.util.Log
import com.example.plccontroller.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class OtaVersionInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val updateLog: String,
    val forceUpdate: Boolean,
)

@Suppress("TooGenericExceptionCaught")
class OtaUpdateManager {
    companion object {
        private const val TAG = "OtaUpdateManager"
    }

    suspend fun checkUpdate(): OtaVersionInfo? =
        withContext(Dispatchers.IO) {
            try {
                val urlConnection = URL("${AppConfig.managementBaseUrl}/pot/ota/check").openConnection() as HttpURLConnection
                urlConnection.connectTimeout = 5000
                urlConnection.readTimeout = 5000
                urlConnection.requestMethod = "GET"

                val responseCode = urlConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = urlConnection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseText)
                    if (json.has("versionCode")) {
                        return@withContext OtaVersionInfo(
                            versionCode = json.getInt("versionCode"),
                            versionName = json.getString("versionName"),
                            downloadUrl = json.getString("downloadUrl"),
                            updateLog = json.optString("updateLog", ""),
                            forceUpdate = json.optBoolean("forceUpdate", false),
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check update: ${e.message}", e)
            }
            null
        }

    suspend fun downloadApk(
        downloadUrl: String,
        destinationFile: File,
        onProgress: (Int) -> Unit,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val urlConnection = URL(downloadUrl).openConnection() as HttpURLConnection
                urlConnection.connectTimeout = 10000
                urlConnection.readTimeout = 30000
                urlConnection.connect()

                val fileLength = urlConnection.contentLength
                BufferedInputStream(urlConnection.inputStream).use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val data = ByteArray(4096)
                        var total: Long = 0
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            if (fileLength > 0) {
                                onProgress(((total * 100) / fileLength).toInt())
                            }
                            output.write(data, 0, count)
                        }
                    }
                }
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download APK: ${e.message}", e)
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
            }
            false
        }
}
