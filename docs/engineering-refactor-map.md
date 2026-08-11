# 打锅机上位机重构地图

更新日期：`2026-06-18`

本文档是后续工程化重构的当前入口。它只记录“现在真实是什么状态、下一步优先做什么、哪些地方不能轻易动”。历史草案、旧问题清单和早期评分只用于回查，不再作为当前判断依据。

## 当前结论

项目已经从早期原型推进到工程化中后期。现在的核心问题不是“能不能做下去”，而是继续把最有风险的基础设施收稳：现场验证、持久化、运行状态拆分、文档一致性。

已经完成低风险拆分的主链路：

- 订单：接单、催单、退单、转台、延时、取消、重新加水已经接入。
- 配方：接口同步、本地兜底、配方编辑保存、Code 优先匹配已经形成统一链路。
- 相位：手动模式和接单模式共用 `PhasePlanner` / `PhaseCommandPlanBuilder` / `PhaseExecutionCoordinator`。
- 鸡油/骨膏：左上加料位规则、单个/多个加料锅底、多相位规划已有单元测试保护。
- 锅底详情：手动和接单共用 `PotDetailPresenter`，首页只显示操作员需要的信息。
- 加热：加热参数、D341=10/11/12、加热执行器安全切换已抽到 `HeaterControlCoordinator`。
- 单独加水：单独加水与备用泵配置已抽到 `StandaloneWaterControlCoordinator`。
- 传锅/副屏：传锅协调器、副屏 demo、分区展示和副屏 UI 主体已经拆分。
- 设备注册：注册、设备类型、设备列表已从运行主协调器中分离。
- Modbus：通信层已经完成文件边界拆分，并保留已验证的串口字节级行为。
- 设置存储：`SettingsStore` 已增加统一写入锁和批量写入入口，复合设置保存不再触发多次配置刷新。
- 订单持久化：`LocalOrderStore` 已抽出 `OrderPersistenceStore` 边界，并补最小单元测试；底层仍保持 SharedPreferences JSON。

当前不建议再做大拆大改。下一阶段应该以“现场验证 + 小范围收口”为主。

## 当前结构快照

### 运行协调层

核心文件：

- `app/src/main/java/com/example/plccontroller/runtime/MachineCoordinator.kt`
- `app/src/main/java/com/example/plccontroller/runtime/OrderSyncCoordinator.kt`
- `app/src/main/java/com/example/plccontroller/runtime/FormulaSyncCoordinator.kt`
- `app/src/main/java/com/example/plccontroller/runtime/PhaseExecutionCoordinator.kt`
- `app/src/main/java/com/example/plccontroller/runtime/HeaterControlCoordinator.kt`
- `app/src/main/java/com/example/plccontroller/runtime/StandaloneWaterControlCoordinator.kt`
- `app/src/main/java/com/example/plccontroller/runtime/TransferCoordinator.kt`
- `app/src/main/java/com/example/plccontroller/runtime/EquipmentRegistrationCoordinator.kt`

当前判断：

- `MachineCoordinator.kt` 当前约 442 行，已经不再是最大风险点。
- 它现在更接近 UI 门面 facade，负责对外暴露入口、维护互斥锁和协调各 coordinator。
- 后续不要再把新业务直接堆回 `MachineCoordinator`，新能力优先抽独立 coordinator/use case。

### 订单与配方层

核心文件：

- `app/src/main/java/com/example/plccontroller/data/local/PosOrderTaskAssembler.kt`
- `app/src/main/java/com/example/plccontroller/data/local/OrderEventReducer.kt`
- `app/src/main/java/com/example/plccontroller/data/local/LocalOrderStore.kt`
- `app/src/main/java/com/example/plccontroller/domain/formula/RecipeMatcher.kt`

当前判断：

- 锅底顺序以接口数组顺序为准，不使用 `potSort`。
- 配方匹配以锅型 Code + 锅底 Code 优先，名称只做兜底。
- 订单结构完整性校验已经接入：接口缺失、解析缺失、解析恢复、数量异常会被区分处理。
- `LocalOrderStore` 已通过 `OrderPersistenceStore` 与业务层隔离，短期仍使用 SharedPreferences JSON；长期如果要断电恢复、历史追溯、审计对账，建议新增 Room/SQLite 实现。

