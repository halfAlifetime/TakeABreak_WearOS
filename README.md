# 休息一下 / Take a Break — Wear OS

独立手表循环计时应用，默认工作 60 分钟、休息 5 分钟。工作结束进入同轮休息，休息结束进入下一轮工作，直至用户停止。

工程：`C:/php/3/TakeABreak_WearOS`。版本：`2.2.0` / `versionCode 5`。本轮完成本地重构并已安装新版；手动及两次自动振动均获用户触感确认。最后的错误界面夹具清理与通知冷停止检查因无线连接中断待完成。

## 设计与模块职责

遵循 [AGENTS.md](./AGENTS.md) 的 Codex 可读性和功能模块化原则。保留单个 `app` Gradle 模块，通过构造参数和必要接口组装依赖。

主代码位于 `app/src/main/java/com/takeabreak/wearos`：

| 入口或文件 | 职责 |
| --- | --- |
| `TakeABreakApplication.kt` | 组装 Android 实现、计时引擎和 ViewModel Factory |
| `MainActivity.kt` | 生命周期、导航和系统设置跳转 |
| `timer/TimerEngine.kt` | 唯一计时修改入口，负责锁、停止保护和副作用提交顺序 |
| `timer/TimerTransitions.kt`、`TimerRecoveryPolicy.kt` | 无平台副作用的阶段、继续和恢复时间规则 |
| `timer/TimerFailure.kt`、`TimerSystemEvent.kt` | 明确的故障和系统事件类型 |
| `timer/TimerRepository.kt`、`DataStoreTimerRepository.kt` | 仓库接口与 DataStore 字段读写 |
| `timer/StopIntentStore.kt`、`SharedPreferencesStopIntentStore.kt` | 独立停止记录与会话恢复许可 |
| `timer/ClockProvider.kt`、`SystemClockProvider.kt` | 时钟接口与 Android 时钟实现 |
| `alarm/` | 阶段闹钟调度、到点广播与系统事件映射 |
| `permission/ReminderCapabilityReader.kt`、`AndroidReminderCapabilityReader.kt` | 能力读取接口与系统快照采集 |
| `permission/ReminderCapabilities.kt`、`ReminderPolicy.kt` | 结构化能力、问题代码和统一用途策略 |
| `permission/ReminderMessages.kt`、`ReminderPermissionChecker.kt`、`ReminderSettingsNavigator.kt` | 诊断文案、命令检查适配、设置 Intent |
| `notification/ReminderNotifier.kt`、`AndroidReminderNotifier.kt` | 正式通知接口与 Android 发布、清理实现 |
| `notification/ReminderVibration.kt`、`AndroidReminderVibration.kt` | 正式提醒和自检共用的振动请求 |
| `notification/ReminderSelfTest.kt` | 独立手动自检入口及结果，计时引擎不依赖此接口 |
| `notification/NotificationActions.kt`、`NotificationActionReceiver.kt`、`NotificationChannels.kt` | 通知协议、会话动作入口和渠道创建 |
| `ui/TimerViewModel.kt`、`TimerViewModelFactory.kt` | 显式注入引擎、时钟、能力读取器、自检入口；对外只读状态 |
| `ui/TimerScreen.kt`、`TimerContent.kt` | 页面组合与四种状态内容 |
| `ui/TimerWaterBackground.kt`、`TimerControls.kt`、`TimerDialogs.kt` | 水波、按钮、类型化反馈与模态停止确认 |
| `ui/SettingsScreen.kt`、`ReminderStatusScreen.kt`、`ActionFeedback.kt` | 时长设置、能力诊断及自检反馈 |

测试位于 `app/src/test/java/com/takeabreak/wearos`。共用 Fake 在 `timer/support/TimerFakes.kt`，能力夹具在 `permission/support/CapabilityFixtures.kt`；ViewModel 行为测试不需要真实 Application。

## 必须保留的行为

- 系统到点由 `AlarmManager.setAlarmClock()` 驱动。UI 的 500ms ticker 只刷新显示，页面不可见时停止；水波仅在 RUNNING 且页面 RESUMED 时持续动画。
- 所有计时修改经过同一把 Mutex。等待锁可取消，进入副作用事务后在 IO 和 NonCancellable 边界内完成提交或补偿。阶段提交成功后才发送正式振动。
- 每个会话使用 UUID 和递增 generation。通知通过会话 URI 和参数隔离；旧通知、晚到广播不能重启已停止会话或停止新会话。
- 独立停止记录优先于旧 RUNNING 快照。主状态与停止记录都写失败时返回失败，不能承诺跨进程保存停止意图。
- 同次开机使用 elapsedRealtime，真实重启才按墙钟恢复；过期阶段暂停等待确认，不追赶全部错过的阶段。
- UI 只提交改变的时长字段。引擎获锁后合并最新状态，仅允许 STOPPED 修改；保存失败不发布未保存值。
- Ongoing Activity 只在 RUNNING 状态通知中附加。表盘入口或通知点击仅导航，不重新开始、恢复或重置计时。

