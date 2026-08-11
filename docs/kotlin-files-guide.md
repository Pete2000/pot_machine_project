# PLC 智能打锅机控制系统 - Kotlin 代码架构与文件指南

本项目是用于 **PLC 智能打锅机控制端** 的安卓上位机应用程序。本指南对项目中的所有 Kotlin (`.kt`) 文件按模块进行了详细梳理，阐述其核心职责、内部包含的关键类/接口及其相互调用关系，为后续的二次开发和代码维护提供全景视图。

---

## 目录结构及架构分层

应用主要基于 **分层架构** 与 **单向数据流 (UDF)** 思想构建，核心逻辑包含四大层次：
1. **配置与初始化容器层 (`com.example.plccontroller`)**：管理应用生命周期、依赖注入与多屏幕入口。
2. **数据持久化与通信层 (`data`)**：处理本地 SharedPreferences 持久化、后端 HTTP 接口通信以及基于 Modbus RTU 的串口/模拟 PLC 传输。
3. **业务与设备运行时层 (`runtime`)**：系统的“大脑”，负责状态管理、PLC 定时轮询、订单生命周期调度、多工位相位转换及转锅逻辑。
4. **用户界面层 (`ui`)**：基于 Jetpack Compose 的双屏交互界面，包括店员操作的主屏界面、面向顾客/出餐员的副屏出餐界面，以及硬件调试与配方配置管理后台。

---

## 1. 配置与入口层

位于根包 `com.example.plccontroller` 下：

| 文件名 | 核心类/对象 | 主要职责与实现逻辑 |
| :--- | :--- | :--- |
| [AppConfig.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/AppConfig.kt) | `AppConfig` | **全局静态配置常量表**。定义了系统默认的后端 HTTP API 地址、Modbus 串口通信参数（波特率、数据位、校验、停止位）、轮询间隔、PLC 超时机制，以及打锅机硬件默认的加热控制温度、水泵标定系数、心跳间隔等默认常量。 |
| [AppContainer.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/AppContainer.kt) | `AppContainer` | **轻量级依赖注入 (DI) 容器**。管理全局单例生命周期，包含 `SettingsStore`、`SerialModbusTransport`、`OrderApiClient`、`PlcController`、`MachineRuntimeStore`、`PlcPollingService`、`MachineCoordinator` 等。它监听配置变更流并动态刷新各组件底层的通信配置和接口 URL。 |
| [MainActivity.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/MainActivity.kt) | `MainActivity` | **主屏入口 Activity**。开启 Kiosk 全屏防误触模式（隐藏系统状态栏和虚拟导航栏，保持屏幕常亮）。渲染主操作界面 `MainScreen`，并提供启动/预览副屏 Activity 的接口。 |
| [PlcControllerApplication.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/PlcControllerApplication.kt) | `PlcControllerApplication` | **应用 Application 基类**。负责在应用启动时初始化全局唯一的 `AppContainer` 依赖注入容器。 |
| [SecondaryDisplayActivity.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/SecondaryDisplayActivity.kt) | `SecondaryDisplayActivity` | **副屏物理显示 Activity**。利用 DisplayManager 投影到第二块物理显示器上，显示面向取餐用户的排队出餐进度状态，无 debug 工具栏。 |
| [SecondaryDisplayPreviewActivity.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/SecondaryDisplayPreviewActivity.kt) | `SecondaryDisplayPreviewActivity` | **副屏预览 Activity**。用于单屏开发调试阶段，在主屏内以独立 Activity 的形式预览副屏效果，并带有一键模拟出餐/排队的 debug 面板。 |

---

## 2. 数据层 (data)

处理网络请求、持久化配置以及底层 PLC 物理串口/协议通信。

### 2.1 本地配置存储 (data)
*   [SettingsStore.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/data/SettingsStore.kt)
    *   **核心类**：`SettingsStore` (管理 SharedPreferences 读写), `AppPersistentConfig` (数据类)
    *   **职责**：负责本地设备配置的持久化存储。包括主/副服务器 URL、PLC 串口路径及协议参数、标定系数、备用水泵模式、温度设定及公式配方数据的 JSON 本地缓存。
    *   **核心机制**：内部维护一个 Kotlin `StateFlow<AppPersistentConfig>`，外部任何组件修改配置（如修改波特率或温度阈值）都会自动触发更新并通知订阅者。

