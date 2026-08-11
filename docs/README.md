# 文档统一入口

当前项目目录：`D:/Users/achillesniu/Documents/pot_machine_project`

本目录里保留了不少历史草案。后续查文档时，优先按下面顺序看，避免被早期草案带偏。

## 1. 当前工程入口

1. [engineering-refactor-map.md](D:/Users/achillesniu/Documents/pot_machine_project/docs/engineering-refactor-map.md)
2. [project-issues-for-codex.md](D:/Users/achillesniu/Documents/pot_machine_project/docs/project-issues-for-codex.md)
3. [field-validation-checklist.md](D:/Users/achillesniu/Documents/pot_machine_project/docs/field-validation-checklist.md)
4. [order-persistence-room-migration-plan.md](D:/Users/achillesniu/Documents/pot_machine_project/docs/order-persistence-room-migration-plan.md)
5. [engineering-quality-gates.md](engineering-quality-gates.md)
6. [plc-aging-test-guide.md](plc-aging-test-guide.md)
7. [plc-automated-recovery-test-guide.md](plc-automated-recovery-test-guide.md)

用途：

1. 判断当前代码重构完成到哪里。
2. 判断下一步该做现场验证、代码收口还是文档清理。
3. 区分“代码编译/单测通过”和“真实 PLC 现场验证通过”。
4. 判断是否需要把本地订单存储迁移到 Room/SQLite，以及迁移后如何做订单/PLC 审计。
5. 执行 CI、覆盖率、静态检查、Room 迁移和真实 PLC 老化/恢复验证。

## 2. PLC / 控制器正式基线

1. [plc-final-formal-spec.md](D:/Users/achillesniu/Documents/pot_machine_project/docs/plc-final-formal-spec.md)
2. [plc-register-map-baseline.md](D:/Users/achillesniu/Documents/pot_machine_project/docs/plc-register-map-baseline.md)
3. [ladder-logic-sketch-generic.md](D:/Users/achillesniu/Documents/pot_machine_project/docs/ladder-logic-sketch-generic.md)
4. [ladder-logic-program-block-checklist.md](D:/Users/achillesniu/Documents/pot_machine_project/docs/ladder-logic-program-block-checklist.md)

规则：

1. PLC 地址、协议、变量定义、控制边界优先以正式基线为准。
2. 如果草案文档与正式基线冲突，以最后确认的 formal/spec/sketch 为准。
3. 梯形图实现时优先查 `ladder-logic-sketch-generic.md` 和程序段清单。

## 3. 安卓上位机实施参考

1. [android-embedded-board-implementation-boundary.md](D:/Users/achillesniu/Documents/pot_machine_project/docs/android-embedded-board-implementation-boundary.md)
2. [android-upper-host-modbus-plan.md](D:/Users/achillesniu/Documents/pot_machine_project/docs/android-upper-host-modbus-plan.md)
3. [android-upper-host-smoke-test.md](D:/Users/achillesniu/Documents/pot_machine_project/docs/android-upper-host-smoke-test.md)
4. [pos_order_integration_design.md](D:/Users/achillesniu/Documents/pot_machine_project/docs/pos_order_integration_design.md)
5. [modbus-crc-iuclc-troubleshooting.md](D:/Users/achillesniu/Documents/pot_machine_project/docs/modbus-crc-iuclc-troubleshooting.md)

对外沟通可直接使用 Word 版：

1. [android-embedded-board-implementation-boundary.docx](D:/Users/achillesniu/Documents/pot_machine_project/docs/android-embedded-board-implementation-boundary.docx)

用途：

1. 明确从当前 PLC 方案迁移到嵌入式控制板时，哪些业务规则和安全边界必须保留。
2. 明确 Android 上位机、嵌入式控制板、命令模型、状态回传、故障码和最小联调验收项。
3. 明确 POS 订单解析、锅型/锅底完整性校验、催单/退单/转台生命周期规则。
4. 回查 Modbus、串口和 IUCLC 导致 CRC 异常的现场问题。

## 4. 历史与原始资料

以下内容只用于回查，不作为当前实施直接依据：

1. [order-api.docx](D:/Users/achillesniu/Documents/pot_machine_project/docs/order-api.docx)
2. 早期 `draft` / `review` / `notes` / `baseline` 文档。

如果历史文档与当前入口冲突，优先相信当前入口和最后现场验证结果。