## 提醒策略与数据兼容

诊断、UI/通知继续的前置检查和每次直接振动共用 `ReminderPolicy`。能力读取失败使用未知值，不默认允许。

| 条件 | 计时命令 | 直接振动 |
| --- | --- | --- |
| 精确闹钟权限缺失 | 阻止 | 单独自检仍可执行 |
| 通知未授权，必要阶段渠道关闭、振动关闭或分组关闭 | 阻止 | 按当前目标渠道判断 |
| 渠道 LOW/MIN，其他配置允许 | 允许并提示触感限制 | 阻止 |
| 渠道 DEFAULT/HIGH，其他配置允许 | 允许 | 允许 |
| 勿扰 ALARMS | 允许，说明弹屏限制 | 允许 ALARM 用途请求 |
| 勿扰 PRIORITY/NONE 或无振动器 | 允许并提示触感限制 | 阻止 |
| 必要能力未知 | 返回不可验证原因 | 阻止无法确认许可的请求 |

工作振动节奏为 `[0, 300, 200, 300]`，休息为 `[0, 600, 200, 600, 200, 800]`，均指定 USAGE_ALARM、不循环。正式提醒保留通知渠道与全屏配置；手动自检通知静默。请求成功只说明 API 请求已发出，触感仍需佩戴者确认。停止全部通知时取消振动；按旧会话定向清理不会取消新会话的振动。

弹屏单独判断渠道重要性、全屏许可和勿扰状态，最终展示由系统决定。其他设备是否叠加渠道振动、睡眠模式和耗电影响仍需设备验证。

DataStore 名称、既有键、停止记录和通知协议保持兼容。新增 `failure_reason` 使用稳定字符串 `storage_read`、`storage_write`、`alarm_scheduling`、`unknown`。旧 ERROR 缺少代码或含未知代码时按 UNKNOWN 处理，可手动重试；仅明确的 ALARM_SCHEDULING 故障在精确闹钟权限恢复事件下自动重试。非 ERROR 忽略残留故障代码，成功恢复或停止清除故障。

## 构建与验证

配置仍为 Gradle 8.9、AGP 8.6.0、Kotlin 2.0.21、JVM target 17，minSdk 30 / targetSdk 34 / compileSdk 35。依赖版本沿用 `app/build.gradle.kts`，未新增框架或 Gradle 模块。

本机 PowerShell 验证命令：

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA/Android/Sdk"
$env:PATH = $env:PATH.Replace('"', '')
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug -PisolatedDebug=true --no-daemon --console=plain
```

仅设置当前命令会话。原 `local.properties` 的 SDK 路径在本机不可用，使用 ANDROID_HOME 构建；未修改系统环境。

`-PisolatedDebug=true` 产出 `com.takeabreak.wearos.debug` / “休息一下·调试”，与原应用并存。APK：`app/build/outputs/apk/debug/app-debug.apk`；不传参数则使用原包名。构建产物不进入 Git。

最终本地验证：166 项测试通过（0 失败、0 错误、0 跳过），构建成功，Lint 0 错误、34 警告。阶段提交见 [实施记录](./IMPLEMENTATION_PLAN.md)。测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`；Lint：`app/build/reports/lint-results-debug.html`。这些文件可能被后续构建覆盖或清理。

新增验证包括策略矩阵、确定性时长竞态、纯恢复规则、真实文件 DataStore 的保存关闭重开，以及 ViewModel 行为。原 67 项恢复、取消、会话隔离和振动回归保留；自检测试从 `ReminderVibrationTestTest` 更名为 `ReminderSelfTestBehaviorTest`，未删除原断言。

新版已验证手动及真实后台两次自动振动、升级保留会话、PRIORITY 勿扰阻断、暂停/继续、模态输入隔离、旧会话停止隔离与时长设置。最后的 ERROR 界面夹具、最终停止清理及通知冷停止检查因无线连接中断待完成。其余未覆盖条件及证据见 [设备记录](./DEVICE_DEBUG_REPORT.md)；本轮状态与历史分析分开记录于 [程序分析](./PROGRAM_ANALYSIS.md)。
