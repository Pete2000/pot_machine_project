package com.example.plccontroller.data.plc

import com.example.plccontroller.domain.PlcCommunicationConfig
import com.example.plccontroller.domain.PlcSerialDiagnostic

interface ModbusTransport : AutoCloseable {
    suspend fun transact(request: ByteArray): ByteArray

    fun serialDiagnostic(): PlcSerialDiagnostic = PlcSerialDiagnostic()

    fun updateConfig(config: PlcCommunicationConfig) {}

    fun resetConnection() {}

    override fun close() {}
}
