# 接单到 PLC 执行流程图

关联文档：
[architecture.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/architecture.md)
[control-routing.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/control-routing.md)
[plc-command-handshake-draft.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/plc-command-handshake-draft.md)
[plc-phase-field-draft.md](/D:/Users/achillesniu/Documents/自研打锅机项目/docs/plc-phase-field-draft.md)

## 1. 主流程

```mermaid
flowchart TD
    A["HTTP 接单"] --> B["按 parentId 还原订单树"]
    B --> C["解析成设备视角锅位任务<br/>单锅 / 拼锅 / 四宫格"]
    C --> D["按 potCode + potTypeCode 查询锅底配方"]
    D --> E["得到每个锅位的 addWater / addChickenOil / boneOil"]
    E --> F["判断哪些锅位需要左上辅料工位"]
    F --> G{"是否存在<br/>需要辅料但不在左上的锅位?"}
    G -- "否" --> I["生成第 1 相位"]
    G -- "是" --> H["优先先把其中一个转到左上"]
    H --> I
    I --> J["UI 展示当前锅位计划与相位信息"]
    J --> K["安卓下发本相位参数到 PLC"]
    K --> L["PLC 接收并锁存相位命令"]
    L --> M["统一启动本相位允许的加水路"]
    M --> N["各路按各自配方时间独立停止"]
    N --> O["当前左上工位执行鸡油 / 骨膏"]
    O --> P["PLC 回写 accepted / executing / finished / result"]
    P --> Q{"本锅任务是否全部完成?"}
    Q -- "是" --> R["订单完成 / 回传结果"]
    Q -- "否" --> S{"下一相位是否需要人工转锅?"}
    S -- "否" --> T["生成下一相位"]
    S -- "是" --> U["弹窗提示人工转锅"]
    U --> V["人工确认转锅完成"]
    V --> T
    T --> J
```

## 2. 相位生成规则

```mermaid
flowchart TD
    A["开始生成相位"] --> B["检查每个锅位是否需要辅料"]
    B --> C{"是否有锅位<br/>需要鸡油或骨膏?"}
    C -- "没有" --> D["按锅位生成加水参数"]
    C -- "有" --> E["确定当前左上优先锅位"]
    E --> F["只有当前左上优先锅位<br/>允许在本相位执行辅料"]
    F --> G["其他也需要辅料的锅位<br/>水和料都延后到后续相位"]
    G --> D
    D --> H["生成 waterDuration1~4"]
    H --> I["生成 activeTopLeftLogicalSlot"]
    I --> J["生成 chickenOilDuration / bonePasteDuration"]
    J --> K["得到本相位字段包"]
```

## 3. PLC 执行规则

```mermaid
flowchart TD
    A["PLC 收到 commandId"] --> B["校验参数并回写 acceptedCommandId"]
    B --> C["回写 executingCommandId"]
    C --> D["同时打开所有 waterDuration > 0 的水路"]
    D --> E["每一路独立计时"]
    E --> F["到各自时长后分别关闭"]
    F --> G{"当前相位左上工位<br/>是否需要鸡油/骨膏?"}
    G -- "否" --> J["等待本相位所有动作结束"]
    G -- "是" --> H["执行 chickenOilDuration"]
    H --> I["执行 bonePasteDuration"]
    I --> J
    J --> K["回写 finishedCommandId"]
    K --> L["回写 plcActionState / resultCode"]
```

## 4. 当前最关键的现场规则

1. 加水是统一启动，不是统一停止。
2. 每个锅位的加水停止时间必须按自己的锅底配方独立控制。
3. 只要存在需要辅料但当前不在左上的锅位，就应优先先把其中一个转到左上。
4. 非左上且也需要辅料的锅位，水和料都应延后到后续相位。
5. 鸡油和骨膏只属于当前左上工位对应的那个逻辑锅位。
6. 人工转锅是安卓流程控制步骤，不是 PLC 自动旋转动作。
