# 真实 PLC 老化测试指南

## 目标

这套测试用于验证安卓上位机、Modbus RTU、PLC 相位状态机和四路出水执行器在长时间重复运行下是否稳定。它不是普通 JVM 单元测试，也不会在常规构建或 CI 中自动驱动物理设备。

当前第一版只测试水阀相位，不测试加热、鸡油泵、骨膏泵或备用泵。

## 三层测试

1. PlcAgingTestRunnerTest：纯 JVM 单元测试，验证循环、场景轮换、失败即停和人工停止，不连接 PLC。
2. RealPlcAgingInstrumentedTest：运行在安卓屏幕上，复用正式 AppContainer -> MachineCoordinator -> PhaseCommandExecutor -> PlcController 链路。
3. scripts/run-plc-aging.ps1：构建、安装、显式授权执行，并归档仪器测试输出和 Logcat。

真实测试不会另写 Modbus 协议实现。每次命令仍经过正式相位命令审计、D320~D329 完成握手和串口队列。

## 安全前提

开始前必须确认：

- PLC 和安卓上位机通讯正常。
- 急停按钮可用，人员可立即触达。
- 急停采用常闭回路：`M300=1` 表示回路正常，`M300=0` 表示急停按下或线路断开。
- D210 可以为 1（低液位）、3（中液位）或 7（高液位）；0 和异常组合仍禁止测试。
- 主/备加热输出均已关闭。
- 补水、单独加水、喷雾、鸡油、骨膏和备用泵均未运行。
- 四个出水口均已接入安全收集容器，现场不会溢水。
- 首次只运行 5 次短测试，确认物理接线和出水口对应关系后再增加循环。

任一轮动作前或动作后检测到急停、液位异常、输出未复位、PLC 拒绝、命令号不一致或相位超时，测试立即失败并停止后续命令。

## 执行

在项目根目录打开 PowerShell：

~~~powershell
.\scripts\run-plc-aging.ps1 -DeviceSerial 10.220.129.27:5555 -Cycles 5 -ActionTicks 10 -IntervalMs 5000 -ConfirmPhysicalTest
~~~

ActionTicks=10 表示单次动作 1 秒。支持的场景名称：

- WaterOutlet1
- WaterOutlet2
- WaterOutlet3
- WaterOutlet4
- AllWaterOutlets

例如只测 1 号和 2 号出水口：

~~~powershell
.\scripts\run-plc-aging.ps1 -DeviceSerial 10.220.129.27:5555 -Cycles 20 -Scenarios "WaterOutlet1,WaterOutlet2" -ConfirmPhysicalTest
~~~

测试结果写入 artifacts/plc-aging/。仪器输出用于判断通过/失败，Logcat 文件用于定位具体 Modbus 帧、PLC 相位状态和异常堆栈。

运行期间，屏幕本机会把进度和最终结果写入 `files/plc-aging-report.txt`。如果 ADB Wi-Fi 临时断开，测试仍在屏幕本机继续；脚本重连后以该文件的最终状态为准，不把 ADB 断线误判为 PLC 失败。

## 建议节奏

1. 冒烟：5 次，1 秒动作，5 秒间隔。
2. 短时：100 次，1 秒动作，5 秒间隔。
3. 老化：连续 8 小时，按实际水箱容量安排补水和人工巡检。
4. 故障注入必须人工完成：断通讯、PLC 重启、安卓重启、急停和液位降至保护阈值，并记录恢复时间。

当前脚本只负责正常循环和失败即停。故障注入不要在无人值守状态下执行。
