# Instructions for Codex (Android & Kotlin Developer)

You are an expert Android Kotlin developer specializing in Coroutines, Flow, Room Database, and Retrofit.
Your task is to implement the Core Data Layer, API Client, and Local Database architecture for a Smart Pot Machine (智能打锅机) based on the rigorous architectural specification provided below.

## Development Tasks:

1. **Retrofit API Client (`PotApiService.kt`)**
   - Implement the endpoints for Device Registration (`pot/getDeviceType` [GET], `pot/getEquipment` [POST]).
   - Implement Recipe Synchronization (`pot/getFormula` [POST], `pot/updateEquipmentParam` [POST]).
   - Implement Order Polling and Feedback (`pos/order/query` [POST], `pot/order/feedback` [POST]).
   - *Note*: Pay attention to `x-www-form-urlencoded` requirements in the specs.

2. **Task Fingerprint Algorithm (`HashUtil.kt`)**
   - Implement the `MD5( ikmsOrder + Root_posFoodCode + ArrayOrder_Child_FoodCodes )` generator.
   - **Crucial**: Ensure `ArrayOrder_Child_FoodCodes` preserves the exact original order of the JSON array. Do not sort them.
   - Append `_0`, `_1` suffix to the generated hash to prevent collisions for identical pots on the same table.

3. **Room Database (`LocalOrderEntity`, `OrderDao`, `RecipeDao`)**
   - Use the generated MD5 sequence hash as the `PrimaryKey`.
   - Create enums for `OrderState`: `PENDING_WATER`, `PENDING_DELIVERY`, `COMPLETED`, `CANCELED`.
   - Implement specialized DAO methods:
     - Cancel order via fuzzy search: `UPDATE orders SET state = 'CANCELED' WHERE id LIKE :baseHash || '%' AND state = 'PENDING_WATER'`
     - Update table code: `UPDATE orders SET tableCode = :newTableCode WHERE id LIKE :baseHash || '%'`

4. **Repository & State Machine (`OrderRepository.kt`, `RecipeRepository.kt`)**
   - Implement the **Two-Key Lookup**: Given a child's `posFoodCode` (mapped to recipe's `potCode`) and root's `posFoodCode` (mapped to recipe's `potTypeCode`), fetch the exact hardware `addWater`, `addChickenOil`, and `boneOil` parameters (which are in seconds `S`).
   - Implement the pausing mechanism: Suspend the 1-minute recipe polling job when `updateEquipmentParam` is being called to prevent overriding local edits.

---

# Architecture Specification

## 1. 核心架构原则
1. **本地状态优先原则 (Local Status Priority)**：上位机仅负责排队与下发，**下位机（PLC）是物理状态唯一真理**。一旦锅底任务进入 `RUNNING`，绝不允许网络旧数据覆盖本地状态。
2. **事件流驱动 (Event Stream Driven)**：API 返回报文基于时间轴的操作日志。同一口锅会因不同操作产生多条关联数据。
3. **弱依赖流水号 (Log-ID Independence)**：严禁使用接口直返的流水 `id` 作为任务主键，必须重建**实体物理指纹**。

## 2. 订单树状重构模型
接口数据为扁平 JSON 数组，需通过 `id` 与 `parentId` 重组为**任务树**：
*   **根节点**：`parentId == null`，定义锅体容量（如：四宫格、拼锅）。
*   **子节点**：指向根节点，决定打入机器的几号工位（如：番茄锅）。
*   **孙节点**：指向子节点，为口味修饰符（如：加麻加辣）。

## 3. 任务指纹生成算法 (MD5)
本地唯一主键 `localTaskId` 算法：
`Base_Hash = MD5( ikmsOrder + Root_posFoodCode + ArrayOrder_Child_FoodCodes )`
*   `ArrayOrder_Child_FoodCodes`：**必须严格保留原始 JSON 数组中的先后顺序进行拼接，绝不允许自行排序**。数组先后顺序直接决定物理机器上的 1、2、3、4 号落位宫格。
*   **同款防重**：若同批数据发现相同的 `Base_Hash`，追加下划线和序号（如 `abc123xyz_0`, `abc123xyz_1`）。

## 4. 关键操作码 (Operation) 处理状态机
*   **301 (下单)**：提取整棵树计算主键。执行 `INSERT OR IGNORE`，初始化为 `PENDING_WATER`。
*   **303 (退单)**：计算退单树 `Base_Hash`，数据库查询 `LIKE '{Base_Hash}_%'`。若状态为 `PENDING_WATER` 则置为 `CANCELED`；若正在制作或已完成，强制触发红灯警报人工干预。
*   **304 (换桌)**：计算 `Base_Hash`，找到匹配任务直接 `UPDATE` 更新 `tableCode` 字段，热刷新界面。

## 5. 核心系统对接接口 (APIs)
### 5.1 机器注册与鉴权 (Device Registration)
两步走（Two-Step）级联注册获取身份 ID：
1. **`GET /pot/getDeviceType`**：实施人员选择机型 `deviceTypeCode`。
2. **`POST /pot/getEquipment`**：入参 `deviceTypeCode`，换取真实机器极长唯一标识 `deviceCode`（永久保存）。

### 5.2 汤底配方与双向同步 (Recipe Sync & Two-Way Mappings)
1. **2D 联合寻址法则 (Two-Key Lookup)**：订单树子节点 `posFoodCode` 对应配方的 `potCode`；根节点 `posFoodCode` 对应配方的 `potTypeCode`。
2. **全量轮询 (Full-Sync Polling)**：每隔 1 分钟通过 `POST /pot/getFormula` (入参 `deviceCode`) 向云端发起全量配方拉取，写入 Room。
3. **物理时间驱动**：接口下发的 `addWater`, `addChickenOil`, `boneOil` 等数值物理单位为**绝对时间(秒S)**，直接透传给 PLC 水泵执行。
4. **后台 UI 与上传防覆盖**：店长可在平板修改加水时间并调用 `POST /pot/updateEquipmentParam` 上传。本地修改并保存时，**立即暂停 1 分钟全量轮询**。云端返回成功后，本地数据库持久化该修改，再恢复轮询。

## 6. 订单业务状态流转与标记
### 6.1 生命周期状态 (Status)
1.  **PENDING_WATER (待加水)**：收到 `301`，尚未下发 PLC。
2.  **PENDING_DELIVERY (待传锅)**：PLC 反馈动作执行完毕，等待服务员端走。
3.  **COMPLETED (已完成)**：传感器检测到锅被端走。**状态变更为已完成时，立刻触发 `POST /pot/order/feedback` (传 `deviceId` 和 `orderId`)，完成 POS 闭环。**
4.  **CANCELED (已取消)**：包含自动取消 (303 触发) 和手动作废。

### 6.2 附属标记 (Flags)
*   **isUrged (催单)**：高亮飘红，提升排队优先级。
*   **isDelayed (延时)**：在屏幕挂起该任务，锁定不可下发 PLC，直至延时结束恢复待加水状态。
