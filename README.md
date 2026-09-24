# 休息一下 / Take a Break — Wear OS

独立手表循环计时应用，默认工作 60 分钟、休息 5 分钟。工作结束进入同轮休息，休息结束进入下一轮工作，直至用户停止。

工程：`C:/php/3/TakeABreak_WearOS`。版本：`2.2.0` / `versionCode 5`。当前源码已修正通知结束时间使用旧时区的问题，保留此前取消传播、恢复保存失败及模块职责整理。当前改动尚未安装到手表；此前 `b9597c4` 版本的手动及两次自动振动已获用户触感确认，详见设备记录。

## 设计与模块职责

遵循 [AGENTS.md](./AGENTS.md) 的 Codex 可读性和功能模块化原则。保留单个 `app` Gradle 模块，通过构造参数和必要接口组装依赖。

主代码位于 `app/src/main/java/com/takeabreak/wearos`：

| 入口或文件 | 职责 |
| --- | --- |
| `TakeABreakApplication.kt` | 组装 Android 实现、计时引擎和 ViewModel Factory |
| `MainActivity.kt` | 生命周期、导航和平台设置入口接线；在当前页面上方展示设置跳转失败 |
| `timer/TimerEngine.kt` | 唯一计时修改入口，负责锁、停止保护和副作用提交顺序；两条系统恢复路径共用保存失败处理 |
| `timer/TimerSystemEffects.kt` | 统一包含闹钟和通知调用异常；调度失败交回引擎故障分支，清理/发布失败记录日志并继续状态提交 |
| `timer/TimerTransitions.kt`、`TimerRecoveryPolicy.kt` | 无平台副作用的阶段、继续和恢复时间规则 |
| `timer/TimerFailure.kt`、`TimerSystemEvent.kt`、`TimerCommand.kt` | 明确的故障、系统事件和会话命令类型 |
| `timer/TimerRepository.kt`、`DataStoreTimerRepository.kt` | 仓库接口与 DataStore 字段读写 |
| `timer/StopIntentStore.kt`、`SharedPreferencesStopIntentStore.kt` | 独立停止记录与会话恢复许可 |
| `timer/ClockProvider.kt`、`SystemClockProvider.kt` | 时钟接口与 Android 时钟实现 |
| `alarm/AlarmScheduler.kt`、`AndroidAlarmScheduler.kt` | 阶段闹钟调度接口与 Android 实现；同目录接收器负责到点广播与系统事件映射 |
| `permission/ReminderCapabilityReader.kt`、`AndroidReminderCapabilityReader.kt` | 能力读取接口与系统快照采集 |
| `permission/ReminderCapabilities.kt`、`ReminderPolicy.kt` | 结构化能力、问题代码和统一用途策略 |
| `permission/ReminderMessages.kt`、`ReminderPermissionChecker.kt`、`ReminderSettingsNavigator.kt` | 诊断文案、命令检查适配、统一构建和启动设置 Intent 并返回结果 |
| `notification/ReminderNotifier.kt`、`AndroidReminderNotifier.kt` | 正式通知接口与 Android 发布、清理实现 |
| `notification/NotificationTimeFormatter.kt` | 通知截止时间文本；每次调用读取当前时区和语言，也可显式传入格式化环境 |
| `notification/ReminderVibration.kt`、`AndroidReminderVibration.kt` | 正式提醒和自检共用的振动请求 |
| `notification/ReminderSelfTest.kt` | 独立手动自检入口及结果，计时引擎不依赖此接口 |
| `notification/NotificationActions.kt`、`NotificationCommandHandler.kt`、`NotificationActionReceiver.kt`、`NotificationChannels.kt` | 稳定通知协议、字符串解码、广播入口和渠道创建；会话校验及继续条件检查仍在引擎锁内 |
| `ui/TimerViewModel.kt`、`TimerViewModelFactory.kt` | 显式注入依赖；null 表示加载中；计时命令共用异常转换入口，取消继续传播 |
| `ui/TimerScreen.kt`、`TimerContent.kt` | 页面组合与四种状态内容 |
| `ui/TimerWaterBackground.kt`、`TimerControls.kt`、`TimerDialogs.kt` | 水波、按钮、类型化反馈与模态停止确认 |
| `ui/SettingsScreen.kt`、`ReminderStatusScreen.kt`、`ActionFeedback.kt` | 时长设置及保存错误、能力诊断及自检反馈；反馈按操作来源分发、按 ID 关闭 |

