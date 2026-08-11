package com.example.plccontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.PlcConnectionState
import com.example.plccontroller.domain.PlcPollingSnapshot
import com.example.plccontroller.runtime.NetworkConnectionState

@Preview(widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun HomeScreenPreview() {
    val mockSnapshot =
        PlcPollingSnapshot(
            holdingRegisters = listOf(15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2),
            mirrorBits = List(40) { it == 40 || it == 41 },
        )
    val mockState =
        MainUiState(
            plcState = PlcConnectionState.Connected,
            networkState = NetworkConnectionState.Online,
            plcPollingSnapshot = mockSnapshot,
            pendingOrders =
                listOf(
                    Order(
                        id = "1001",
                        recipeCode = "R1",
                        quantity = 1,
                        targetTemperature = 90,
                        cookSeconds = 60,
                        spiceLevel = 1,
                        tableCode = "A01",
                        potBottomName = "番茄锅",
                    ),
                ),
        )
    MaterialTheme {
        Box(modifier = Modifier.background(PageBackground)) {
            HomeScreen(
                state = mockState,
                activeOrder = mockState.pendingOrders.first(),
                homeMode = HomeMode.Receive,
                isWateringActive = false,
                onHomeModeChange = {},
                onRequestWater = {},
                onRequestManualWater = { _, _ -> },
                onContinueManualPhase = {},
                onSetOrderDelayed = { _, _ -> },
                onPinOrder = {},
                onOpenPendingWaterOrders = {},
            )
        }
    }
}
