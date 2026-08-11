package com.example.plccontroller.data

import android.content.Context
import android.content.SharedPreferences
import com.example.plccontroller.AppConfig
import com.example.plccontroller.data.http.toFormulaCatalogOrNull
import com.example.plccontroller.data.http.toFormulaCatalogsListOrNull
import com.example.plccontroller.data.http.toJsonString
import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.domain.PlcCommunicationConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val writeLock = Any()
    private var batchWriteDepth = 0

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<AppPersistentConfig> = _configFlow.asStateFlow()

    fun snapshot(): AppPersistentConfig = _configFlow.value

    fun updateBatch(action: SettingsStore.() -> Unit) {
        synchronized(writeLock) {
            batchWriteDepth += 1
            try {
                action()
            } finally {
                batchWriteDepth -= 1
                if (batchWriteDepth == 0) {
                    refresh()
                }
            }
        }
    }

    fun cachedFormulaCatalog(): FormulaCatalog? =
        prefs
            .getString(KEY_CACHED_FORMULA_CATALOG, null)
            ?.toFormulaCatalogOrNull()

    fun cachedFormulaSourceLabel(): String? = prefs.getString(KEY_CACHED_FORMULA_SOURCE_LABEL, null)

    fun cacheFormulaCatalog(
        catalog: FormulaCatalog,
        sourceLabel: String,
    ) {
        writePrefs(refreshConfig = false) {
            putString(KEY_CACHED_FORMULA_CATALOG, catalog.toJsonString())
            putString(KEY_CACHED_FORMULA_SOURCE_LABEL, sourceLabel)
            putLong(KEY_CACHED_FORMULA_UPDATED_AT, System.currentTimeMillis())
        }
    }

    fun cachedFormulaCatalogsList(): List<FormulaCatalog> {
        val json = prefs.getString(KEY_CACHED_FORMULA_CATALOGS_LIST, null)
        return json?.toFormulaCatalogsListOrNull() ?: emptyList()
    }

    fun cacheFormulaCatalogsList(catalogs: List<FormulaCatalog>) {
        writePrefs(refreshConfig = false) {
            putString(KEY_CACHED_FORMULA_CATALOGS_LIST, catalogs.toJsonString())
        }
    }

    fun getSelectedFormulaCode(): String? = prefs.getString(KEY_SELECTED_FORMULA_CODE, null)

    fun setSelectedFormulaCode(code: String?) {
        writePrefs(refreshConfig = false) {
            putString(KEY_SELECTED_FORMULA_CODE, code)
        }
    }

    fun updateBusinessUrl(url: String) = writeString(KEY_BUSINESS_URL, url)

    fun updateManagementUrl(url: String) = writeString(KEY_MANAGEMENT_URL, url)

    fun updateDeviceCode(code: String) = writeString(KEY_DEVICE_CODE, code)

    fun updatePlcSerialPortPath(path: String) = writeString(KEY_PLC_SERIAL_PORT_PATH, path)

    fun updatePlcBaudRate(baudRate: Int) = writeInt(KEY_PLC_BAUD_RATE, baudRate)

    fun updatePlcDataBits(dataBits: Int) = writeInt(KEY_PLC_DATA_BITS, dataBits)

    fun updatePlcParity(parity: String) = writeString(KEY_PLC_PARITY, parity.uppercase())

    fun updatePlcStopBits(stopBits: Int) = writeInt(KEY_PLC_STOP_BITS, stopBits)

    fun updatePlcSlaveId(id: Int) = writeInt(KEY_PLC_SLAVE_ID, id)

    fun updateReadTimeoutMs(ms: Long) = writeLong(KEY_READ_TIMEOUT_MS, ms)

    fun updateWriteTimeoutMs(ms: Long) = writeLong(KEY_WRITE_TIMEOUT_MS, ms)

    fun updateRetryCount(count: Int) = writeInt(KEY_RETRY_COUNT, count)

    fun updateFrameGapMs(ms: Long) = writeLong(KEY_FRAME_GAP_MS, ms.coerceAtLeast(10L))

    fun updateRegisterOnlyMode(enabled: Boolean) = writeBoolean(KEY_REGISTER_ONLY_MODE, enabled)

    fun updateRegisterOnlyBlockStart(start: Int) {
        writeInt(KEY_REGISTER_ONLY_BLOCK_START, start.takeIf { it in REGISTER_ONLY_BLOCK_STARTS } ?: 0)
    }

    fun updateRegisterOnlyBlockCount(count: Int) = writeInt(KEY_REGISTER_ONLY_BLOCK_COUNT, count.takeIf { it in 0..125 } ?: 0)

    fun updateHeartbeatPeriodMs(ms: Long) = writeLong(KEY_HEARTBEAT_PERIOD_MS, ms)

    fun updateActivePollingIntervalMs(ms: Long) = writeLong(KEY_ACTIVE_POLLING_MS, ms)

    fun updateIdlePollingIntervalMs(ms: Long) = writeLong(KEY_IDLE_POLLING_MS, ms)

    fun updateOrderPollingInterval(ms: Long) = writeLong(KEY_ORDER_POLLING_MS, ms)

    fun updateFormulaSyncIntervalSeconds(seconds: Long) = writeLong(KEY_FORMULA_SYNC_INTERVAL_SECONDS, seconds.coerceAtLeast(0L))

    fun updateConnectionTestRegister(register: Int) = writeInt(KEY_CONNECTION_TEST_REGISTER, register)

    fun updatePollingInterval(ms: Long) = updateOrderPollingInterval(ms)

    fun updateDeviceHeaterTargetTemp(temp: Int) = writeInt(KEY_DEVICE_HEATER_TARGET_TEMP, temp.coerceIn(0, 120))

    fun updateDeviceHeaterHysteresisTemp(temp: Int) = writeInt(KEY_DEVICE_HEATER_HYSTERESIS_TEMP, temp.coerceIn(0, 30))

    fun updateDeviceHeaterSelect(select: Int) {
        writeInt(KEY_DEVICE_HEATER_SELECT, select.takeIf { it == 1 || it == 2 } ?: AppConfig.deviceHeaterSelect)
    }

    fun updateDeviceHeaterSensorSelect(select: Int) {
        writeInt(KEY_DEVICE_HEATER_SENSOR_SELECT, select.takeIf { it in 0..2 } ?: AppConfig.deviceHeaterSensorSelect)
    }

    fun updateDeviceStandaloneWaterMode(mode: Int) {
        writeInt(KEY_DEVICE_STANDALONE_WATER_MODE, mode.takeIf { it in 0..2 } ?: AppConfig.deviceStandaloneWaterMode)
    }

    fun updateDeviceStandaloneWaterTimedTicks(ticks: Int) = writeInt(KEY_DEVICE_STANDALONE_WATER_TIMED_TICKS, ticks.coerceIn(1, 3_600))

    fun updateDeviceWaterOutlet1CalibrationPercent(percent: Int) {
        writeInt(KEY_DEVICE_WATER_OUTLET_1_CALIBRATION_PERCENT, percent.toCalibrationPercent())
    }

    fun updateDeviceWaterOutlet2CalibrationPercent(percent: Int) {
        writeInt(KEY_DEVICE_WATER_OUTLET_2_CALIBRATION_PERCENT, percent.toCalibrationPercent())
    }

    fun updateDeviceWaterOutlet3CalibrationPercent(percent: Int) {
        writeInt(KEY_DEVICE_WATER_OUTLET_3_CALIBRATION_PERCENT, percent.toCalibrationPercent())
    }

    fun updateDeviceWaterOutlet4CalibrationPercent(percent: Int) {
        writeInt(KEY_DEVICE_WATER_OUTLET_4_CALIBRATION_PERCENT, percent.toCalibrationPercent())
    }

    fun updateDeviceChickenOilCalibrationPercent(percent: Int) {
        writeInt(KEY_DEVICE_CHICKEN_OIL_CALIBRATION_PERCENT, percent.toCalibrationPercent())
    }

    fun updateDeviceBonePasteCalibrationPercent(percent: Int) {
        writeInt(KEY_DEVICE_BONE_PASTE_CALIBRATION_PERCENT, percent.toCalibrationPercent())
    }

    fun updateDeviceSparePumpMode(mode: Int) {
        writeInt(KEY_DEVICE_SPARE_PUMP_MODE, mode.takeIf { it in 0..2 } ?: AppConfig.DEVICE_SPARE_PUMP_MODE)
    }

    @android.annotation.SuppressLint("UseKtx")
    fun nextPlcCommandId(): Int {
        synchronized(writeLock) {
            val lastCommandId = prefs.getInt(KEY_PLC_LAST_COMMAND_ID, PLC_COMMAND_ID_SEED)
            val nextCommandId =
                if (lastCommandId >= PLC_COMMAND_ID_MAX || lastCommandId < 0) {
                    1
                } else {
                    lastCommandId + 1
                }
            check(prefs.edit().putInt(KEY_PLC_LAST_COMMAND_ID, nextCommandId).commit()) {
                "PLC command id persistence failed"
            }
            return nextCommandId
        }
    }

    fun currentPlcCommandId(): Int =
        synchronized(writeLock) {
            prefs.getInt(KEY_PLC_LAST_COMMAND_ID, PLC_COMMAND_ID_SEED)
        }

    private fun writeString(
        key: String,
        value: String,
    ) {
        writePrefs { putString(key, value) }
    }

    private fun writeInt(
        key: String,
        value: Int,
    ) {
        writePrefs { putInt(key, value) }
    }

    private fun writeLong(
        key: String,
        value: Long,
    ) {
        writePrefs { putLong(key, value) }
    }

    private fun writeBoolean(
        key: String,
        value: Boolean,
    ) {
        writePrefs { putBoolean(key, value) }
    }

    private fun writePrefs(
        refreshConfig: Boolean = true,
        block: SharedPreferences.Editor.() -> Unit,
    ) {
        synchronized(writeLock) {
            prefs.edit().apply {
                block()
                apply()
            }
            if (refreshConfig && batchWriteDepth == 0) {
                refresh()
            }
        }
    }

    private fun refresh() {
        _configFlow.value = loadConfig()
    }

    private fun loadConfig(): AppPersistentConfig =
        AppPersistentConfig(
            businessUrl =
                prefs.getString(KEY_BUSINESS_URL, AppConfig.businessBaseUrl)
                    ?: AppConfig.businessBaseUrl,
            managementUrl =
                prefs.getString(KEY_MANAGEMENT_URL, AppConfig.managementBaseUrl)
                    ?: AppConfig.managementBaseUrl,
            deviceCode =
                prefs.getString(KEY_DEVICE_CODE, AppConfig.formulaDeviceCode)
                    ?: AppConfig.formulaDeviceCode,
            plcSerialPortPath =
                prefs.getString(KEY_PLC_SERIAL_PORT_PATH, AppConfig.plcSerialPortPath)
                    ?: AppConfig.plcSerialPortPath,
            plcBaudRate = prefs.getInt(KEY_PLC_BAUD_RATE, AppConfig.plcBaudRate),
            plcDataBits = prefs.getInt(KEY_PLC_DATA_BITS, AppConfig.plcDataBits),
            plcParity =
                prefs.getString(KEY_PLC_PARITY, AppConfig.plcParity)
                    ?: AppConfig.plcParity,
            plcStopBits = prefs.getInt(KEY_PLC_STOP_BITS, AppConfig.plcStopBits),
            plcSlaveId = prefs.getInt(KEY_PLC_SLAVE_ID, AppConfig.plcSlaveId),
            readTimeoutMs = prefs.getLong(KEY_READ_TIMEOUT_MS, AppConfig.plcReadTimeoutMs),
            writeTimeoutMs = prefs.getLong(KEY_WRITE_TIMEOUT_MS, AppConfig.plcWriteTimeoutMs),
            retryCount = prefs.getInt(KEY_RETRY_COUNT, AppConfig.plcRetryCount),
            frameGapMs = prefs.getLong(KEY_FRAME_GAP_MS, AppConfig.plcFrameGapMs),
            registerOnlyMode = prefs.getBoolean(KEY_REGISTER_ONLY_MODE, AppConfig.plcRegisterOnlyMode),
            registerOnlyBlockStart =
                prefs.getInt(
                    KEY_REGISTER_ONLY_BLOCK_START,
                    AppConfig.plcRegisterOnlyBlockStart,
                ),
            registerOnlyBlockCount =
                prefs.getInt(
                    KEY_REGISTER_ONLY_BLOCK_COUNT,
                    AppConfig.plcRegisterOnlyBlockCount,
                ),
            heartbeatPeriodMs = prefs.getLong(KEY_HEARTBEAT_PERIOD_MS, AppConfig.plcHeartbeatPeriodMs),
            activePollingIntervalMs =
                prefs.getLong(
                    KEY_ACTIVE_POLLING_MS,
                    AppConfig.plcActivePollingIntervalMs,
                ),
            idlePollingIntervalMs = prefs.getLong(KEY_IDLE_POLLING_MS, AppConfig.plcIdlePollingIntervalMs),
            orderPollingIntervalMs = prefs.getLong(KEY_ORDER_POLLING_MS, AppConfig.orderPollingIntervalMs),
            formulaSyncIntervalSeconds =
                prefs.getLong(
                    KEY_FORMULA_SYNC_INTERVAL_SECONDS,
                    AppConfig.FORMULA_SYNC_INTERVAL_SECONDS,
                ),
            connectionTestRegister =
                prefs.getInt(
                    KEY_CONNECTION_TEST_REGISTER,
                    AppConfig.plcConnectionTestRegister,
                ),
            deviceHeaterTargetTemp =
                prefs.getInt(
                    KEY_DEVICE_HEATER_TARGET_TEMP,
                    AppConfig.deviceHeaterTargetTemp,
                ),
            deviceHeaterHysteresisTemp =
                prefs.getInt(
                    KEY_DEVICE_HEATER_HYSTERESIS_TEMP,
                    AppConfig.deviceHeaterHysteresisTemp,
                ),
            deviceHeaterSelect = prefs.getInt(KEY_DEVICE_HEATER_SELECT, AppConfig.deviceHeaterSelect),
            deviceHeaterSensorSelect =
                prefs.getInt(
                    KEY_DEVICE_HEATER_SENSOR_SELECT,
                    AppConfig.deviceHeaterSensorSelect,
                ),
            deviceStandaloneWaterMode =
                prefs.getInt(
                    KEY_DEVICE_STANDALONE_WATER_MODE,
                    AppConfig.deviceStandaloneWaterMode,
                ),
            deviceStandaloneWaterTimedTicks =
                prefs.getInt(
                    KEY_DEVICE_STANDALONE_WATER_TIMED_TICKS,
                    AppConfig.deviceStandaloneWaterTimedTicks,
                ),
            deviceWaterOutlet1CalibrationPercent =
                prefs.getInt(
                    KEY_DEVICE_WATER_OUTLET_1_CALIBRATION_PERCENT,
                    AppConfig.deviceWaterOutletCalibrationPercent,
                ),
            deviceWaterOutlet2CalibrationPercent =
                prefs.getInt(
                    KEY_DEVICE_WATER_OUTLET_2_CALIBRATION_PERCENT,
                    AppConfig.deviceWaterOutletCalibrationPercent,
                ),
            deviceWaterOutlet3CalibrationPercent =
                prefs.getInt(
                    KEY_DEVICE_WATER_OUTLET_3_CALIBRATION_PERCENT,
                    AppConfig.deviceWaterOutletCalibrationPercent,
                ),
            deviceWaterOutlet4CalibrationPercent =
                prefs.getInt(
                    KEY_DEVICE_WATER_OUTLET_4_CALIBRATION_PERCENT,
                    AppConfig.deviceWaterOutletCalibrationPercent,
                ),
            deviceChickenOilCalibrationPercent =
                prefs.getInt(
                    KEY_DEVICE_CHICKEN_OIL_CALIBRATION_PERCENT,
                    AppConfig.deviceChickenOilCalibrationPercent,
                ),
            deviceBonePasteCalibrationPercent =
                prefs.getInt(
                    KEY_DEVICE_BONE_PASTE_CALIBRATION_PERCENT,
                    AppConfig.DEVICE_BONE_PASTE_CALIBRATION_PERCENT,
                ),
            deviceSparePumpMode =
                prefs.getInt(
                    KEY_DEVICE_SPARE_PUMP_MODE,
                    AppConfig.DEVICE_SPARE_PUMP_MODE,
                ),
        )

    private fun Int.toCalibrationPercent(): Int = coerceIn(50, 200)

    companion object {
        private const val KEY_BUSINESS_URL = "business_url"
        private const val KEY_MANAGEMENT_URL = "management_url"
        private const val KEY_DEVICE_CODE = "device_code"
        private const val KEY_PLC_SERIAL_PORT_PATH = "plc_serial_port_path"
        private const val KEY_PLC_BAUD_RATE = "plc_baud_rate"
        private const val KEY_PLC_DATA_BITS = "plc_data_bits"
        private const val KEY_PLC_PARITY = "plc_parity"
        private const val KEY_PLC_STOP_BITS = "plc_stop_bits"
        private const val KEY_PLC_SLAVE_ID = "plc_slave_id"
        private const val KEY_READ_TIMEOUT_MS = "read_timeout_ms"
        private const val KEY_WRITE_TIMEOUT_MS = "write_timeout_ms"
        private const val KEY_RETRY_COUNT = "retry_count"
        private const val KEY_FRAME_GAP_MS = "frame_gap_ms"
        private const val KEY_REGISTER_ONLY_MODE = "register_only_mode"
        private const val KEY_REGISTER_ONLY_BLOCK_START = "register_only_block_start"
        private const val KEY_REGISTER_ONLY_BLOCK_COUNT = "register_only_block_count"
        private const val KEY_HEARTBEAT_PERIOD_MS = "heartbeat_period_ms"
        private const val KEY_ACTIVE_POLLING_MS = "active_polling_interval_ms"
        private const val KEY_IDLE_POLLING_MS = "idle_polling_interval_ms"
        private const val KEY_ORDER_POLLING_MS = "order_polling_interval_ms"
        private const val KEY_FORMULA_SYNC_INTERVAL_SECONDS = "formula_sync_interval_seconds"
        private const val KEY_CONNECTION_TEST_REGISTER = "connection_test_register"
        private const val KEY_DEVICE_HEATER_TARGET_TEMP = "device_heater_target_temp"
        private const val KEY_DEVICE_HEATER_HYSTERESIS_TEMP = "device_heater_hysteresis_temp"
        private const val KEY_DEVICE_HEATER_SELECT = "device_heater_select"
        private const val KEY_DEVICE_HEATER_SENSOR_SELECT = "device_heater_sensor_select"
        private const val KEY_DEVICE_STANDALONE_WATER_MODE = "device_standalone_water_mode"
        private const val KEY_DEVICE_STANDALONE_WATER_TIMED_TICKS = "device_standalone_water_timed_ticks"
        private const val KEY_DEVICE_WATER_OUTLET_1_CALIBRATION_PERCENT =
            "device_water_outlet_1_calibration_percent"
        private const val KEY_DEVICE_WATER_OUTLET_2_CALIBRATION_PERCENT =
            "device_water_outlet_2_calibration_percent"
        private const val KEY_DEVICE_WATER_OUTLET_3_CALIBRATION_PERCENT =
            "device_water_outlet_3_calibration_percent"
        private const val KEY_DEVICE_WATER_OUTLET_4_CALIBRATION_PERCENT =
            "device_water_outlet_4_calibration_percent"
        private const val KEY_DEVICE_CHICKEN_OIL_CALIBRATION_PERCENT =
            "device_chicken_oil_calibration_percent"
        private const val KEY_DEVICE_BONE_PASTE_CALIBRATION_PERCENT =
            "device_bone_paste_calibration_percent"
        private const val KEY_DEVICE_SPARE_PUMP_MODE = "device_spare_pump_mode"
        private const val KEY_PLC_LAST_COMMAND_ID = "plc_last_command_id"
        private const val KEY_CACHED_FORMULA_CATALOG = "cached_formula_catalog"
        private const val KEY_CACHED_FORMULA_SOURCE_LABEL = "cached_formula_source_label"
        private const val KEY_CACHED_FORMULA_UPDATED_AT = "cached_formula_updated_at"
        private const val KEY_SELECTED_FORMULA_CODE = "selected_formula_code"
        private const val KEY_CACHED_FORMULA_CATALOGS_LIST = "cached_formula_catalogs_list"
        private const val PLC_COMMAND_ID_SEED = 1_000
        private const val PLC_COMMAND_ID_MAX = 65_535
        private val REGISTER_ONLY_BLOCK_STARTS = setOf(0, 200, 320, 350, 370)
    }
}

