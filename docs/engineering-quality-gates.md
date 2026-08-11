# 工程质量门禁

## 本地必跑

提交前执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest jacocoCoreCoverageReport jacocoCoreCoverageVerification
.\gradlew.bat detekt ktlintCheck lintDebug assembleDebug
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-lint-warning-baseline.ps1 -MaximumWarnings 18
```

核心覆盖率门禁只统计订单事件、订单解析、相位规划、命令队列、状态生命周期和 PLC 老化执行器，要求行覆盖率不低于 80%。Compose UI、Android 框架胶水代码和生成代码不纳入该指标，避免用低价值 UI 测试稀释核心风险。

## CI

`.github/workflows/quality.yml` 对主分支提交和所有 PR 执行：

- JVM 单元测试。
- 核心业务 80% 覆盖率校验。
- Detekt、Ktlint 和 Android Lint。
- Debug APK 可复现构建。
- Android 模拟器上的 Room 迁移测试。

`.github/workflows/dependency-review.yml` 阻止引入高危依赖，Dependabot 每周检查 Gradle 和 GitHub Actions 更新。

## 当前基线

- Android Lint：0 error、18 warning。CI 允许已有告警，但禁止告警数量增加。
- Room：数据库版本 2，必须通过真实 `1 -> 2` 迁移验证，禁止使用破坏性迁移兜底。
- Detekt/Ktlint：首次接入的 302 条 Detekt 问题和 4743 条 Ktlint 格式问题已固定在受控 baseline 中；新增问题必须修复，不能在 CI 中动态刷新 baseline。
- 真实 PLC 老化与恢复测试不会进入普通 CI，必须显式运行现场脚本，避免 CI 或模拟器误驱动物理设备。

只有在确认历史问题已经修复后，才允许降低 Lint 上限或重新生成静态检查 baseline。
