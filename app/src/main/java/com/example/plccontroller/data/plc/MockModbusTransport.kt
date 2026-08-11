package com.example.plccontroller.data.plc

class MockModbusTransport : ModbusTransport {
    private val coils = BooleanArray(MOCK_COIL_CAPACITY)
    private val holdingRegisters = IntArray(MOCK_REGISTER_CAPACITY)
    private val stateLock = Any()

    init {
        holdingRegisters[200] = 52
        holdingRegisters[201] = 51
        holdingRegisters[210] = 3
    }

    override suspend fun transact(request: ByteArray): ByteArray {
        require(request.size >= 8) { "Invalid Modbus request" }
        require(hasValidCrc(request)) { "Invalid Modbus request CRC" }

        val slaveId = request[0]
        val function = request[1].toInt() and 0xFF
        val address = readUShort(request, 2)

        return synchronized(stateLock) {
            when (function) {
                FUNCTION_READ_COILS -> handleReadCoils(slaveId, function, address, readUShort(request, 4))
                FUNCTION_READ_HOLDING_REGISTERS ->
                    handleReadHoldingRegisters(
                        slaveId = slaveId,
                        function = function,
                        address = address,
                        quantity = readUShort(request, 4),
                    )
                FUNCTION_WRITE_SINGLE_COIL ->
                    handleWriteSingleCoil(
                        slaveId = slaveId,
                        function = function,
                        address = address,
                        value = readUShort(request, 4),
                    )
                FUNCTION_WRITE_SINGLE_REGISTER ->
                    handleWriteSingleRegister(
                        slaveId = slaveId,
                        function = function,
                        address = address,
                        value = readUShort(request, 4),
                    )
                FUNCTION_WRITE_MULTIPLE_COILS ->
                    handleWriteMultipleCoils(
                        slaveId = slaveId,
                        function = function,
                        address = address,
                        quantity = readUShort(request, 4),
                        byteCount = request[6].toInt() and 0xFF,
                        values = request.copyOfRange(7, request.lastIndex - 1),
                    )
                FUNCTION_WRITE_MULTIPLE_REGISTERS ->
                    handleWriteMultipleRegisters(
                        slaveId = slaveId,
                        function = function,
                        address = address,
                        quantity = readUShort(request, 4),
                        byteCount = request[6].toInt() and 0xFF,
                        values = request.copyOfRange(7, request.lastIndex - 1),
                    )
                else -> throw IllegalArgumentException("Unsupported mock Modbus function: $function")
            }
        }
    }

    private fun handleReadCoils(
        slaveId: Byte,
        function: Int,
        address: Int,
        quantity: Int,
    ): ByteArray {
        require(quantity in 1..2000) { "Invalid coil quantity: $quantity" }
        val byteCount = (quantity + 7) / 8
        val payload = ByteArray(3 + byteCount)
        payload[0] = slaveId
        payload[1] = function.toByte()
        payload[2] = byteCount.toByte()
        repeat(quantity) { index ->
            val absoluteIndex = address + index
            val value = absoluteIndex in coils.indices && coils[absoluteIndex]
            if (value) {
                payload[3 + (index / 8)] =
                    (payload[3 + (index / 8)].toInt() or (1 shl (index % 8))).toByte()
            }
        }
        return payload + crc16(payload)
    }

    private fun handleReadHoldingRegisters(
        slaveId: Byte,
        function: Int,
        address: Int,
        quantity: Int,
    ): ByteArray {
        require(quantity in 1..125) { "Invalid register quantity: $quantity" }
        val payload = ByteArray(3 + quantity * 2)
        payload[0] = slaveId
        payload[1] = function.toByte()
        payload[2] = (quantity * 2).toByte()
        repeat(quantity) { index ->
            val value = holdingRegisters.getOrElse(address + index) { 0 }
            payload[3 + index * 2] = ((value shr 8) and 0xFF).toByte()
            payload[4 + index * 2] = (value and 0xFF).toByte()
        }
        return payload + crc16(payload)
    }