### 2.2 网络 HTTP 通信 (data/http)
*   [OrderApiClient.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/data/http/OrderApiClient.kt)
    *   **核心类**：`OrderApiClient` (基于 OkHttp 的网络客服端)
    *   **职责**：封装与业务后台服务器交互的原生 HTTP 网络请求。主要包括轮询挂起订单列表、上报订单开始/完成/取消状态、拉取最新的锅底配方目录等，支持动态切换 Base URL。
*   [HttpOrderRepository.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/data/http/HttpOrderRepository.kt)
    *   **核心类**：`HttpOrderRepository`
    *   **职责**：网关代理类。充当 domain 层与 `OrderApiClient` 之间的适配器，将网络传输的模型（DTO）转换为 domain 层的业务模型，隐藏底层网络访问细节。
*   [PosOrderApiModels.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/data/http/PosOrderApiModels.kt) 与 [FormulaApiModels.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/data/http/FormulaApiModels.kt)
    *   **职责**：数据传输对象 (DTO) 定义。分别对应 POS 订单报文格式和云端配方树形报文格式，包含对应的 JSON 序列化与反序列化工具函数。

### 2.3 PLC 协议与串口通信 (data/plc)
*   [NativeSerialPortConfigurator.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/data/plc/NativeSerialPortConfigurator.kt)
    *   **核心类**：`NativeSerialPortConfigurator`
    *   **职责**：串口驱动配置。加载 JNI 动态链接库 `libplcserial.so`，利用底层的 C/C++ 代码配置安卓串口文件描述符 (FileDescriptor) 的波特率、数据位、校验位和停止位，使其工作在 RAW 裸串口模式。
*   [ModbusRtu.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/data/plc/ModbusRtu.kt) *(重要核心文件)*
    *   **核心类/接口**：
        *   `ModbusTransport`：定义 Modbus 事务接口，输入请求字节数组，挂起等待返回响应字节数组。
        *   `SerialModbusTransport`：**物理串口 Modbus 传输层实现**。负责打开 `/dev/ttyS*` 设备文件，调用 Native 层配置串口，并通过 Java 输入输出流读写二进制 Modbus RTU 报文。自带 CRC16 校验生成与验证，支持“纯发送测试模式”。
        *   `MockModbusTransport`：**模拟 PLC 传输层实现**。在不连接真实硬件时，在内存中模拟 PLC 寄存器映射表的读写，方便软件层面业务逻辑的测试和界面预览。
        *   `QueuedModbusTransport`：**并发队列传输层**。因为串口是独占式半双工介质，该类通过内部 Channel 队列，将所有并发的 PLC 读写请求转换为单线程串行执行，避免报文冲突。
        *   `PlcController`：**上位机对 PLC 的控制 API**。向上层提供高级抽象函数（如 `pollSnapshot` 读取整体状态、`writePhaseCommand` 下发辅料时间、`writeHeartbeat` 写入心跳位等），它在底层将请求组装为标准的 Modbus 功能码（01 读取线圈、03 读取保持寄存器、05 写入单线圈、16 写入多寄存器）并交由传输层执行。

---

## 3. 领域模型层 (domain)

*   [Models.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/domain/Models.kt)
    *   **关键定义**：
        *   `OrderStatus`：挂起、待加水、等待转锅、执行中、已完成、已取消等订单生命周期状态。
        *   `PotMode` / `ManualWaterPotMode`：锅具物理结构（单锅、双格、四格）。
        *   `ManualWaterPhaseSlot` / `ManualWaterPhaseRequest`：手动加水所选工位的加水/辅料时长参数。
        *   `FormulaCatalog` / `FormulaPotType`：配方库结构，规定不同锅底对应的加水、鸡油、骨膏标准秒数。
        *   `PlcRegisterMap`：**PLC 寄存器物理地址映射表**。定义了 PLC 内部线圈（如水泵、加热器、传锅电机）和保持寄存器（如温度、工位状态、动作执行 ID）对应的 Modbus 物理地址偏置。

