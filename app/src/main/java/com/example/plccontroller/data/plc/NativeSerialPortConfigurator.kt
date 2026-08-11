package com.example.plccontroller.data.plc

import android.util.Log
import com.example.plccontroller.domain.PlcCommunicationConfig
import java.io.FileDescriptor

internal object NativeSerialPortConfigurator {
    private const val TAG = "SerialModbus"

    private val loadError: Throwable? =
        runCatching {
            System.loadLibrary("plcserial")
        }.exceptionOrNull()

    fun configureBinary(
        fileDescriptor: FileDescriptor,
        config: PlcCommunicationConfig,
    ): Result<String> {
        loadError?.let { error ->
            return Result.failure(IllegalStateException("Native serial library not loaded: ${error.message}", error))
        }

        return runCatching {
            nativeConfigureBinary(
                fileDescriptor = fileDescriptor,
                baudRate = config.baudRate,
                dataBits = config.dataBits,
                stopBits = config.stopBits,
                parityName = config.parityName,
            )
        }.onSuccess { state ->
            Log.d(TAG, "Native serial configured: $state")
        }.onFailure { error ->
            Log.w(TAG, "Native serial configure failed: ${error.message}", error)
        }
    }

    private external fun nativeConfigureBinary(
        fileDescriptor: FileDescriptor,
        baudRate: Int,
        dataBits: Int,
        stopBits: Int,
        parityName: String,
    ): String
}
