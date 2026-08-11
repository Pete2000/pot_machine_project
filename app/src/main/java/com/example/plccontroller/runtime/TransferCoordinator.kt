package com.example.plccontroller.runtime

import com.example.plccontroller.AppConfig
import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.domain.Order
import com.example.plccontroller.domain.OrderStatus
import com.example.plccontroller.domain.PotMode

internal class TransferCoordinator(
    private val runtimeStore: MachineRuntimeStore,
    private val settingsStore: SettingsStore,
    private val orderLifecycle: OrderLifecycleController,
    private val log: (String) -> Unit,
) {
    suspend fun confirmTransferLocked(
        orderId: String,
        source: TransferConfirmSource,
    ) {
        val snapshot = runtimeStore.snapshot()
        val demoTarget =
            snapshot.secondaryDisplayDemoState.waitingTransferOrders
                .firstOrNull { it.id == orderId }
        val liveTarget =
            snapshot.waitingTransferOrders
                .firstOrNull { it.id == orderId }
        val target = demoTarget ?: liveTarget

        if (target == null) {
            log("Transfer confirmation ignored for missing order $orderId via ${source.name}")
            return
        }

        runtimeStore.update { state ->
            val demoWaitingOrders = state.secondaryDisplayDemoState.waitingTransferOrders
            val message = "Transfer confirmed for ${target.id} via ${source.name}"
            if (demoWaitingOrders.any { it.id == orderId }) {
                orderLifecycle.applyDemoTransferCompleted(
                    state = state,
                    order = target,
                    message = message,
                )
            } else {
                orderLifecycle.applyLiveTransferCompleted(
                    state = state,
                    order = target,
                    message = message,
                )
            }
        }
        log("Transfer confirmed for $orderId via ${source.name}")

        if (liveTarget != null) {
            runCatching {
                orderLifecycle.markCompleted(
                    deviceCode =
                        settingsStore
                            .snapshot()
                            .deviceCode
                            .trim()
                            .ifBlank { AppConfig.formulaDeviceCode.trim() },
                    orderId = orderId,
                )
            }.onFailure { error ->
                log("Order feedback failed for $orderId: ${error.message}")
            }
        }
    }

    fun physicalConfirmOrderId(): String? =
        runtimeStore
            .snapshot()
            .transferDeck.confirmOrder
            ?.id

    fun setTransferDisplayMode(mode: TransferDisplayMode) {
        runtimeStore.update {
            it.copy(
                transferDisplayMode = mode,
                lastMessage = "Transfer display mode set to ${mode.name}",
            )
        }
        log("Transfer display mode changed to ${mode.name}")
    }

    fun loadSecondaryDisplayDemoData(orderCount: Int = 7) {
        val demoOrders = SecondaryDisplayDemoFactory.buildWaitingTransferOrders(orderCount)
        runtimeStore.update {
            it.copy(
                secondaryDisplayDemoState =
                    SecondaryDisplayDemoState(
                        enabled = true,
                        waitingTransferOrders = demoOrders,
                        completedOrders = emptyList(),
                    ),
                lastMessage = "Secondary display demo loaded with ${demoOrders.size} waiting orders",
            )
        }
        log("Secondary display demo loaded with ${demoOrders.size} waiting orders")
    }

    fun clearSecondaryDisplayDemoData() {
        runtimeStore.update {
            it.copy(
                secondaryDisplayDemoState = SecondaryDisplayDemoState(),
                lastMessage = "Secondary display demo cleared",
            )
        }
        log("Secondary display demo cleared")
    }
}

internal object SecondaryDisplayDemoFactory {
    fun buildWaitingTransferOrders(orderCount: Int): List<Order> {
        val tableCodes = listOf("A01", "A05", "B08", "B12", "C03", "D06", "E09", "F11", "G02")
        val potBottomNames = listOf("清汤锅", "番茄锅", "麻辣锅", "菌汤锅", "冬阴功锅", "三鲜锅", "酸菜锅", "牛油锅", "骨汤锅")
        val tasteSummaries = listOf("微辣", "番茄浓汤", "中辣", "菌香", "酸辣", "鲜香", "酸爽", "特辣", "原味")
        val potModes =
            listOf(
                PotMode.Single,
                PotMode.Split,
                PotMode.FourGrid,
                PotMode.ThreeGrid,
                PotMode.Single,
                PotMode.Split,
                PotMode.FourGrid,
                PotMode.ThreeGrid,
                PotMode.Single,
            )
        val timeTexts =
            listOf(
                "10:00:00",
                "10:02:00",
                "10:04:00",
                "10:06:00",
                "10:08:00",
                "10:10:00",
                "10:12:00",
                "10:14:00",
                "10:16:00",
            )
        val slotSummaries =
            listOf(
                "整锅：清汤锅（微辣）",
                "左锅：番茄锅（番茄浓汤）|右锅：菌汤锅（菌香）",
                "左上：麻辣锅（中辣）|右上：番茄锅（番茄浓汤）|左下：菌汤锅（菌香）|右下：清汤锅（原味）",
                "左上：冬阴功锅（酸辣）|右上：番茄锅（番茄浓汤）|下锅：三鲜锅（鲜香）",
                "整锅：酸菜锅（酸爽）",
                "左锅：牛油锅（特辣）|右锅：番茄锅（番茄浓汤）",
                "左上：骨汤锅（原味）|右上：菌汤锅（菌香）|左下：番茄锅（番茄浓汤）|右下：麻辣锅（中辣）",
                "左上：三鲜锅（鲜香）|右上：番茄锅（番茄浓汤）|下锅：清汤锅（微辣）",
                "整锅：清汤锅（原味）",
            )

        return (0 until orderCount).map { index ->
            Order(
                id = "secondary-demo-${index + 1}",
                recipeCode = "DEMO-${index + 1}",
                quantity = 1,
                targetTemperature = 98,
                cookSeconds = 0,
                spiceLevel = (index % 4) + 1,
                tableCode = tableCodes[index % tableCodes.size],
                orderTime = timeTexts[index % timeTexts.size],
                potMode = potModes[index % potModes.size],
                potBottomName = potBottomNames[index % potBottomNames.size],
                tasteSummary = tasteSummaries[index % tasteSummaries.size],
                slotSummary = slotSummaries[index % slotSummaries.size],
                status = OrderStatus.WaitingTransfer,
            )
        }
    }
}