---

## 4. 业务运行时与调度层 (runtime)

这是应用最核心的控制枢纽，连接了 UI 交互与 PLC 状态机。

*   [MachineRuntimeModels.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/runtime/MachineRuntimeModels.kt)
    *   **关键定义**：
        *   `MachineRuntimeState`：**设备全局内存状态大对象**。包含 PLC 连接状态、网络状态、当前订单、待处理订单列表、配方缓存、当前待确认的转锅提示对象、手动模式加水进度令牌、系统运行日志等。
        *   `TransferDeckState` / `TransferCardModel`：餐牌（锅位）卡片槽位状态，用于处理“传锅”流程的逻辑卡片序列。
*   [MachineRuntimeStore.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/runtime/MachineRuntimeStore.kt)
    *   **核心类**：`MachineRuntimeStore`
    *   **职责**：内存状态容器。利用 `MutableStateFlow` 线程安全地保管 `MachineRuntimeState` 快照，提供原子更新 `update` 方法，一旦状态变化，界面层会立即重绘。
*   [PlcPollingService.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/runtime/PlcPollingService.kt)
    *   **核心类**：`PlcPollingService`
    *   **职责**：**PLC 状态轮询服务**。在后台启动一个独立协程轮询任务。
    *   **核心机制**：
        1. 周期性地（根据活跃状态在 200ms 至 500ms 间自动调整）向 PLC 发送 03 功能码，获取传感器温度、液位状态、电机限位等最新硬件数据，更新至 `MachineRuntimeStore`。
        2. 定期（默认 1000ms）向 PLC 写入心跳翻转位 `M400`。若 PLC 连续多次轮询超时，则自动将系统判定为 `Fault` 故障状态，保证安全联锁。
        3. 检测到 PLC 连接重建时，自动重新下发当前的加热器控制指令，确保状态一致。
*   [MachineCoordinator.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/runtime/MachineCoordinator.kt) *(重要核心文件)*
    *   **核心类**：`MachineCoordinator`
    *   **职责**：**业务总调度中心**。
    *   **核心机制**：
        1.  **配方同步逻辑**：在后台定时同步云端锅底配方，若同步成功则持久化缓存至本地；如果断网则使用 `SettingsStore` 的缓存，确保离线可用。
        2.  **自动接单循环**：当“自动接单”开启且系统无活跃订单时，定时向 HTTP 接口拉取待执行订单，自动进入物理执行流程。
        3.  **多阶段（Phase）指令链构建**：无论是自动订单还是手动模式，因为鸡油、骨膏出料口固定在 **左上工位**。若右侧或下方锅位需要辅料，系统会通过算法生成一个包含转锅（传锅电机运行）与加料阶段的 `PhaseCommandPlan` 步骤列表。
        4.  **转锅提示与人机协同**：当需要转锅时，暂停 PLC 自动加料，向界面抛出 `PhaseRotationPrompt` 弹窗。店员手动完成旋转或确认旋转后，点击弹窗中的“已转锅，继续加料”，系统则调用 `PlcController` 写入新的命令 ID 和配料参数，启动下一阶段。
        5.  **订单状态机回传**：在订单开始、转锅完成、最终加料结束后，分别向后端发送 HTTP 回调以更新云端订单状态。

---

## 5. 用户界面层 (ui)

采用 Jetpack Compose 构建的声明式 UI：

### 5.1 主界面与核心逻辑
*   [MainScreen.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/MainScreen.kt)
    *   **主要职责**：界面的整体骨架。包含底部导航栏（主控、订单、设置），并在最顶层负责渲染全局的 **转锅确认弹窗** (`AlertDialog`)，处理店员确认转锅 (`confirmPhaseRotation`) 或取消/暂不处理的操作。
*   [MainViewModel.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/MainViewModel.kt)
    *   **主要职责**：UI 层与业务层（`MachineCoordinator`）之间的桥梁。收集并组合 `MachineRuntimeStore` 和 `SettingsStore` 的状态，对外暴露按钮点击事件（如手动加水、自动接单开关、配方调试、确认转锅等）。
