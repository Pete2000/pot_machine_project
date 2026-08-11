package com.example.plccontroller.data.http

import android.util.Log
import com.example.plccontroller.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@Suppress("TooGenericExceptionCaught")
class LogUploadManager {
    companion object {
        private const val TAG = "LogUploadManager"
    }

    suspend fun uploadLogs(logFile: File): Boolean =
        withContext(Dispatchers.IO) {
            if (!logFile.exists() || logFile.length() == 0L) {
                return@withContext false
            }
            try {
                val logContent = logFile.readText()
                val urlConnection = URL("${AppConfig.managementBaseUrl}/pot/log/upload").openConnection() as HttpURLConnection
                urlConnection.connectTimeout = 10000
                urlConnection.readTimeout = 10000
                urlConnection.requestMethod = "POST"
                urlConnection.doOutput = true
                urlConnection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")

                urlConnection.outputStream.use { os ->
                    OutputStreamWriter(os, "UTF-8").use { writer ->
                        writer.write(logContent)
                        writer.flush()
                    }
                }

                val responseCode = urlConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.i(TAG, "Log uploaded successfully: ${logFile.name}")
                    return@withContext true
                } else {
                    Log.w(TAG, "Log upload failed with response code: $responseCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload log: ${e.message}", e)
            }
            false
        }
}
