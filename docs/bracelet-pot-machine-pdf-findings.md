# 智能手环-打锅机接口文档摘录

来源文件：

[智能手环-打锅机 - 接口文档(1).pdf](</D:/Zhuomian/厚德海底捞项目相关/智能手环-打锅机 - 接口文档(1).pdf>)

## 1. 已确认的接口

### 查询设备类型

- 地址：`http://IP:PORT/pot/getDeviceType`
- 方法：`GET`
- `Content-Type`: `x-www-form-urlencoded`

### 根据设备类型开通设备

- 地址：`http://IP:PORT/pot/addDevice`
- 方法：`POST`
- 入参：
  - `deviceTypeCode`
  - `enable`

### 获取在售锅底配方

- 地址：`http://IP:PORT/pot/getFormula`
- 方法：`GET`
- 入参：
  - `deviceCode`：必填
  - `potCode`：选填
  - `potTypeCode`：选填
  - `formulaCode`：选填

### 查询设备

- 地址：`http://IP:PORT/pot/getEquipment`
- 方法：`GET`
- 入参：
  - `deviceTypeCode`

### 修改设备参数

- 地址：`http://IP:PORT/pot/updateEquipmentParam`
- 方法：`POST`
- 入参：
  - `deviceCode`
  - `potCode`
  - `potTypeCode`
  - `addWater`
  - `addChickenOil`
  - `boneOil`

## 2. 配方接口对当前项目最重要的地方

配方接口返回结构里，已经明确给出了锅底和锅型对应的控制字段：

- `formulaCode`
- `formulaName`
- `potCode`
- `potName`
- `potTypeCode`
- `potTypeName`
- `addWater`
- `addChickenOil`
- `bone_oil`
- `materials`
- `basicMaterials`

这意味着当前项目可以按下面的真实链路设计：

`订单接口 -> parentId 还原锅底树 -> potCode + potTypeCode -> 配方接口 -> addWater/addChickenOil/bone_oil -> 控制计划`

## 3. 这份 PDF 帮我们确认了什么

1. 配方接口确实存在，不用再猜
2. 配方接口是 `GET`
3. `deviceCode` 在配方接口里是必填
4. 配方不只是看锅底，还和 `potTypeCode` 有关系
5. 加水、鸡油、骨膏都是以时长参数形式提供
6. 文档里设备参数修改接口的时长单位写的是 `S`，也就是秒

## 4. 对当前代码的直接影响

现在模型里已经有：

1. `PotRecipeProfile`
2. `DispatchStep.durationMs`
3. `PotOrderParser.parse(lines, recipeProfilesByPotBottomId)`

后面接这份 PDF 的真实接口时，需要做两件事：

1. 把接口返回的秒数统一转换成代码里的毫秒，或统一把代码改成秒
2. 用 `potCode + potTypeCode` 去拿正确配方，而不是只按锅底一个维度拿

## 5. 文档里需要再次确认的疑点

这份 PDF 很有帮助，但还有几处要和后端再确认：

1. PDF 写的是 `getFormula`，之前你给的订单接口文档里字段写法有些不统一，需要确认正式命名
2. 配方返回里鸡油字段写的是 `addChickenOil`，骨膏字段写的是 `bone_oil`，命名风格不统一
3. “订单反馈接口”这一页写的地址仍然是 `http://IP:PORT/pot/updateEquipmentParam`
   这大概率是文档笔误，需要后端确认真实反馈地址
4. 订单反馈接口入参只有
   - `deviceCode`
   - `orderId`
   - `doneTime`
   这还不够表达失败、取消、异常，需要确认是否还有状态字段

## 6. 当前建议

下一步最值得确认的是：

1. `getFormula` 的真实返回样例
2. 秒和毫秒的统一口径
3. 是否必须同时带 `potCode` 和 `potTypeCode` 查配方
4. 订单反馈接口的真实地址和字段

## 7. 与真实接口样例对比后的修正

根据你后续提供的真实样例和调用截图，还要修正这些点：

1. PDF 写的是 `GET`，实际截图显示 `POST`
2. PDF 里的骨膏字段写的是 `bone_oil`，真实样例里是 `boneOil`
3. 真实返回里多了 `whetherDefault`
4. 真实返回会同时给出多个 `formulaCode`
5. 终端不能只按 `potCode` 查配方，必须按 `potCode + potTypeCode`
6. 还要再确认多个 `formulaCode` 并存时的最终选用规则

## 8. 当前采用原则

从现在开始，关于锅底配方接口：

1. 以真实调用截图为准
2. 以真实返回字段为准
3. PDF 只保留为历史参考，不再作为优先依据
