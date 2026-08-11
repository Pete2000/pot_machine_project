package com.example.plccontroller.ui

import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.data.http.EquipmentDto
import com.example.plccontroller.domain.FormulaParameterUpdate

/** Groups settings callbacks by the page that owns them. */
internal data class SettingsScreenActions(
    val display: SettingsDisplayActions,
    val formula: SettingsFormulaActions,
    val access: SettingsAccessActions,
    val device: SettingsDeviceActions,
    val diagnostics: SettingsDiagnosticsActions,
    val orders: SettingsOrderActions,
)

internal data class SettingsDisplayActions(
    val openSecondaryDisplay: () -> Unit,
    val previewSecondaryDisplay: () -> Unit,
)

internal data class SettingsFormulaActions(
    val saveParameter: (FormulaParameterUpdate) -> Unit,
    val selectCatalog: (String) -> Unit,
)

internal data class SettingsAccessActions(
    val updateSettings: ((SettingsStore) -> Unit) -> Unit,
    val startRegistration: () -> Unit,
    val selectDeviceType: (String) -> Unit,
    val selectEquipment: (EquipmentDto) -> Unit,
    val resetRegistrationState: () -> Unit,
)

internal data class SettingsDeviceActions(
    val beginHeaterActuatorConfigEdit: () -> Unit,
    val cancelHeaterActuatorConfigEdit: () -> Unit,
    val saveHeaterActuatorConfig: ((SettingsStore) -> Unit) -> Unit,
)

internal data class SettingsDiagnosticsActions(
    val refreshPlcStatus: () -> Unit,
    val sendHeartbeatPulse: () -> Unit,
    val sendHeaterTestCommand: () -> Unit,
    val sendEmergencyWaterTestCommand: () -> Unit,
    val sendPhaseTestCommand: () -> Unit,
)

internal data class SettingsOrderActions(
    val clearAllLocalOrders: () -> Unit,
)

internal fun emptySettingsScreenActions(): SettingsScreenActions =
    SettingsScreenActions(
        display = SettingsDisplayActions(openSecondaryDisplay = {}, previewSecondaryDisplay = {}),
        formula = SettingsFormulaActions(saveParameter = {}, selectCatalog = {}),
        access =
            SettingsAccessActions(
                updateSettings = {},
                startRegistration = {},
                selectDeviceType = {},
                selectEquipment = {},
                resetRegistrationState = {},
            ),
        device =
            SettingsDeviceActions(
                beginHeaterActuatorConfigEdit = {},
                cancelHeaterActuatorConfigEdit = {},
                saveHeaterActuatorConfig = {},
            ),
        diagnostics =
            SettingsDiagnosticsActions(
                refreshPlcStatus = {},
                sendHeartbeatPulse = {},
                sendHeaterTestCommand = {},
                sendEmergencyWaterTestCommand = {},
                sendPhaseTestCommand = {},
            ),
        orders = SettingsOrderActions(clearAllLocalOrders = {}),
    )
