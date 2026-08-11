# PLC 自动恢复测试指南

该测试不驱动水阀、泵或加热器，只操作 Modbus 通讯和 M400 心跳。

自动覆盖以下场景：

1. 真实 PLC 完整状态读取。
2. PLC 命令号写入持久化。
3. 安卓目标进程强制停止并重新启动。
4. 重启后命令号不回退且继续递增。
5. 主动关闭底层串口句柄，下一次轮询自动重开 `/dev/ttyS4`。
6. M400 心跳翻转、读回并恢复测试前的值。
7. 仪器结果和 Logcat 自动归档。

执行命令：

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-plc-recovery.ps1 -DeviceSerial 10.220.129.27:5555
~~~

成功报告必须包含 `status=Succeeded`。该测试能验证安卓进程与串口软件恢复，但不能代替人工拔插通讯线、PLC 断电和物理急停试验。
