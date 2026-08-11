# 代码审查问题清单

> **用途**：供 Codex 自我审查和修复  
> **创建日期**：2026-05-29  
> **项目阶段**：骨架项目，大部分问题属于代码质量改进，非致命缺陷  
> **说明**：按优先级 P0-P3 排列，P0 为部署前必须修复，P3 为持续优化

---

## 一、Bug 风险（运行时可能出错）

### 1.1 recipeCode 静默截断为 0
- **严重程度**：高
- **类别**：Bug
- **文件**：`app/src/main/java/com/example/plccontroller/data/plc/ModbusRtu.kt`
- **行号**：L458
- **问题描述**：`order.recipeCode.toIntOrNull() ?: 0`，当 recipeCode 无法转为整数时静默写入 0，既不抛异常也不通知调用方。PLC 可能因收到无效配方编码 0 而执行错误操作。
- **修复建议**：在 recipeCode 无效时抛出明确异常或返回错误结果，至少添加日志警告。

### 1.2 HTTP 连接无 disconnect() 保护
- **严重程度**：高
- **类别**：Bug
- **文件**：`app/src/main/java/com/example/plccontroller/data/http/OrderApiClient.kt`
- **行号**：L98-L136
- **问题描述**：`request()` 方法没有使用 `try-finally` 确保 `connection.disconnect()` 被调用。网络超时或异常时 socket 连接可能不会被正确释放，长期运行后可能导致 socket 文件描述符耗尽。
- **修复建议**：
```kotlin
private fun request(...): String {
    val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
    try {
        // ... existing logic ...
    } finally {
        connection.disconnect()
    }
}
```

### 1.3 Modbus 响应长度校验不足
- **严重程度**：高
- **类别**：Bug
- **文件**：`app/src/main/java/com/example/plccontroller/data/plc/ModbusRtu.kt`
- **行号**：L506-L516（readHoldingRegisters）、L484（readCoils）
- **问题描述**：校验只检查了 `response.size >= 5`，但实际需要的最小长度是 `3 + count * 2 + 2`。如果 PLC 返回截断的响应，`response[3 + index * 2]` 会抛出 `IndexOutOfBoundsException`。
- **修复建议**：
```kotlin
val expectedMinSize = 3 + count * 2 + 2
require(response.size >= expectedMinSize) {
    "Modbus register response too short: expected $expectedMinSize, got ${response.size}"
}
```

### 1.4 dispatchOrder 失败时未通知后端
- **严重程度**：高
- **类别**：Bug
- **文件**：`app/src/main/java/com/example/plccontroller/runtime/MachineCoordinator.kt`
- **行号**：L308-L315
- **问题描述**：当 `plcController.dispatch(order)` 抛出异常时，代码只更新了本地状态和日志，但没有调用 `orderRepository.markFinished()` 告知后端订单失败。后端在 L309 已经调用了 `markStarted`，如果 dispatch 失败，后端可能永远不知道该订单已失败。
- **修复建议**：在 `onFailure` 分支中也调用 `orderRepository.markFinished(order.id, false, error.message)`。

### 1.5 QueuedModbusTransport 使用 UNLIMITED Channel 无背压
- **严重程度**：中
- **类别**：Bug
- **文件**：`app/src/main/java/com/example/plccontroller/data/plc/ModbusRtu.kt`
- **行号**：L257
- **问题描述**：`Channel.UNLIMITED` 意味着队列可以无限增长。如果 Modbus 通信持续超时，每个 `transact()` 调用都会向 Channel 发送消息，但 worker 因超时处理缓慢，导致内存持续增长。
- **修复建议**：使用有界 Channel（如 `Channel(capacity = 64)`），配合 `send` 的挂起特性实现背压。

### 1.6 QueuedModbusTransport.close() 后请求永久挂起
- **严重程度**：中
- **类别**：Bug
- **文件**：`app/src/main/java/com/example/plccontroller/data/plc/ModbusRtu.kt`
- **行号**：L292-L295
- **问题描述**：`close()` 中先关闭 Channel 再取消 Job。如果有协程正在等待 `queued.response.await()`，Channel 关闭后 worker 退出，但已发送的请求永远不会得到响应，`CompletableDeferred` 既不会 `complete` 也不会 `completeExceptionally`，导致调用方协程永久挂起。
- **修复建议**：遍历 Channel 中剩余的请求，对其 `completeExceptionally`。