测试位于 `app/src/test/java/com/takeabreak/wearos`。共用 Fake 在 `timer/support/TimerFakes.kt`，能力夹具在 `permission/support/CapabilityFixtures.kt`；ViewModel 行为测试不需要真实 Application。

## 必须保留的行为

- 系统到点由 `AlarmManager.setAlarmClock()` 驱动。UI 的 500ms ticker 只刷新显示，页面不可见时停止；水波仅在 RUNNING 且页面 RESUMED 时持续动画。
- 主界面通过 `TimerState.formattedRemainingTime` 生成倒计时文本，复用统一的剩余时间计算与向上取整规则。
- 运行通知的“预计结束时间”通过 `formatNotificationDeadline` 生成，每次调用使用当前时区和语言，避免长期缓存默认时区。截止时间无效时仍显示 `--:--`；时区广播继续通过既有系统对账路径重新发布通知。
- 所有计时修改经过同一把 Mutex。等待锁可取消，进入副作用事务后在 IO 和 NonCancellable 边界内完成提交或补偿。阶段提交成功后才发送正式振动。
- ViewModel 的计时命令只将普通异常转为操作失败；直接抛出或 Result 携带的 CancellationException 都继续传播。取消不生成失败提示，也不覆盖期间的新反馈，已进入的引擎事务仍完成提交或补偿。
- 闹钟调度异常统一进入 ALARM_SCHEDULING 故障分支；通知发布或清理失败不能阻断计时状态提交。系统异常记录操作与相关会话/代次，协程取消异常继续向上传播。
- Android 闹钟适配器将意外排程异常交给 TimerSystemEffects，日志保留原始异常、会话与代次；AlarmClock 安全异常后的备用排程若也失败，会将首次异常附在备用异常中。入口检查发现能力不可用或截止时间已过时仍返回 false。
- 停止先发布 STOPPED、废弃旧会话，再尽力清理系统闹钟和通知并保存状态。清理失败可能留下系统对象，但旧会话与旧代次不能继续推进计时；不把清理尝试描述为系统一定已清空。
- 每个会话使用 UUID 和递增 generation。通知通过会话 URI 和参数隔离；旧通知、晚到广播不能重启已停止会话或停止新会话。
- 独立停止记录优先于旧 RUNNING 快照。主状态与停止记录都写失败时返回失败，不能承诺跨进程保存停止意图。
- 同次开机使用 elapsedRealtime，真实重启才按墙钟恢复；过期阶段暂停等待确认，不追赶全部错过的阶段。
- 系统恢复的未到期和已到期分支共用保存失败处理：进入 ERROR / STORAGE_WRITE，记录原始异常、会话和代次，并尝试一次故障快照补偿保存。到期故障保留剩余时间 0，显式重试进入下一阶段；精确闹钟权限恢复不自动重试存储故障。
- UI 只提交改变的时长字段。引擎获锁后合并最新状态，仅允许 STOPPED 修改；保存失败不发布未保存值。
- ViewModel 维护最新一条带来源和递增 ID 的反馈。时长保存失败在设置页显示并滚动定位，保存成功仅清除对应字段的错误；计时反馈与自检反馈分别由计时页和诊断页展示。关闭反馈按 ID 匹配，“去设置”先关闭原提示再跳转，跳转失败的新提示得以保留。
- 停止命令在等待前记录原有计时反馈的 ID，成功后只消费该条反馈，保留等待期间的新提示和已有自检提示。所有设置入口通过 ReminderSettingsNavigator.open 执行；构建或启动失败返回结果并记录日志，SETTINGS_NAVIGATION 反馈在当前页面上方使用共用弹窗显示，不受各页来源过滤影响。
- Android 通知清理捕获普通异常后写入 `ReminderNotifier` 日志，定向清理包含会话 ID；历史提醒清理失败后仍尝试新阶段的振动和通知，取消异常继续传播。
- 首次加载显示“正在读取计时…”且不展示操作按钮，不再用默认 STOPPED 占位。开始命令不能覆盖已知 PAUSED/ERROR；RUNNING 保持幂等。读取失败时，仅此前已有明确通知停止请求的恢复路径允许显式开始新会话，保留旧停止保护行为。
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

