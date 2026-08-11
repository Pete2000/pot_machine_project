# PLC 智能打锅机控制系统 - 架构设计评审与改进建议书

本文档针对当前打锅机安卓控制端骨架系统的源码进行了深度剖析，指出了在物理串口通信、业务状态恢复、人机交互防御性设计以及工业级防灾安全（Watchdog）方面的潜在隐患与缺陷，并给出了具体的修改建议与代码级别改进设计方案，供 Codex 进行研判与代码自动演进。

---

## 1. 核心缺陷：Modbus 串口通信在物理连接断开时无法自动恢复

### 1.1 隐患分析
在 [ModbusRtu.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/data/plc/ModbusRtu.kt#L63-L88) 的 `SerialModbusTransport.transact` 方法中，执行事务的代码直接包裹在 `synchronized(ioLock)` 中，但在遭遇强电磁干扰、拔线或硬件串口芯片死锁时，`outputStream!!.write(request)` 或 `readResponseLocked` 会抛出 `IOException`、`SocketTimeoutException` 或 `EOFException`。
```kotlin
// 当前 transact 源码逻辑片段
override suspend fun transact(request: ByteArray): ByteArray {
    val snapshotConfig = config
    if (snapshotConfig.serialPortPath.isMockPath()) {
        return mockDelegate.transact(request)
    }
    return withContext(Dispatchers.IO) {
        synchronized(ioLock) {
            ensureOpenLocked(snapshotConfig) // 如果 openedPath 非空且流不为 null，直接返回
            discardPendingInputLocked()
            outputStream!!.write(request)    // 若此处跑出 IOException
            outputStream!!.flush()
            val response = readResponseLocked(...)
            response
        }
    }
}
```
由于此方法中没有任何异常捕获来置空并关闭串口，通信一旦报错，异常向外抛出。但是，`serialFile`、`inputStream` 和 `outputStream` 变量**仍然保持非 null**。
下一次轮询或写入再次调用 `transact` 时，`ensureOpenLocked` 会判定连接有效而直接跳过打开步骤，使用已经失效的流继续写入，导致应用陷入**永久通信故障**，除非人工手动修改配置（触发 `updateConfig()` -> `closeLocked()`）或重启 App。

### 1.2 改进方案
在 `transact` 内部加入异常拦截，一旦捕获到任何底层 I/O 读写异常，必须**立即调用 `closeLocked()`** 释放文件描述符，清空引用，从而确保下一次事务执行时，`ensureOpenLocked` 能强制重新打开 `/dev/ttyS4` 并调用 JNI 初始化串口。

#### 建议代码变更 (Diff 方案)
```diff
     override suspend fun transact(request: ByteArray): ByteArray {
         val snapshotConfig = config
         if (snapshotConfig.serialPortPath.isMockPath()) {
             return mockDelegate.transact(request)
         }
         return withContext(Dispatchers.IO) {
             synchronized(ioLock) {
-                ensureOpenLocked(snapshotConfig)
-                discardPendingInputLocked()
-                logFrame("TX", request, snapshotConfig.serialPortPath)
-                outputStream!!.write(request)
-                outputStream!!.flush()
-                if (snapshotConfig.serialPortPath.isSendOnlyPath()) {
-                    val synthetic = synthesizeResponse(request)
-                    logFrame("RX-SYNTH", synthetic, snapshotConfig.serialPortPath)
-                    return@withContext synthetic
-                }
-                val response = readResponseLocked(
-                    request = request,
-                    timeoutMs = snapshotConfig.normalizedReadTimeoutMs
-                )
-                logFrame("RX", response, snapshotConfig.serialPortPath)
-                response
+                try {
+                    ensureOpenLocked(snapshotConfig)
+                    discardPendingInputLocked()
+                    logFrame("TX", request, snapshotConfig.serialPortPath)
+                    outputStream!!.write(request)
+                    outputStream!!.flush()
+                    if (snapshotConfig.serialPortPath.isSendOnlyPath()) {
+                        val synthetic = synthesizeResponse(request)
+                        logFrame("RX-SYNTH", synthetic, snapshotConfig.serialPortPath)
+                        return@withContext synthetic
+                    }
+                    val response = readResponseLocked(
+                        request = request,
+                        timeoutMs = snapshotConfig.normalizedReadTimeoutMs
+                    )
+                    logFrame("RX", response, snapshotConfig.serialPortPath)
+                    response
+                } catch (e: Exception) {
+                    Log.w(TAG_SERIAL, "Serial transaction error, closing port to force reconnect: ${e.message}", e)
+                    closeLocked() // 核心修改：发生任何异常时，关闭并清空串口连接
+                    throw e
+                }
             }
         }
     }
```

---

## 2. 工业级防灾联锁：PLC 看门狗安全保护 (Watchdog)

### 2.1 潜在隐患
在打锅机加热和加水期间，如果安卓主板突然断电、崩溃、冻结或出现由于 JNI 导致的原生层 Crash，PLC 硬件将永远停留在最后一个控制状态（如：阀门打开 `M101=1`，加热使能 `M100=1`）。如果没有保护逻辑，极易引发**溢水、干烧甚至起火**等严重现场事故。

### 2.2 改进方案
必须在上下位机之间确立**硬件看门狗握手逻辑**。
1. **安卓上位机**：在 [PlcPollingService.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/runtime/PlcPollingService.kt) 的轮询任务中，定期（如每 1000ms）通过 `writeHeartbeat` 向 PLC 心跳线圈 `M400` 翻转写入 `0/1`。
2. **PLC 下位机**：在 PLC 梯形图程序中开启一个看门狗定时器（如 3000ms）。
   * 每次检测到 `M400` 状态翻转（上升沿或下降沿），重置该定时器。
   * 如果在 3000ms 内 `M400` 的电平从未变化，则触发**超时联锁停机保护**。PLC 应当无条件关闭所有的加热输出控制线圈（如关闭 `M100`、`M101`）和电磁阀，并向硬件指示灯发出警报。

---

## 3. 防断电数据恢复：本地持久化数据库 (SQLite/Room)

> 实施状态（2026-06-19）：Room v1 已作为订单主存储，旧 SharedPreferences 订单在首次启动时一次性导入；订单生命周期、低频 PLC 命令和通信故障已建立审计表。相位级断点续作仍未开放，异常重启后不得自动重复下发物理命令。

### 3.1 现状与隐患
订单当前态已经写入 Room，重启后可恢复待加水、延时、待传锅、完成和取消记录。运行中的相位上下文仍不支持自动断点续作；如果安卓在物理动作期间异常重启，必须结合 PLC 实际状态人工确认，禁止直接重复下发。

### 3.2 改进建议
建议引入 Android 官方的 `Room` 持久化组件：
1. **建立订单实体表 `OrderEntity`**，包含主键 `id`、锅型、口味、相位计划执行进度 JSON、当前状态等。
2. **状态机强一致性**：
   * 从 HTTP 接口拉取订单时，首先写入 Room（状态设为 `PENDING`）。
   * 启动相位指令发送前，将状态改为 `RUNNING`。
   * 转锅确认和物理操作完成时，先更新 Room，再回传 HTTP 接口。
3. **断电重启自检**：在 [MainActivity.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/MainActivity.kt) 初始化时，读取 Room 内处于 `RUNNING` 状态的脏数据，并弹窗提示店员：“检测到上一次非正常关机，上一次订单未完成，是否恢复中断的相位继续加料？”。

---

## 4. UI 界面防御性设计：并发操作死锁防护

### 4.1 现状与隐患
在打锅机运行期间，可能会发生两种操作冲突：
1. 自动接单流程已拉取订单并准备向 PLC 发送加热和相位参数，但店员此时又在 [HomeScreen.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/HomeScreen.kt) 的“手动加水”面板上选择锅底并点击“加水”。
2. 虽然底层 `MachineCoordinator` 的 `maintenanceLock` 和 `plcCommandLock` 锁住了写指令，但在 UI 上没有显式的置灰拦截，店员可以在“自动加料”时疯狂点击“手动加水”，造成网络请求堆积或界面响应卡死。

### 4.2 改进建议
在 [HomeScreen.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/HomeScreen.kt#L332) 中，应增强 `manualWaterEnabled` 的计算条件，实施双向互斥：
```kotlin
// 增强后的手动加水可用性判定
val isSystemBusy = state.currentOrder != null || state.automationEnabled
val manualWaterEnabled = phaseRotationPrompt == null && allSlotsSelected && !isSystemBusy
```
并在自动接单页面（或者手动模式运行中），将对应的 Tab 切换栏或操作按钮实施置灰和点击穿透禁用，从 UI 输入源上彻底杜绝并发死锁的可能。