### 1.7 MockModbusTransport 数组无同步保护
- **严重程度**：中
- **类别**：Bug
- **文件**：`app/src/main/java/com/example/plccontroller/data/plc/ModbusRtu.kt`
- **行号**：L23-L24
- **问题描述**：`coils` 和 `holdingRegisters` 是普通数组，`transact()` 是 suspend 函数，可被多个协程并发调用。`BooleanArray` 和 `IntArray` 不是线程安全类型，可能导致数据读取撕裂或写入丢失。
- **修复建议**：使用 `synchronized` 块保护，或改用 `AtomicIntegerArray` 包装。

### 1.8 debugCommandSeed 非线程安全
- **严重程度**：中
- **类别**：Bug
- **文件**：`app/src/main/java/com/example/plccontroller/runtime/MachineCoordinator.kt`
- **行号**：L506-L512
- **问题描述**：`debugCommandSeed` 是普通的 `var Int`，虽然当前在 `plcCommandLock.withLock` 内被调用，但保护是隐式的。另外存在 off-by-one 问题：初始化为 9000，第一次 +=1 后变为 9001，但 9000 本身永远不会被返回。
- **修复建议**：使用 `AtomicInteger`，或添加注释说明逻辑。

### 1.9 心跳和轮询失败处理不分离
- **严重程度**：中
- **类别**：Bug
- **文件**：`app/src/main/java/com/example/plccontroller/runtime/PlcPollingService.kt`
- **行号**：L46-L53
- **问题描述**：心跳写入和快照轮询在同一 `runCatching` 块中。如果心跳写入成功但 `pollSnapshot()` 失败，PLC 状态会更新为 `Fault`，但此时心跳已被写入，PLC 侧认为连接正常，造成状态不一致。
- **修复建议**：将心跳与轮询的失败分开处理。

### 1.10 SettingsStore 并发写入问题
- **严重程度**：低
- **类别**：Bug
- **文件**：`app/src/main/java/com/example/plccontroller/data/SettingsStore.kt`
- **行号**：L56-L69
- **问题描述**：每次 `writeString`/`writeInt` 都创建新的 `SharedPreferences.Editor`，快速连续调用时前一个可能还未提交就被后一个覆盖。`refresh()` 没有线程安全保护。
- **修复建议**：将多个设置更新合并到一个 Editor 中批量提交，或引入 Mutex 保护写入序列。

### 1.11 orderLocks 无上限增长
- **严重程度**：低
- **类别**：Bug
- **文件**：`app/src/main/java/com/example/plccontroller/runtime/MachineCoordinator.kt`
- **行号**：L33, L479-L487
- **问题描述**：`orderLocks` 使用 `ConcurrentHashMap` 存储每个订单 ID 的 Mutex，但从未清理已完成的订单锁。长时间运行后会积累大量无用的 Mutex 对象。
- **修复建议**：在 `withOrderLock` 的 `finally` 块中清理不再需要的 Mutex。

### 1.12 OrderApiClient 直接将响应解析为 JSONArray
- **严重程度**：低
- **类别**：Bug
- **文件**：`app/src/main/java/com/example/plccontroller/data/http/OrderApiClient.kt`
- **行号**：L45
- **问题描述**：`fetchPendingOrders()` 直接将 HTTP 响应体传入 `JSONArray()` 构造函数。如果后端返回的是包装格式（如 `{"data": [...]}`）或错误页面，会抛出 `JSONException`。
- **修复建议**：先解析为 `JSONObject`，再取 `data` 字段。

---

## 二、代码质量问题

### 2.1 buildSecondaryDisplayDemoOrders 重复定义
- **严重程度**：高
- **类别**：代码重复
- **文件**：`app/src/main/java/com/example/plccontroller/runtime/MachineCoordinator.kt`
- **行号**：L535-L635
- **问题描述**：`buildSecondaryDisplayDemoOrdersV2`（L535-590）和 `buildSecondaryDisplayDemoOrders`（L592-635）共享了绝大部分硬编码数据和几乎相同的 Order 构造逻辑。旧版函数未被调用但仍保留。
- **修复建议**：删除未使用的 `buildSecondaryDisplayDemoOrders`，将共用数据提取为常量。

