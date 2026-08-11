# 架构说明

## 1. 控制主体建议

建议采用以下结构：

1. 安卓主屏作为上位控制台，负责接单、展示、参数下发、状态回传
2. PLC 作为底层实时执行器，负责电机、加热、传感器和联锁保护
3. 安卓和 PLC 之间通过 RS485 + Modbus RTU 通信
4. 后端订单系统与安卓主屏通过 HTTP 通信

这样分层的好处是：

1. PLC 保持现场控制稳定，不依赖安卓界面存活
2. 安卓负责业务逻辑和联网，比直接让 PLC 接 HTTP 更灵活
3. 现场寄存器和云端订单可以分开演进

## 2. 当前工程里的默认链路

当前代码默认按以下流程工作：

1. 安卓端轮询 `GET /api/orders/pending`
2. 取得待执行订单后，调用 `POST /api/orders/{id}/start`
3. 将订单字段写入 PLC 寄存器
4. 向 PLC 的启动寄存器写入 `1`
5. 执行结束后调用 `POST /api/orders/{id}/finish`

## 3. 当前 PLC 映射基线

当前项目已经按真实地址表整理出一版 PLC 映射基线，见
[plc-register-map-baseline.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/plc-register-map-baseline.md)。

对应的业务动作到 PLC 写入关系，见
[plc-action-execution-baseline.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/plc-action-execution-baseline.md)。

对应的上下位机握手协议草案，见
[plc-command-handshake-draft.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/plc-command-handshake-draft.md)。

对应的“4 路独立加水时长 + 左上工位辅料约束”字段草案，见
[plc-phase-field-draft.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/plc-phase-field-draft.md)。

对应的“接单 -> 解析 -> 相位生成 -> PLC 执行”流程图版，见
[order-to-plc-flowchart.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/order-to-plc-flowchart.md)。

目前已经落地到 `PlcRegisterMap` 的核心内容包括：

1. `RS485 + Modbus RTU + 19200 + 8N1 + slaveId=1`
2. 读取按钮状态：`M300 ~ M305`
3. 读取输出状态：`M320 ~ M330`
4. 读取字寄存器：`D200`、`D201`、`D210`
5. 写控制线圈：`M100 ~ M110`
6. 写应急时间寄存器：`D212`、`D214`
7. 心跳位：`M400`

早期演示阶段那套 `100/101/110` 假寄存器没有直接删除，而是被隔离到了
`prototypeDispatchRegisters`，仅用于让原型流程继续编译，不参与正式 PLC 联调。

## 4. 你下一步最需要补齐的内容

### PLC 侧

1. PLC 型号
2. 串口参数
   例如波特率、数据位、停止位、校验位
3. 完整寄存器表
4. 设备运行状态码定义
5. 故障码定义

### 安卓硬件侧

1. 安卓主屏型号
2. 与 PLC 的物理连接方式
   例如 USB 转 RS485，或板载串口转 RS485
3. 是否需要开机自启动
4. 是否需要离线缓存订单

### 后端接口侧

1. 订单 JSON 字段结构
2. 是否只轮询待执行订单，还是支持抢单
3. 执行中的心跳回传频率
4. 失败重试和补偿规则

## 5. 代码层建议

如果你准备继续做成正式可上线版本，建议下一阶段做这几件事：

1. 把 `MockModbusTransport` 换成真实串口传输层
2. 增加本地数据库，用于离线缓存订单和执行记录
3. 给订单执行增加状态机，避免重复下发
4. 增加 PLC 轮询读取，展示设备实时状态
5. 增加异常恢复，例如网络断开、PLC 超时、串口重连

## 6. 低配置安卓主机补充建议

如果现场安卓主机配置一般，正式版建议优先满足下面这些目标：

1. 界面稳定不掉帧，比炫酷更重要
2. 所有通讯和数据解析必须后台化
3. 首页只做局部刷新，不做整页重绘
4. 异常场景可恢复，不能因为接口超时导致界面卡死
5. 订单、PLC 状态、日志分离刷新，避免互相拖累

详细评审见 [performance-and-api-review.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/performance-and-api-review.md)。

## 7. 打锅机控制规则补充

目前已明确一层关键控制规则：

1. 单锅
   4 个出水口同时出同一种锅底
2. 拼锅
   左侧 `1 + 3` 一种锅底
   右侧 `2 + 4` 一种锅底
3. 四宫格
   `1 / 2 / 3 / 4` 各出各自锅底
4. 鸡油和骨膏出料口位于左上角工位
5. 非左上锅位如果要鸡油或骨膏，需要自动转锅

这部分详细规则见 [control-routing.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/control-routing.md)。

## 8. 锅底配方驱动

当前又明确了一条很关键的约束：

1. 加水、鸡油、骨膏不是靠订单文字判断
2. 它们由锅底 ID 映射到锅底配方接口的数据来决定
3. 当鸡油或骨膏时长不为 0 时，该锅底必须走左上角辅料工位

这部分详见 [recipe-profile-notes.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/recipe-profile-notes.md)。
