# Modbus RTU 串口返回帧被 tty 层改写问题日志

## 结论

本问题不是 PLC 程序错误，也不是 Modbus CRC 算法错误，而是 Android 工控板 `/dev/ttyS4` 的 Linux tty 输入层没有进入真正的二进制 raw 模式，导致 PLC 返回的 Modbus RTU 字节在进入上位机代码前已经被系统层改写。

最终修复方向：上位机打开串口 fd 后，使用 native `termios/tcsetattr(TCSANOW)` 直接配置串口，不能只依赖 `stty`。

## 现场现象

- PLC 能收到 Android 上位机发出的请求。
- 心跳 M400 可以在 PLC 中正常翻转。
- PC 串口调试工具直连 PLC 时，`03` 读寄存器返回正常。
- Android 上位机日志中经常出现 `Invalid Modbus CRC`。
- 部分帧肉眼看数据区好像接近正确，但 CRC 字节和个别数据字节稳定异常。
- 读 D320/D350/D370 状态区时，有时只看到 TX，看不到完整 RX。

## 关键证据

### 1. IUCLC 导致字节固定加 `0x20`

历史日志中出现过：

```text
正常：... 01 54 ... 10 D6
异常：... 01 74 ... 10 F6
```

典型变化：

```text
0x54 -> 0x74
0xD6 -> 0xF6
0xCC -> 0xEC
```

这是 `IUCLC` 输入大写转小写造成的固定 `+0x20` 类污染，不像随机线路干扰。

### 2. ISTRIP 导致最高位 `0x80` 被清掉

最新日志中出现：

```text
正常 CRC：AC 92
异常 CRC：2C 12

正常字节：D6 / D7
异常字节：56 / 57
```

典型变化：

```text
0xAC -> 0x2C
0x92 -> 0x12
0xD6 -> 0x56
0xD7 -> 0x57
```

这是 `ISTRIP` 清除第 8 位导致的稳定污染。

### 3. IXON/IXOFF 可能吞掉控制字符

D320 状态区曾经出现过正常返回帧尾部：

```text
... 95 13
```

其中 `0x13` 是 XOFF 控制字符。如果 `IXON/IXOFF` 开启，tty 层可能把它当成软件流控字符处理，导致上位机等不到完整返回帧。

## stty 验证结果

上位机已经执行过多种 `stty` 命令：

```text
stty -F /dev/ttyS4 19200 cs8 -cstopb -parenb raw ...
stty -F /dev/ttyS4 -istrip -inpck -ignpar -ixon -ixoff -iuclc ...
stty -istrip -inpck -ignpar -ixon -ixoff -iuclc ... < /dev/ttyS4
```

但 `stty -a` 仍显示：

```text
inpck istrip ixon ixoff iuclc ixany imaxbel iutf8
```

这说明在当前设备上，`stty` 写入这些输入标志没有实际生效，不能作为最终修复方案。

## 为什么心跳曾经看起来正常

心跳 M400 正常翻转，只能证明 Android 发给 PLC 的 TX 帧正常，PLC 能执行动作。

它不能证明 PLC 返回给 Android 的 RX 帧正常。

之前问题被掩盖的原因：

- 写线圈动作主要依赖 TX，PLC 收到就能执行。
- 代码里曾经存在 CRC 错误仍继续放行的兼容逻辑。
- 简单心跳场景不一定频繁碰到 `0x80` 以上字节或 `0x13` 控制字符。
- 手动加水后开始频繁读取 D320/D350/D370，相位状态帧更容易触发 tty 层污染。

## 修复策略

### 1. 使用 native termios 配置串口

在 Android 上位机打开 `/dev/ttyS4` 后，直接对已打开的 fd 执行：

```text
tcgetattr
cfsetispeed / cfsetospeed
tcflush
tcsetattr(fd, TCSANOW, ...)
```

核心目标：

```text
c_iflag = 0
c_oflag = 0
c_lflag = 0
c_cflag = CLOCAL | CREAD | CS8 | 8N1
VMIN = 0
VTIME = 1
```

这样可以关闭：

```text
IUCLC
ISTRIP
IXON / IXOFF / IXANY
INPCK / IGNPAR / PARMRK
ICRNL / INLCR / IGNCR
IUTF8 / IMAXBEL
OPOST
ICANON / ECHO / ISIG / IEXTEN
```

### 2. CRC 错误不再兼容放行

如果 CRC 校验失败，说明上位机拿到的帧不可信。此时不能继续解析并更新 UI，否则会把被污染的数据当成真实 PLC 状态。

### 3. 日志区分故障类型

后续 UI 和日志应尽量区分：

- 串口 raw 配置异常
- Modbus CRC 异常
- 读寄存器超时
- 写命令失败
- PLC 业务状态故障

不要把所有异常都笼统显示成 `PLC 故障`。

## 修复后验证标准

### 1. 串口配置日志

应看到 native 配置成功：

```text
Native serial configured: iflag=0x0 oflag=0x0 ...
```

`stty -a` 如果仍显示旧状态，以 native 日志为主，因为当前设备已证明 `stty` 状态不一定可靠。

### 2. 读线圈 CRC 正常

```text
TX 01 01 01 2C 00 28 FC 21
RX 01 01 05 01 00 00 00 00 AC 92
```

不能再出现：

```text
RX ... 2C 12
```

### 3. 读 D200/D210 CRC 正常

```text
TX 01 03 00 C8 00 0B 85 F3
RX ... 10 D6
```

不能再出现：

```text
RX ... 10 56
```

### 4. 读 D320/D350/D370 能完整返回

重点观察包含 `0x13`、`0x95`、`0xA3`、`0xE5` 等高位或控制字符的返回帧，不应再被吞字节或改字节。

## 排障经验

如果同类问题再次出现，优先按下面顺序排查：

1. 固定一条 Modbus 请求帧。
2. 对比 PC 串口工具和 Android 上位机收到的原始 RX。
3. 如果差异是固定 `+0x20`，优先怀疑 `IUCLC`。
4. 如果差异是固定清除 `0x80`，优先怀疑 `ISTRIP`。
5. 如果读到一半卡住，检查返回帧是否包含 `0x11/0x13`，并怀疑 `IXON/IXOFF`。
6. 如果 `stty` 执行后状态仍不变，改用 native `termios`。

一句话总结：Modbus RTU 是二进制协议，Android 工控板串口必须是严格 raw/binary 通道，任何 tty 文本处理都会破坏返回帧。