### 2.2 isPendingWaterBucket() 重复定义
- **严重程度**：高
- **类别**：代码重复
- **文件**：
  - `app/src/main/java/com/example/plccontroller/runtime/MachineCoordinator.kt` L638-L642
  - `app/src/main/java/com/example/plccontroller/ui/UiModels.kt` L576-L580
- **问题描述**：两个文件中对 `OrderStatus.isPendingWaterBucket()` 的实现完全相同。
- **修复建议**：统一为一处定义，另一处引用。

### 2.3 调试命令硬编码数值无注释
- **严重程度**：高
- **类别**：魔法数字
- **文件**：`app/src/main/java/com/example/plccontroller/runtime/MachineCoordinator.kt`
- **行号**：L118-L195
- **问题描述**：`commandType = 10`, `heaterSelect = 1`, `targetTemp = 55`, `hysteresis = 3` 等值无任何注释说明来源。
- **修复建议**：提取为命名常量，添加注释说明来自哪份 PLC 协议文档。

### 2.4 MockModbusTransport 硬编码寄存器初始值
- **严重程度**：高
- **类别**：魔法数字
- **文件**：`app/src/main/java/com/example/plccontroller/data/plc/ModbusRtu.kt`
- **行号**：L27-L29
- **问题描述**：`holdingRegisters[200] = 52`, `holdingRegisters[201] = 51`, `holdingRegisters[210] = 3`，这些数字代表什么物理量完全无注释。
- **修复建议**：使用 `PlcReadHoldingRegisterMap` 中定义的常量地址，添加注释说明含义。

### 2.5 AppConfig 硬编码环境配置
- **严重程度**：高
- **类别**：硬编码
- **文件**：`app/src/main/java/com/example/plccontroller/AppConfig.kt`
- **行号**：L4-L8
- **问题描述**：`businessBaseUrl = "http://192.168.1.100:8050"`, `plcSerialPortPath = "/dev/ttyS4"` 等值在编译时写死，换设备或环境需要修改源码重新编译。
- **修复建议**：使用 `BuildConfig` 字段、`gradle.properties` 或运行时配置文件管理。

### 2.6 boneOil 与 bonePaste 命名混用
- **严重程度**：中
- **类别**：命名不一致
- **文件**：`app/src/main/java/com/example/plccontroller/domain/Models.kt`
- **行号**：L63, L99, L109
- **问题描述**：同一种辅料在不同上下文中分别称为 "boneOil" 和 "bonePaste"。`FormulaPotType` 使用 `boneOilSeconds`，但 `PotRecipeProfile` 和 `AdditiveType` 使用 `bonePaste`。
- **修复建议**：统一命名，建议全部使用 `bonePaste`（与 `AdditiveType.BonePaste` 一致）。

### 2.7 addWaterSeconds 与 boneOilSeconds 命名不对称
- **严重程度**：低
- **类别**：命名不一致
- **文件**：`app/src/main/java/com/example/plccontroller/domain/Models.kt`
- **行号**：L95-L99
- **问题描述**：`addWaterSeconds` 和 `addChickenOilSeconds` 有 `add` 前缀，但 `boneOilSeconds` 没有，语义不对称。
- **修复建议**：统一为 `addBonePasteSeconds`。

### 2.8 PlcPrototypeDispatchRegisterMap 使用十进制
- **严重程度**：中
- **类别**：风格不一致
- **文件**：`app/src/main/java/com/example/plccontroller/domain/Models.kt`
- **行号**：L536-L544
- **问题描述**：其他寄存器映射均使用十六进制（`0x012C`），唯独此处使用十进制（100, 101, 110, 120）。
- **修复建议**：统一使用十六进制：`0x0064`, `0x0065`, `0x006E`, `0x0078`。

### 2.9 UI 文件中大量硬编码颜色值
- **严重程度**：中
- **类别**：硬编码
- **文件**：`HomeScreen.kt`, `SettingsScreen.kt`, `SecondaryDisplayScreen.kt`
- **问题描述**：三个 UI 文件中散布着 20+ 处 `Color(0xFF061735)`, `Color(0xFF0D2A66)` 等硬编码颜色，未使用 Material Theme 配色方案。
- **修复建议**：将所有 HMI 面板常用颜色提取到 `Theme.kt` 或专门的 `HmiColors` 对象中。