本次本地验证：201 项测试通过（0 失败、0 错误、0 跳过），调试 APK 构建成功，Lint 0 错误、34 警告，警告分类与此前一致。最新验证日志为 `app/build/principles-review-20260924-3/fix-validation.log`。阶段提交及后续修正见 [实施记录](./IMPLEMENTATION_PLAN.md)。测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`；Lint：`app/build/reports/lint-results-debug.html`。这些文件可能被后续构建覆盖或清理。

新增验证包括策略矩阵、确定性时长竞态、纯恢复规则、真实文件 DataStore 的保存关闭重开，以及 ViewModel 行为。原 67 项恢复、取消、会话隔离和振动回归保留；自检测试从 `ReminderVibrationTestTest` 更名为 `ReminderSelfTestBehaviorTest`，未删除原断言。

此前反馈与异常处理修正保留全部 181 项测试，新增 9 项：ViewModel 的停止反馈隔离 3 项、设置跳转结果与取消行为 4 项，以及 TimerSystemEffects 的原异常日志与取消传播 2 项。延迟停止清空新自检反馈曾在修复前的隔离用例中失败，证据为 `app/build/principles-review-20260924/reproduction.log`；该场景现已纳入正式回归。随后格式化复用和文件拆分沿用这 190 项测试，Android 闹钟实现类正文的移动前后校验一致，未为文件搬移新增镜像测试。

上一轮保留全部 190 项测试，新增取消传播 4 项和恢复保存失败 5 项。覆盖七种 UI 命令等待锁时取消、提交后的取消、Result 携带取消、故障快照保存与引擎重建、剩余时间保留、显式重试及补偿取消。修复前三个隔离用例失败的证据保留在 `app/build/principles-review-20260924-2/reproduction.log`。

本轮保留全部 199 项测试，新增通知时间格式化 2 项，覆盖同进程连续切换时区、跨日显示和无效截止时间占位。旧格式化器保留旧时区的 JVM 复现证据为 `app/build/principles-review-20260924-3/timezone-reproduction.log`。系统时区广播到通知发布的调用关系已核查，手表实际切换时区尚未验证。

恢复故障快照补偿保存成功时，重建仍为 ERROR；若全部写入持续失败，故障仅保留在当前进程内，重建仍可能读取旧 RUNNING 快照。已验证后续系统事件可再次对账，不能将其描述为“仅重新打开应用即可恢复未落盘故障”。这与成功保存后的重建测试分开记录。

设置结果测试通过注入的平台回调执行，日志测试通过系统端口 Fake 执行；真实 Android 设置 Intent 与排程备用分支经过代码检查、编译及 Lint，未注入真实系统服务故障。设置失败弹窗及此前设置错误卡片的圆屏外观尚未在设备验证。

此前 `b9597c4` 已验证手动及真实后台两次自动振动、升级保留会话、PRIORITY 勿扰阻断、暂停/继续、模态输入隔离、旧会话停止隔离与时长设置。最后的 ERROR 界面夹具、最终停止清理及通知冷停止检查因无线连接中断未获完整记录。本次没有连接设备，不能将上述历史结果当作当前修复版本的真机验收。其他未覆盖条件及证据见 [设备记录](./DEVICE_DEBUG_REPORT.md)；当前状态与历史分析分开记录于 [程序分析](./PROGRAM_ANALYSIS.md)。