    private fun handleWriteSingleCoil(
        slaveId: Byte,
        function: Int,
        address: Int,
        value: Int,
    ): ByteArray {
        if (address in coils.indices) {
            coils[address] = value == MODBUS_TRUE_WORD
        }
        val payload =
            byteArrayOf(
                slaveId,
                function.toByte(),
                ((address shr 8) and 0xFF).toByte(),
                (address and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte(),
            )
        return payload + crc16(payload)
    }

    private fun handleWriteSingleRegister(
        slaveId: Byte,
        function: Int,
        address: Int,
        value: Int,
    ): ByteArray {
        if (address in holdingRegisters.indices) {
            holdingRegisters[address] = value and 0xFFFF
        }
        val payload =
            byteArrayOf(
                slaveId,
                function.toByte(),
                ((address shr 8) and 0xFF).toByte(),
                (address and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte(),
            )
        return payload + crc16(payload)
    }

    private fun handleWriteMultipleCoils(
        slaveId: Byte,
        function: Int,
        address: Int,
        quantity: Int,
        byteCount: Int,
        values: ByteArray,
    ): ByteArray {
        require(byteCount == values.size) { "Invalid multi-coil byte count" }
        repeat(quantity) { index ->
            val bitValue = ((values[index / 8].toInt() shr (index % 8)) and 0x01) == 1
            val absoluteIndex = address + index
            if (absoluteIndex in coils.indices) {
                coils[absoluteIndex] = bitValue
            }
        }
        val payload =
            byteArrayOf(
                slaveId,
                function.toByte(),
                ((address shr 8) and 0xFF).toByte(),
                (address and 0xFF).toByte(),
                ((quantity shr 8) and 0xFF).toByte(),
                (quantity and 0xFF).toByte(),
            )
        return payload + crc16(payload)
    }

    private fun handleWriteMultipleRegisters(
        slaveId: Byte,
        function: Int,
        address: Int,
        quantity: Int,
        byteCount: Int,
        values: ByteArray,
    ): ByteArray {
        require(byteCount == values.size) { "Invalid multi-register byte count" }
        repeat(quantity) { index ->
            val high = values[index * 2].toInt() and 0xFF
            val low = values[index * 2 + 1].toInt() and 0xFF
            val absoluteIndex = address + index
            if (absoluteIndex in holdingRegisters.indices) {
                holdingRegisters[absoluteIndex] = (high shl 8) or low
            }
        }
        mirrorCommandStatus(address = address, quantity = quantity)
        val payload =
            byteArrayOf(
                slaveId,
                function.toByte(),
                ((address shr 8) and 0xFF).toByte(),
                (address and 0xFF).toByte(),
                ((quantity shr 8) and 0xFF).toByte(),
                (quantity and 0xFF).toByte(),
            )
        return payload + crc16(payload)
    }

    private fun mirrorCommandStatus(
        address: Int,
        quantity: Int,
    ) {
        if (quantity <= 0 || address !in holdingRegisters.indices) {
            return
        }
        when (address) {
            300 -> mirrorStatusBlock(statusStart = 320, commandId = holdingRegisters[address])
            340 -> mirrorStatusBlock(statusStart = 350, commandId = holdingRegisters[address])
            360 -> mirrorStatusBlock(statusStart = 370, commandId = holdingRegisters[address])
        }
    }

    private fun mirrorStatusBlock(
        statusStart: Int,
        commandId: Int,
    ) {
        if (statusStart !in 0 until holdingRegisters.size - 4) {
            return
        }
        holdingRegisters[statusStart] = commandId
        holdingRegisters[statusStart + 1] = commandId
        holdingRegisters[statusStart + 2] = commandId
        holdingRegisters[statusStart + 3] = 3
        holdingRegisters[statusStart + 4] = 0
    }
}