### 2.10 UI 文件中硬编码尺寸值
- **严重程度**：中
- **类别**：硬编码
- **文件**：`HomeScreen.kt`, `SettingsScreen.kt`, `SecondaryDisplayScreen.kt`
- **问题描述**：`220.dp`, `270.dp`, `420.dp` 等尺寸散落在各处，无统一的尺寸常量体系。
- **修复建议**：定义 `HmiDimensions` 对象，集中管理间距、圆角、面板高度等常量。

### 2.11 UI 文件中重复的样式模式
- **严重程度**：中
- **类别**：代码重复
- **文件**：`HomeScreen.kt`, `SettingsScreen.kt`, `SecondaryDisplayScreen.kt`
- **问题描述**：多处重复出现 `.clip(RoundedCornerShape(8.dp)).background(Color(0xFF061735)).border(1.dp, Cyan.copy(alpha=...), RoundedCornerShape(8.dp))` 组合模式。
- **修复建议**：提取为自定义 `Modifier` 扩展函数。

### 2.12 日志保留条数为魔法数字
- **严重程度**：低
- **类别**：魔法数字
- **文件**：`app/src/main/java/com/example/plccontroller/runtime/MachineCoordinator.kt`
- **行号**：L502
- **问题描述**：`it.logs.take(11)` -- 为什么保留 11 条日志？没有命名常量或注释。
- **修复建议**：提取为 `private const val MAX_LOG_ENTRIES = 11`。

### 2.13 currentDateText() 命名不准确
- **严重程度**：低
- **类别**：命名
- **文件**：`app/src/main/java/com/example/plccontroller/ui/HomeScreen.kt`
- **行号**：L1149-L1151
- **问题描述**：函数返回包含日期和时间的完整字符串，但名称中只有 "date" 没有 "time"。
- **修复建议**：改为 `currentDateTimeText()` 或 `formattedNow()`。

---

## 三、架构设计问题

### 3.1 Models.kt 承载过多不相关职责
- **严重程度**：高
- **类别**：架构
- **文件**：`app/src/main/java/com/example/plccontroller/domain/Models.kt`
- **行号**：整个文件（607 行）
- **问题描述**：同时包含领域模型（Order, OrderStatus）、PLC 寄存器映射（PlcReadButtonMap 等）、业务逻辑（PotDispatchPlanner, PotOrderParser）、PLC 通信配置（PlcSerialSettings）。
- **修复建议**：拆分为多个文件：
  - `domain/OrderModels.kt`
  - `domain/PlcRegisterModels.kt`
  - `domain/dispatch/PotDispatchPlanner.kt`
  - `domain/order/PotOrderParser.kt`

### 3.2 ModbusRtu.kt 混合多种职责
- **严重程度**：高
- **类别**：架构
- **文件**：`app/src/main/java/com/example/plccontroller/data/plc/ModbusRtu.kt`
- **行号**：整个文件（659 行）
- **问题描述**：包含 ModbusTransport 接口、MockModbusTransport、QueuedModbusTransport、PlcController、三个命令数据类、CRC 工具函数。协议层、传输层、控制器层、命令 DTO 全部堆在一个文件。
- **修复建议**：拆分为：
  - `ModbusTransport.kt`（接口 + QueuedModbusTransport）
  - `MockModbusTransport.kt`
  - `PlcController.kt`
  - `PlcCommands.kt`
  - `ModbusCrc.kt`

### 3.3 MachineCoordinator 职责过多
- **严重程度**：中
- **类别**：架构
- **文件**：`app/src/main/java/com/example/plccontroller/runtime/MachineCoordinator.kt`
- **行号**：整个文件（642 行）
- **问题描述**：同时承担订单生命周期管理、PLC 通信控制、网络同步、维护模式管理、副屏演示数据生成、设置管理、日志记录等所有业务流程。
- **修复建议**：拆分为多个 UseCase 类：
  - `OrderDispatchUseCase`
  - `PlcDebugUseCase`
  - `OrderSyncUseCase`
  - `FormulaSyncUseCase`
  - `TransferDeckUseCase`