### 相位与控制层

核心文件：

- `app/src/main/java/com/example/plccontroller/domain/phase/PhasePlanner.kt`
- `app/src/main/java/com/example/plccontroller/domain/phase/RotationPlanner.kt`
- `app/src/main/java/com/example/plccontroller/domain/phase/PotLayout.kt`
- `app/src/main/java/com/example/plccontroller/runtime/PhaseCommandPlanBuilder.kt`
- `app/src/main/java/com/example/plccontroller/runtime/PhaseCommandExecutor.kt`

当前判断：

- 手动模式与接单模式共享相位规划核心。
- 左上加料位规则、转锅弹窗、普通锅底同步加水、多相位推进都应该继续走这里。
- 相位命令、加热命令、单独加水命令必须继续保持幂等和互斥，不允许 UI 连点造成重复物理动作。

### PLC / Modbus 通信层

核心文件：

- `app/src/main/java/com/example/plccontroller/data/plc/ModbusProtocol.kt`
- `app/src/main/java/com/example/plccontroller/data/plc/SerialModbusTransport.kt`
- `app/src/main/java/com/example/plccontroller/data/plc/QueuedModbusTransport.kt`
- `app/src/main/java/com/example/plccontroller/data/plc/PlcController.kt`
- `app/src/main/java/com/example/plccontroller/data/plc/PlcCommands.kt`

当前判断：

- 原 `ModbusRtu.kt` 已拆分，不再按“1500 行巨型文件”判断。
- 真实风险转移到：`PlcController.kt`、`SerialModbusTransport.kt` 的字节级行为和真实 PLC 长时间稳定性。
- IUCLC、帧间隔、03 读寄存器边界、CRC、心跳、写线圈兼容策略都属于已现场验证链路，非必要不改。
- M400 生产写入已收口到 `PlcPollingService`，相位等待只读 D320 状态，不再并发写心跳；`PlcController` 仅在 05 写成功后更新心跳缓存。
- 01/03 响应除 CRC 外还必须严格匹配请求字节数；有效 CRC 但数据数量不足同样按失败处理。
- `QueuedModbusTransport` 已统一负责队列配置向底层传递，并保证关闭时在途、排队请求都会结束，不允许调用方永久等待。
- 后续如果继续动 Modbus，只允许在真实 PLC 验证窗口内做等价搬移或补测试，不做行为级重写。

### UI 层

当前判断：

- 首页、手动加水、设置配方、设置设备、副屏已经完成主体拆分。
- 仍有若干 400~500 行文件，但不是立即风险。
- 后续按功能变更顺手拆，不为拆而拆。
- 首页业务卡片不得混入寄存器、出水口、相位号、命令参数等工程调试信息；这些应放到设置/维护/调试。

## 已有测试保护

当前已有 12 个单元测试文件，覆盖方向包括：

- `PhasePlannerTest`：鸡油/骨膏左上、多加料锅底、多相位、普通锅底同步加水。
- `PhaseCommandPlanBuilderTest`：相位命令构建、配方 Code 匹配、接口数组顺序。
- `PosOrderTaskAssemblerTest`：订单解析、接口数组顺序、不使用 `potSort`、结构完整性校验。
- `OrderEventReducerTest`：301/302/303/304、催单、转台、退单、去重。
- `OrderQueueProjectorTest`：远程/本地刷新后的队列投影一致性。
- `OrderLifecycleControllerTest`：延时、恢复、取消、重新加水、暂不处理回待加水。
- `LocalOrderStoreTest`：本地订单持久化边界、延时/取消/重新加水、转台 alias、催单去重、结构异常刷新。
- `ModbusRtuTest`：CRC、03 读寄存器帧与数量校验、05 写线圈帧、心跳失败缓存一致性、Mock 状态镜像。
- `QueuedModbusTransportTest`：配置向底层传递、关闭时在途请求释放与底层资源关闭。
- `HeaterControlCoordinatorTest`：加热命令和配置差异判断。
- `StandaloneWaterControlCoordinatorTest`：单独加水、备用泵配置和参数裁剪。
- `SecondaryPotSectionsTest` / `SecondaryDisplayDemoFactoryTest`：副屏分区和 demo 数据。