data class AppPersistentConfig(
    val businessUrl: String,
    val managementUrl: String,
    val deviceCode: String,
    val plcSerialPortPath: String,
    val plcBaudRate: Int,
    val plcDataBits: Int,
    val plcParity: String,
    val plcStopBits: Int,
    val plcSlaveId: Int,
    val readTimeoutMs: Long,
    val writeTimeoutMs: Long,
    val retryCount: Int,
    val frameGapMs: Long,
    val registerOnlyMode: Boolean,
    val registerOnlyBlockStart: Int,
    val registerOnlyBlockCount: Int,
    val heartbeatPeriodMs: Long,
    val activePollingIntervalMs: Long,
    val idlePollingIntervalMs: Long,
    val orderPollingIntervalMs: Long,
    val formulaSyncIntervalSeconds: Long,
    val connectionTestRegister: Int,
    val deviceHeaterTargetTemp: Int,
    val deviceHeaterHysteresisTemp: Int,
    val deviceHeaterSelect: Int,
    val deviceHeaterSensorSelect: Int,
    val deviceStandaloneWaterMode: Int,
    val deviceStandaloneWaterTimedTicks: Int,
    val deviceWaterOutlet1CalibrationPercent: Int,
    val deviceWaterOutlet2CalibrationPercent: Int,
    val deviceWaterOutlet3CalibrationPercent: Int,
    val deviceWaterOutlet4CalibrationPercent: Int,
    val deviceChickenOilCalibrationPercent: Int,
    val deviceBonePasteCalibrationPercent: Int,
    val deviceSparePumpMode: Int,
)

fun AppPersistentConfig.toCommunicationConfig(): PlcCommunicationConfig =
    PlcCommunicationConfig(
        serialPortPath = plcSerialPortPath,
        baudRate = plcBaudRate,
        dataBits = plcDataBits,
        parityName = plcParity,
        stopBits = plcStopBits,
        slaveId = plcSlaveId,
        readTimeoutMs = readTimeoutMs,
        writeTimeoutMs = writeTimeoutMs,
        retryCount = retryCount,
        frameGapMs = frameGapMs,
        registerOnlyMode = registerOnlyMode,
        registerOnlyBlockStart = registerOnlyBlockStart,
        registerOnlyBlockCount = registerOnlyBlockCount,
        heartbeatPeriodMs = heartbeatPeriodMs,
        activePollingIntervalMs = activePollingIntervalMs,
        idlePollingIntervalMs = idlePollingIntervalMs,
        orderPollingIntervalMs = orderPollingIntervalMs,
        connectionTestRegister = connectionTestRegister,
    )