### 3.4 缺少领域层接口
- **严重程度**：中
- **类别**：架构
- **文件**：`MachineCoordinator.kt`, `AppContainer.kt`
- **问题描述**：`HttpOrderRepository` 和 `PlcController` 没有定义接口，上层直接依赖具体实现，无法替换为测试替身。
- **修复建议**：定义 `OrderRepository` 和 `PlcRepository` 接口。

### 3.5 MachineRuntimeState 是"上帝状态对象"
- **严重程度**：中
- **类别**：架构
- **文件**：`app/src/main/java/com/example/plccontroller/runtime/MachineRuntimeModels.kt`
- **行号**：L113-L157
- **问题描述**：包含 25 个字段，覆盖 PLC 连接状态、网络状态、订单队列、配方目录、转盘状态、维护状态、日志等。任何功能变更都会触发整个状态对象变更，导致所有观察者重新计算。
- **修复建议**：拆分为多个独立的 StateFlow：`plcState`、`orderState`、`transferDeckState`、`networkState`、`maintenanceState`、`logs`。

### 3.6 SettingsScreen 参数列表过长
- **严重程度**：中
- **类别**：架构
- **文件**：`app/src/main/java/com/example/plccontroller/ui/SettingsScreen.kt`
- **行号**：L61-L82
- **问题描述**：`SettingsScreen` composable 签名有 16 个参数，`settingsOverviewCards` 有 9 个参数，`settingsFieldModels` 有 11 个参数，三者参数高度重叠。
- **修复建议**：封装为 `SettingsScreenState` data class。

### 3.7 PlcController.updateCommunicationConfig 中的不安全类型转换
- **严重程度**：中
- **类别**：架构
- **文件**：`app/src/main/java/com/example/plccontroller/data/plc/ModbusRtu.kt`
- **行号**：L410-L417
- **问题描述**：`(transport as? QueuedModbusTransport)?.updateConfig(...)` 需要知道 transport 的具体实现类型，破坏了接口抽象。
- **修复建议**：在 `ModbusTransport` 接口中添加可选的配置更新方法。

---

## 四、性能问题

### 4.1 MachineRuntimeStore.update() 每次重建 TransferDeckState
- **严重程度**：高
- **类别**：性能
- **文件**：`app/src/main/java/com/example/plccontroller/runtime/MachineRuntimeStore.kt`
- **行号**：L16-L19
- **问题描述**：每次 `update()` 都调用 `withRefreshedTransferDeck()`，即使变更与订单无关。PLC 轮询 200ms 一次，每秒 5 次无意义的 TransferDeckState 重建，导致下游所有 collector 重新执行 map + stateIn，触发 UI 重组。
- **修复建议**：移除 `update()` 中的 `withRefreshedTransferDeck()`，改为仅在订单相关字段变化时触发重建。

### 4.2 OrdersScreen 的 remember key 使用列表引用
- **严重程度**：中
- **类别**：性能
- **文件**：`app/src/main/java/com/example/plccontroller/ui/OrdersScreen.kt`
- **行号**：L111-L117
- **问题描述**：`filteredOrders` 的 `remember` 使用了全部订单列表作为 key。PLC 轮询更新 `MachineRuntimeState` 时，`toMainUiState()` 每次都创建新的 `MainUiState` data class，导致所有 List 字段引用变化，`remember` 缓存永远失效。
- **修复建议**：在 `toMainUiState()` 中保持列表引用不变，或使用 `@Stable` 注解的包装类型。

### 4.3 currentDateText() 每次创建 SimpleDateFormat
- **严重程度**：中
- **类别**：性能
- **文件**：`app/src/main/java/com/example/plccontroller/ui/HomeScreen.kt`
- **行号**：L1149-L1151
- **问题描述**：每次调用都 `new SimpleDateFormat()` 和 `new Date()`，PLC 轮询频繁时每秒创建 2-5 个 SimpleDateFormat 对象。
- **修复建议**：将 SimpleDateFormat 提升为顶级 `val` 或 `companion object` 常量。

### 4.4 Brush 对象在 Composable 内频繁创建
- **严重程度**：中
- **类别**：性能
- **文件**：`HomeScreen.kt`, `UiComponents.kt` 多处
- **问题描述**：多处 UI 组件在函数体内直接创建 `Brush` 对象，每帧可能创建 20+ 个 Brush。
- **修复建议**：将固定的 Brush 提升为 `companion object` 或顶层 `val`。