*   [HomeScreen.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/HomeScreen.kt) *(主操作面板)*
    *   **主要职责**：店员最常驻的界面。支持选择锅具类型（单锅、双格、四格），为每个锅位选择锅底口味（如番茄、清汤、麻辣），并呈现设备状态监控（液位、温度）。底部的“手动加水/加料”区域是手动模式的操作入口。
*   [OrdersScreen.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/OrdersScreen.kt)
    *   **主要职责**：订单列表页。展示“待加水”、“已完成”、“已取消”的订单，支持店员手动重新拉取订单、删除历史日志以及查看详细的网络/PLC 操作时序日志。

### 5.2 后台设置页面 (ui)
*   [SettingsScreen.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/SettingsScreen.kt)
    *   **主要职责**：设置主控页。包含侧边栏导航，用来切换设备连接、配方库标定、维护排错等不同的子设置卡片。
*   [SettingsAccessPage.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/SettingsAccessPage.kt)
    *   **主要职责**：管理后台准入权限。输入密码以进入高级设备设置或硬件标定参数页，防止店员误改核心参数。
*   [SettingsDevicePage.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/SettingsDevicePage.kt)
    *   **主要职责**：**高级硬件参数调节页**。包括配置物理串口号、串口波特率、网关 URL 地址；单独配置和标定 1-4 号水口出水流速比率、鸡油泵/骨膏泵的标定系数，以及备用泵冗余策略。
*   [SettingsFormulaPage.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/SettingsFormulaPage.kt)
    *   **主要职责**：**本地配方调试管理页**。列出当前加载的配方大类，点击可手动微调特定锅底的标配加水秒数、加鸡油秒数、加骨膏秒数，用于应对物料粘稠度变化时的微调。
*   [SettingsMaintenancePage.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/SettingsMaintenancePage.kt)
    *   **主要职责**：**点动维护测试排错页**。提供直接向 PLC 发送单点控制信号的开关（如：手动开启传锅电机、手动点动 1 号水泵、手动开关加热管），并实时显示 PLC 输入点（限位开关、液位计）的状态，是硬件工程师排查线路故障的利器。
*   [SettingsDebugPage.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/SettingsDebugPage.kt)
    *   **主要职责**：**软件运行调试页**。用于研发人员调试。提供一键模拟网络状态变化（断网、同步中）、模拟订单生成下发、模拟 PLC 连接异常等测试工具。

### 5.3 辅助 UI 组件
*   [SecondaryDisplayScreen.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/SecondaryDisplayScreen.kt) 和 [SecondaryDisplayViewModel.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/SecondaryDisplayViewModel.kt)
    *   **主要职责**：副屏界面及对应 ViewModel。专门负责用大字体向外展示“正在加水”、“等待传锅取餐”的餐牌卡片与号码，与物理传锅操作实时联动。
*   [Theme.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/Theme.kt)
    *   **主要职责**：主题色彩与样式定义。本项目采用富有现代科技感的**暗黑/星空蓝微光美学**（Deep Ocean/Dark Panel），在这里定义了 `Yellow` (辅色高亮)、`Cyan` (青色标定)、`Panel` (卡片背景)、`PageBackground` (主屏深色背景) 等统一配色标记。
*   [UiComponents.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/UiComponents.kt)
    *   **主要职责**：通用的 Compose 自定义基础组件。包含微光发光卡片、圆角按钮、LED 式指示灯、段落标题等基础样式控件，维持全系统视觉风格的一致性。
*   [UiModels.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/UiModels.kt)
    *   **主要职责**：UI 专用的辅助数据结构。定义了底部 Tab 种类、设置页菜单、格式化时间/秒数的工具函数等。
*   [UiStatusText.kt](file:///D:/Users/achillesniu/Documents/pot_machine_project/app/src/main/java/com/example/plccontroller/ui/UiStatusText.kt)
    *   **主要职责**：定义设备各物理部件与订单流转在 UI 上显示的文案与颜色映射规则。
