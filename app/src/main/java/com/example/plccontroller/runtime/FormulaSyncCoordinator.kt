package com.example.plccontroller.runtime

import com.example.plccontroller.AppConfig
import com.example.plccontroller.data.SettingsStore
import com.example.plccontroller.data.http.HttpOrderRepository
import com.example.plccontroller.domain.FormulaCatalog
import com.example.plccontroller.domain.FormulaParameterUpdate
import com.example.plccontroller.domain.localDefaultFormulaCatalog
import com.example.plccontroller.domain.withUpdatedParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FormulaSyncCoordinator(
    private val orderRepository: HttpOrderRepository,
    private val runtimeStore: MachineRuntimeStore,
    private val settingsStore: SettingsStore,
    private val log: (String) -> Unit,
) {
    private val formulaLock = Mutex()

    suspend fun refreshFormulaCatalog() {
        formulaLock.withLock {
            runCatching {
                if (settingsStore.snapshot().formulaSyncIntervalSeconds <= 0L) {
                    useCachedFormulaOrLocalDefault("配方自动同步已关闭")
                    return
                }

                val deviceCode =
                    AppConfig.formulaDeviceCode
                        .trim()
                        .ifBlank { settingsStore.snapshot().deviceCode.trim() }
                if (deviceCode.isEmpty()) {
                    useCachedFormulaOrLocalDefault("Formula device code missing")
                    return
                }

                val catalogs =
                    withContext(Dispatchers.IO) {
                        orderRepository.loadFormulaCatalogs(deviceCode)
                    }

                settingsStore.cacheFormulaCatalogsList(catalogs)

                val selectedCode = settingsStore.getSelectedFormulaCode()
                val selectedCatalog =
                    catalogs.firstOrNull { it.formulaCode == selectedCode }
                        ?: catalogs.firstOrNull(FormulaCatalog::whetherDefault)
                        ?: catalogs.firstOrNull()

                if (selectedCatalog == null) {
                    useCachedFormulaOrLocalDefault("Formula API returned no catalog")
                    return
                }

                val sourceLabel =
                    selectedCatalog.formulaName
                        .ifBlank {
                            selectedCatalog.formulaCode
                        }.ifBlank {
                            "接口同步配方"
                        }
                settingsStore.cacheFormulaCatalog(
                    catalog = selectedCatalog,
                    sourceLabel = sourceLabel,
                )
                runtimeStore.update {
                    it.copy(
                        availableCatalogs = catalogs,
                        formulaCatalog = selectedCatalog,
                        formulaSourceLabel = sourceLabel,
                    )
                }
                log("Formula sync completed: ${selectedCatalog.formulaName}")
            }.onFailure { error ->
                useCachedFormulaOrLocalDefault("Formula sync failed: ${error.message}")
            }
        }
    }

    suspend fun saveFormulaParameter(update: FormulaParameterUpdate) {
        formulaLock.withLock {
            runCatching {
                val config = settingsStore.snapshot()
                val isLocalFormulaMode = config.formulaSyncIntervalSeconds <= 0L
                val currentCatalog =
                    runtimeStore.snapshot().formulaCatalog
                        ?: settingsStore.cachedFormulaCatalog()
                        ?: localDefaultFormulaCatalog()

                if (!isLocalFormulaMode) {
                    val deviceCode =
                        AppConfig.formulaDeviceCode
                            .trim()
                            .ifBlank { config.deviceCode.trim() }
                    require(deviceCode.isNotEmpty()) { "设备编码为空，无法保存配方" }
                    withContext(Dispatchers.IO) {
                        orderRepository.updateFormulaParameter(
                            deviceCode = deviceCode,
                            update = update,
                        )
                    }
                }

                val updatedCatalog = currentCatalog.withUpdatedParameter(update)
                val baseSourceLabel =
                    currentCatalog.formulaName.ifBlank {
                        currentCatalog.formulaCode
                    }
                val sourceLabel =
                    if (isLocalFormulaMode) {
                        baseSourceLabel.ifBlank { "本地已保存配方" } + " / 本地修改"
                    } else {
                        baseSourceLabel.ifBlank { "平台已保存配方" }
                    }
                settingsStore.cacheFormulaCatalog(
                    catalog = updatedCatalog,
                    sourceLabel = sourceLabel,
                )
                runtimeStore.update { state ->
                    state.copy(
                        formulaCatalog = updatedCatalog,
                        formulaSourceLabel = sourceLabel,
                        lastMessage =
                            if (isLocalFormulaMode) {
                                "配方已保存到本地：${update.potCode}/${update.potTypeCode}"
                            } else {
                                "配方已保存到平台：${update.potCode}/${update.potTypeCode}"
                            },
                    )
                }
                log(
                    "Formula parameter saved (${if (isLocalFormulaMode) "local" else "platform"}): " +
                        "pot=${update.potCode}, type=${update.potTypeCode}, " +
                        "water=${update.addWaterSeconds}, chicken=${update.addChickenOilSeconds}, " +
                        "bone=${update.addBonePasteSeconds}",
                )
            }.onFailure { error ->
                val message = error.message ?: "配方保存失败"
                log("Formula save failed: $message")
                runtimeStore.update { state ->
                    state.copy(lastMessage = "配方保存失败：$message")
                }
            }
        }
    }

    suspend fun selectFormulaCatalog(formulaCode: String) {
        formulaLock.withLock {
            val catalogs =
                runtimeStore.snapshot().availableCatalogs.ifEmpty {
                    settingsStore.cachedFormulaCatalogsList()
                }
            val targetCatalog = catalogs.firstOrNull { it.formulaCode == formulaCode }
            if (targetCatalog != null) {
                settingsStore.setSelectedFormulaCode(formulaCode)
                val sourceLabel =
                    targetCatalog.formulaName
                        .ifBlank {
                            targetCatalog.formulaCode
                        }.ifBlank {
                            "切换同步配方"
                        }
                settingsStore.cacheFormulaCatalog(
                    catalog = targetCatalog,
                    sourceLabel = sourceLabel,
                )
                runtimeStore.update {
                    it.copy(
                        formulaCatalog = targetCatalog,
                        formulaSourceLabel = sourceLabel,
                    )
                }
                log("Formula catalog switched to: ${targetCatalog.formulaName}")
            } else {
                log("Catalog with code $formulaCode not found in cached/available lists")
            }
        }
    }

    fun useCachedFormulaOrLocalDefault(reason: String) {
        val snapshot = runtimeStore.snapshot()
        val currentCatalog = snapshot.formulaCatalog
        val cachedCatalog = currentCatalog ?: settingsStore.cachedFormulaCatalog()
        val cachedCatalogs = settingsStore.cachedFormulaCatalogsList()
        if (cachedCatalog != null) {
            val sourceLabel =
                if (currentCatalog != null) {
                    snapshot.formulaSourceLabel
                } else {
                    settingsStore
                        .cachedFormulaSourceLabel()
                        ?.let { "$it / 本地缓存" }
                        ?: "本地缓存配方"
                }
            runtimeStore.update {
                it.copy(
                    availableCatalogs = cachedCatalogs.ifEmpty { listOf(cachedCatalog) },
                    formulaCatalog = cachedCatalog,
                    formulaSourceLabel = sourceLabel,
                    lastMessage = "$reason，继续使用当前有效配方",
                )
            }
            log("$reason. Using cached formula.")
        } else {
            val localDefaultCatalog = localDefaultFormulaCatalog()
            runtimeStore.update {
                it.copy(
                    availableCatalogs = listOf(localDefaultCatalog),
                    formulaCatalog = localDefaultCatalog,
                    formulaSourceLabel = "本地默认",
                    lastMessage = "$reason，使用本地默认配方",
                )
            }
            log("$reason. Falling back to local defaults.")
        }
    }
}