### 4.5 DeviceMetricsPanel 每次重组创建 List
- **严重程度**：低
- **类别**：性能
- **文件**：`app/src/main/java/com/example/plccontroller/ui/HomeScreen.kt`
- **行号**：L721-L727
- **问题描述**：`metricItems` 在函数体内用 `listOf(...)` 直接创建，每次重组都产生新的 List 实例。
- **修复建议**：使用 `remember` 缓存该列表。

### 4.6 log() 每次创建新 List
- **严重程度**：低
- **类别**：性能
- **文件**：`app/src/main/java/com/example/plccontroller/runtime/MachineCoordinator.kt`
- **行号**：L500-L504
- **问题描述**：每次 `log()` 调用都执行 `listOf(newLog) + it.logs.take(11)`，创建多个临时 List 对象。
- **修复建议**：使用 `ArrayDeque` 或 `CircularBuffer` 替代。

### 4.7 PotPartitionDiagram 中动态构建 Regex
- **严重程度**：低
- **类别**：性能
- **文件**：`app/src/main/java/com/example/plccontroller/ui/SecondaryDisplayScreen.kt`
- **行号**：L1172-L1173
- **问题描述**：`extractDirectionalSections` 内部在循环中构建 `Regex`，每次重组都触发正则编译。
- **修复建议**：将每种 `PotMode` 对应的 Regex 预编译为 `companion object` 中的静态常量。

---

## 五、其他问题

### 5.1 生产环境使用 MockModbusTransport
- **严重程度**：高
- **类别**：配置
- **文件**：`app/src/main/java/com/example/plccontroller/AppContainer.kt`
- **行号**：L29
- **问题描述**：`AppContainer` 中硬编码了 `MockModbusTransport()`，没有构建变体或条件逻辑切换到真正的串口传输。
- **修复建议**：通过 BuildConfig 或 DI 框架在 release 构建中注入真正的 Modbus 串口传输实现。

### 5.2 URL 拼接缺少规范化
- **严重程度**：低
- **类别**：边界条件
- **文件**：`app/src/main/java/com/example/plccontroller/data/http/OrderApiClient.kt`
- **行号**：L98
- **问题描述**：`baseUrl` 和 `path` 直接字符串拼接，如果 `baseUrl` 末尾有 `/` 且 `path` 以 `/` 开头，会产生双斜杠。
- **修复建议**：
```kotlin
private fun buildUrl(path: String): URL {
    val base = baseUrl.trimEnd('/')
    return URL("$base/$path")
}
```

### 5.3 PlcPollingSnapshot 使用 List 而非原始数组
- **严重程度**：低
- **类别**：性能
- **文件**：`app/src/main/java/com/example/plccontroller/domain/Models.kt`
- **行号**：L590-L607
- **问题描述**：`PlcPollingSnapshot` 使用 `List<Int>` 和 `List<Boolean>` 存储寄存器数据，存在装箱开销。
- **修复建议**：如果性能敏感，改为 `IntArray` 和 `BooleanArray`。

---

## 六、优先级建议

### P0 - 部署前必须修
1. 修复 recipeCode 静默截断问题（1.1）
2. HTTP 连接添加 try-finally disconnect（1.2）
3. 加强 Modbus 响应长度校验（1.3）
4. 准备真正的 Modbus 串口传输实现（5.1）

### P1 - 尽快修
5. dispatchOrder 失败时通知后端（1.4）
6. 移除 MachineRuntimeStore.update 中的 withRefreshedTransferDeck（4.1）
7. 为 OrderRepository、PlcController 定义接口（3.4）
8. Channel 背压机制（1.5）
9. close() 时的请求清理（1.6）

### P2 - 近期改进
10. 拆分 MachineCoordinator 为多个 UseCase（3.3）
11. 拆分 Models.kt、ModbusRtu.kt 巨型文件（3.1, 3.2）
12. 提取 UI 中的硬编码颜色/尺寸到统一常量（2.9, 2.10）
13. 修复 OrdersScreen remember key 失效问题（4.2）
14. SimpleDateFormat 缓存（4.3）

### P3 - 持续优化
15. 代码重复清理（2.1, 2.2）
16. 命名一致性（2.6, 2.7）
17. 魔法数字注释（2.3, 2.4）
18. Brush 对象缓存（4.4）
19. 日志系统优化（4.6）
