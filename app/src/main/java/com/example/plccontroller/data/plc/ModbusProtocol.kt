package com.example.plccontroller.data.plc

fun hasValidCrc(frame: ByteArray): Boolean {
    if (frame.size < 4) {
        return false
    }
    val payload = frame.copyOfRange(0, frame.size - 2)
    val crc = crc16(payload)
    return crc[0] == frame[frame.size - 2] && crc[1] == frame[frame.size - 1]
}

fun crc16(data: ByteArray): ByteArray {
    var crc = 0xFFFF
    for (byte in data) {
        crc = crc xor (byte.toInt() and 0xFF)
        repeat(8) {
            crc =
                if ((crc and 0x0001) != 0) {
                    (crc shr 1) xor 0xA001
                } else {
                    crc shr 1
                }
        }
    }
    return byteArrayOf(
        (crc and 0xFF).toByte(),
        ((crc shr 8) and 0xFF).toByte(),
    )
}

internal fun readUShort(
    frame: ByteArray,
    offset: Int,
): Int {
    val high = frame[offset].toInt() and 0xFF
    val low = frame[offset + 1].toInt() and 0xFF
    return (high shl 8) or low
}

internal fun String.isMockPath(): Boolean {
    val normalized = trim().lowercase()
    return normalized == "mock" || normalized.startsWith("mock://")
}

internal fun String.isSendOnlyPath(): Boolean {
    val normalized = trim().lowercase()
    return normalized.contains("#send-only") || normalized.contains("?sendonly=1")
}

internal fun String.devicePath(): String =
    trim()
        .substringBefore("#")
        .substringBefore("?")

internal fun String.sttyTokens(): Set<String> =
    replace(';', ' ')
        .lineSequence()
        .flatMap { it.splitToSequence(Regex("\\s+")) }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

internal fun ByteArray.toHexString(): String =
    joinToString(" ") { byte ->
        "%02X".format(byte.toInt() and 0xFF)
    }

internal const val FUNCTION_READ_COILS = 0x01
internal const val FUNCTION_READ_HOLDING_REGISTERS = 0x03
internal const val FUNCTION_WRITE_SINGLE_COIL = 0x05
internal const val FUNCTION_WRITE_SINGLE_REGISTER = 0x06
internal const val FUNCTION_WRITE_MULTIPLE_COILS = 0x0F
internal const val FUNCTION_WRITE_MULTIPLE_REGISTERS = 0x10
internal const val MAX_WRITE_MULTIPLE_REGISTER_COUNT = 123
internal const val MOCK_COIL_CAPACITY = 2048
internal const val MOCK_REGISTER_CAPACITY = 1024
internal const val MODBUS_TRUE_WORD = 0xFF00
internal const val MODBUS_FALSE_WORD = 0x0000
internal const val STTY_COMMAND_TIMEOUT_MS = 1_500L
internal const val TAG_SERIAL = "SerialModbus"
internal val CRITICAL_RAW_FLAGS = listOf("iuclc", "istrip", "ixon", "ixoff")