## 剩余风险排序

### P0：真实 PLC 长时间运行验证

必须继续验证：

- 手动模式加水，含单个和多个鸡油/骨膏锅底。
- 接单模式加水，含转锅、多相位、暂不处理、重新加水。
- 断线、恢复、重启后的通信状态和 UI 提示。
- 急停、液位超低、主备加热互斥、备用泵互斥。
- 加热允许 D341=11 的自动补发策略。
- M400 心跳、D200/D201 温度、D210 液位、D320/D350/D370 状态区读取。

### P1：`LocalOrderStore` 持久化

已完成：

- 已抽出 `OrderPersistenceStore`，业务层不再强依赖 `LocalOrderStore` 具体实现。
- 已给 `LocalOrderStore` 增加可测试的 key-value storage 和 codec 边界。
- 已补 `LocalOrderStoreTest` 覆盖关键本地状态迁移。

短期策略：

- 继续保留 SharedPreferences JSON，避免引入 Room 造成额外变量。

中期策略：

- 如果现场要求断电恢复、历史追溯、订单审计、故障复盘，迁移 Room/SQLite。
- 迁移前先补持久化行为测试，不直接改现场主链路。
- Room/SQLite 迁移评估与 schema 草案见 `order-persistence-room-migration-plan.md`。

### P1：`SettingsStore` 长期增强

已完成：

- 已增加统一写入锁。
- 已增加 `updateBatch`，复合设置保存时只做一次配置刷新。
- PLC 命令号仍使用同步持久化，避免重启/并发导致命令号回退。

后续策略：

- 如果未来要做设置审计、配置版本、保存失败提示，再单独扩展。
- 当前不建议继续扩大这一刀。

注意：

- 涉及加热、备用泵、PLC 配置时，保存前仍要保持互斥校验。

### P2：运行状态模型拆分

当前 `MachineRuntimeState` 承载 PLC、网络、订单、配方、维护、传锅、副屏等多组状态。

后续如果 UI 重组压力增加，再评估拆分：

- `PlcRuntimeState`
- `OrderRuntimeState`
- `FormulaRuntimeState`
- `TransferRuntimeState`
- `MaintenanceRuntimeState`

当前不建议为了抽象而拆。

### P2：UI 文件继续小步拆分

仍可顺手优化：

- `SettingsMaintenancePage.kt`
- `ManualWaterComponents.kt`
- `OrdersScreen.kt`
- `SettingsAccessPage.kt`
- `SettingsFormulaComponents.kt`

策略：

- 有功能变更时顺手拆组件。
- 不改变业务语义。
- 不把业务规则写回 Compose。

## 后续推荐节奏

1. 先按 `field-validation-checklist.md` 做现场验证。
2. 如果订单恢复和审计需求明确，再按 `order-persistence-room-migration-plan.md` 启动 Room/SQLite 专项迁移。
3. 如果 UI 继续膨胀，再按功能变更顺手拆页面组件。
4. Modbus 通信层只在真实 PLC 验证窗口内做小步等价调整。
5. 每次 Kotlin 修改至少跑 `:app:testDebugUnitTest` 和 `:app:assembleDebug`；文档修改可不编译，但要说明原因。

## 不应重复返工的内容

- 不再重新实现一套相位规划。
- 不再按 `potSort` 排锅底顺序。
- 不再在首页展示出水口、相位号、命令参数等工程调试信息。
- 不再把订单事件处理散回 `HttpOrderRepository` / `MachineCoordinator`。
- 不再把配方匹配逻辑分别写在手动模式和接单模式。
- 不在没有真实 PLC 验证窗口时修改串口字节级行为。
