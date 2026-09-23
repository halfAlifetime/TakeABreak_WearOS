# 真机安装与调试记录

## 2026-09-23 结构重构版：振动已通过，最终清理等待恢复连接

本轮按用户提供的 `192.168.0.123:35751` 连接 SM-L310（API 36、480 × 480），在 15:41:47 覆盖安装“休息一下·调试”。原应用未卸载、未清除数据。首次连接和后续测试均遇到无线离线；最后一次重连超时，目前已请求用户恢复无线调试。

| 项目 | 当前记录 |
| --- | --- |
| 验证源码 | b9597c4（P1–P5 重构完成） |
| 包名、版本 | com.takeabreak.wearos.debug；2.2.0 / versionCode 5 |
| 安装时间 | 2026-09-23 15:41:47 |
| APK SHA256 | `4CF7E6966634A66074610F4EC5D7A2E81C9D43770818F42B7E67ADA22FE071C1` |
| 安装包校验 | 手表 base.apk 的 SHA256 与本地产物一致 |
| 本地验证 | 166 项测试通过，构建成功，Lint 0 错误、34 警告 |
| 手动休息振动 | 15:52:09.950，USAGE_ALARM，finished，2451ms；用户确认有震动 |
| 手动工作振动 | 15:52:17.488，USAGE_ALARM，finished，823ms；用户确认有震动 |
| 自动休息提醒 | 15:54:21.273，USAGE_ALARM，finished，2420ms；用户确认有震动 |
| 自动工作提醒 | 15:55:21.212，USAGE_ALARM，finished，816ms；用户确认有震动 |

自动测试使用停止后的独立调试包，临时工作 1 分钟 / 休息 1 分钟。启动后退回表盘并执行 am kill，确认进程不存在，再等待真实阶段闹钟。结果为 WORK 第 1 轮 generation 32 → BREAK 第 1 轮 generation 33 → WORK 第 2 轮 generation 34；没有伪造到点广播或修改系统时间。用户分别对两次手动和两次自动测试回答“两次都有震动”。

其他已验证项：

- 覆盖安装前后 sessionId 及 elapsed/wall 两个截止时间一致；PACKAGE_REPLACED 对账将 generation 27 提升至 28 并重新排程，未重置原会话。
- 暂停视图与继续操作正常；模态显示时点击底层继续按钮位置不改变状态，返回键关闭弹窗后继续成功。运行、暂停和模拟 ERROR 页面已留存截图；错误页长文本按既有两行限制显示。
- 正常模式诊断页显示通知、精确闹钟及提醒配置就绪。PRIORITY 勿扰下显示触感限制，自检被阻止，振动器记录没有新增该包的请求；随后将勿扰恢复为原值 0。
- 在真实第二轮工作状态发送旧会话 STOP，完整状态不变；发送当前会话 STOP 后 generation 35 / STOPPED，通知与闹钟均清空。
- 短周期结束后已恢复 60/5。随后通过设置页按钮改为 45/10，验证两项同时保存，再通过按钮恢复 60/5。

**尚待完成的收尾**：最后的 ERROR 界面测试以正常暂停会话为基础，停止进程后仅在调试包写入 `storage_read` 模拟故障及长文案；这是界面夹具，不是实际存储损坏。错误页显示已确认，但点击重试后即时读取未满足恢复断言，随后无线连接中断，尚未区分操作未送达、异步保存与实际恢复问题。需重连检查并完成停止，清除模拟故障字段、确认无测试闹钟和通知。冷进程通知停止的本轮 UI 检查也被连接中断，仍待完成。

系统设置已恢复：screen_off_timeout 原值 15000（界面操作期间临时设为 120000，自动测试前已恢复）；zen_mode 原值 0（勿扰检查 finally 中已恢复并核验）。工作/休息偏好已恢复为 60/5；由于之后加入 ERROR 夹具，不能把此前短周期清理结果当作全部测试结束后的最终状态。

本轮证据目录：`app/build/reports/device-debug/2026-09-23-refactor/`。包括升级前后状态、安装包信息、manual-vibrations.txt、cycle-work2-vibrations.txt、cycle-vibration-log.txt、vibration-confirmations.json、ui-modal-resume.json、receiver-check.json、dnd-priority-result.json、ui-duration-settings.json、error-fixture.png 和夹具前原始偏好备份。二进制写入仍通过 adb push 和逐字节回读校验，不通过 Windows stdin 传输。所有辅助脚本与证据均位于被忽略的构建目录。

边界：渠道/分组关闭及 LOW/MIN 的设备映射、其他勿扰模式、整机重启、深度休眠和长期耗电未验证；这些限制未被标为通过。旧系统设备与其他厂商叠加触感仍需另行验证。

## 历史：重构前已确认的振动修复

**2026-09-23 振动修复结果：手动自检与正式到点提醒均通过实际触感确认。** 当时修正版于 14:10:30 安装到 SM-L310 的“休息一下·调试”，连接地址 `192.168.0.123:35993`，包名 `com.takeabreak.wearos.debug`。原应用未卸载或清除数据。

定位过程：最初仅依赖通知渠道时，通知虽有 HIGH 优先级和振动模式配置，实际没有触感。先补回手动 Vibrator 自检，14:00 的两次测试都有系统 finished 记录，用户确认“两次都有震动”；但 14:02、14:03 的自动提醒仍没有振动执行记录，用户明确确认“两次都没有震动”。这表明仅修复测试按钮不足以解决正式提醒。

最终实现：手动自检和正式阶段切换共用直接振动路径，指定 USAGE_ALARM，休息节奏总长 2400ms、工作节奏总长 800ms，均不循环。每次重新检查通知权限、目标渠道振动开关和勿扰模式；正式提醒在引擎提交阶段后发出振动并保留通知显示，停止时取消振动。测试通知静默，测试页面显示请求结果和失败原因，未将 API 调用成功等同于物理振动成功。未修改手表全局振动或勿扰设置。

| 历史修复版真机验证 | 结果 |
| --- | --- |
| 14:12:20 自动“该休息了” | 系统记录 USAGE_ALARM，finished，实际执行 2421ms；用户确认有震动 |
| 14:13:21 自动“休息结束” | 系统记录 USAGE_ALARM，finished，实际执行 815ms；用户确认有震动 |
| 自动阶段推进 | WORK 第 1 轮 → BREAK 第 1 轮 → WORK 第 2 轮；没有点击测试按钮或伪造阶段广播 |
| 测试后恢复 | STOPPED，generation 26，工作 60 分钟 / 休息 5 分钟；重新启动界面显示 60:00 和“休息 5分钟” |

测试仍使用独立调试包的 1 分钟工作 / 1 分钟休息，启动后退回表盘并结束后台进程，再等待真实闹钟；没有修改系统时间。用户对该历史版本两次自动提醒的回答是“两次都有震动”。本地 67 项单元测试通过，构建通过，Lint 0 错误、34 警告。

该历史版本 APK SHA256：`B0E7D651DEE9F9C5374693F5227F0EB9EEBABAB376538DC382590A9B23C54651`。构建参数为 `-PisolatedDebug=true`，产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

历史证据：[振动执行记录](C:/php/3/TakeABreak_WearOS/app/build/reports/device-debug/2026-09-23-vibration/automatic-fix-vibrations.txt)、[振动请求日志](C:/php/3/TakeABreak_WearOS/app/build/reports/device-debug/2026-09-23-vibration/automatic-fix-vibration-log.txt)、[最终状态](C:/php/3/TakeABreak_WearOS/app/build/reports/device-debug/2026-09-23-vibration/automatic-fix-final-state.json)、[最终界面](C:/php/3/TakeABreak_WearOS/app/build/reports/device-debug/2026-09-23-vibration/automatic-fix-final-home.png)。证据位于构建目录，清理构建时可能删除。

测试工具补充：恢复时长时发现 Windows 下通过 adb stdin 传输二进制在 0x1A 处截断。已根据停止后的完整状态快照恢复调试包数据，并将辅助脚本改为 adb push、暂存文件逐字节校验、替换后再次校验；冷启动界面和状态读取均正常。此问题发生于测试辅助工具，未将截断文件作为应用故障或遗留在设备上。

边界：本次确认的是该手表正常设置下的短周期振动；勿扰实机分支、整机重启、深度休眠和长时间耗电没有验证。正式通知仍保留渠道振动配置，其他厂商设备是否出现叠加触感需另行验证。

实现参考：[Android Vibrator API](https://developer.android.com/reference/android/os/Vibrator) 支持指定闹钟用途的振动；[NotificationCompat.setSilent](https://developer.android.com/reference/androidx/core/app/NotificationCompat.Builder#setSilent(boolean)) 用于手动自检的静默通知。正式阶段提醒保留原有通知行为，静默设置仅作用于手动自检通知。

以下为首次安装版本的历史调试记录及其 APK 指纹。

2026-09-23，在 Samsung SM-L310（Android 16 / API 36，480 × 480）上完成安装和基本功能验证。

## 安装结果

原有 `com.takeabreak.wearos` 与本机 APK 的签名不同，覆盖安装返回 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。没有卸载原应用或清除其数据；原应用更新时间仍为 2026-09-18 23:34:03。

新增可选构建参数 `-PisolatedDebug=true`，使用独立包名 `com.takeabreak.wearos.debug` 和名称“休息一下·调试”。未传参数时仍使用原包名和名称。调试版 2.2.0 / versionCode 5 已成功安装，已授予其通知权限。

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA/Android/Sdk"
$env:PATH = $env:PATH.Replace('"', '')
./gradlew.bat :app:assembleDebug -PisolatedDebug=true --no-daemon --console=plain
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s '192.168.0.104:39597' install -r app/build/outputs/apk/debug/app-debug.apk
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s '192.168.0.104:39597' shell am start -W -n 'com.takeabreak.wearos.debug/com.takeabreak.wearos.MainActivity'
```

已安装 APK 的 SHA256：`DBE17EE5EB80B682692371D5744F02B2ED8389D1C764F401A1F63E7F042859F9`。后续普通构建会覆盖同一 APK 输出路径，重新安装时应使用上述参数。

## 验证结果

| 场景 | 观察结果 |
| --- | --- |
| 启动与倒计时 | 正常启动，60:00 递减；存储为 RUNNING，系统存在对应精确闹钟 |
| 主页面暂停、继续 | PAUSED 时截止时间清零、剩余时间保存、闹钟撤销；继续后 RUNNING 并重新排程 |
| 停止确认 | 点击弹窗外的底层暂停位置未改变计时；返回键关闭弹窗；确认结束后 STOPPED |
| 通知继续 | 点击系统通知中的“继续”，状态从 PAUSED 变为 RUNNING |
| 通知暂停接收器 | 以调试应用自身 UID 发送生产 PAUSE action，状态变为 PAUSED，通知显示继续和停止 |
| 冷进程通知停止 | 应用进程不存在时点击系统通知“停止”，重新创建进程并最终提交 STOPPED；停止记录已落入 SharedPreferences，通知消失 |
| 运行中进程退出后返回 | 表盘持续活动入口重新打开主页面，保留原会话与剩余计时 |
| 真实阶段闹钟 | 进程退出后，系统闹钟启动应用，WORK 第 1 轮进入 BREAK 第 1 轮，再进入 WORK 第 2 轮 |
| 阶段通知替换 | 每次观察到状态通知和最新一条阶段提醒；旧阶段提醒未继续保留 |
| 旧会话停止请求 | 发送上一会话 STOP，当前新会话完整状态保持不变；有效 STOP 则取消计时 |
| 停止后清理 | 调试包的活动闹钟、通知均为空 |
| 日志和进程退出记录 | 所检查应用日志未发现 FATAL EXCEPTION 或 ANR；退出记录均为本次测试主动结束后台进程或 force-stop |

阶段测试临时将独立调试包的工作和休息时长各设为 1 分钟，实际等待系统闹钟触发，没有手动发送阶段闹钟广播，也未更改系统时间。配置注入在进程停止、计时状态为 STOPPED 时进行。测试后已恢复 60 分钟工作 / 5 分钟休息，重新启动页面显示 60:00、休息 5 分钟，最终状态为 STOPPED。

阶段日志示例：11:51:19 开始工作；11:52:21 进入休息；11:53:21 进入第 2 轮工作。通知记录显示 HIGH 优先级、启用的振动模式及全屏 PendingIntent。它们证明系统收到这些配置，不能单独证明佩戴时的振动体感或锁屏亮屏效果。

冷进程通知停止的首次 1 秒采样早于异步提交，后续采样确认停止成功；辅助脚本已改为有界轮询，避免把启动延迟误判为失败。

## 证据与边界

本次证据保存在 [设备调试目录](C:/php/3/TakeABreak_WearOS/app/build/reports/device-debug/2026-09-23)：包括截图、UI 层级、计时状态快照、应用日志、进程退出记录和通知详情。该目录位于构建输出中，清理构建时可能被删除。

- [最终界面](C:/php/3/TakeABreak_WearOS/app/build/reports/device-debug/2026-09-23/final-home.png)
- [最终状态](C:/php/3/TakeABreak_WearOS/app/build/reports/device-debug/2026-09-23/final-state.json)
- [旧通知隔离及停止清理](C:/php/3/TakeABreak_WearOS/app/build/reports/device-debug/2026-09-23/receiver-check.json)
- [冷进程停止后的状态](C:/php/3/TakeABreak_WearOS/app/build/reports/device-debug/2026-09-23/cold-stop-settled-state.json)
- [进程退出记录](C:/php/3/TakeABreak_WearOS/app/build/reports/device-debug/2026-09-23/process-exits.txt)

未进行手表整机重启、深度休眠长测、存储读写故障注入或持续耗电测量；通知暂停通过真实接收器验证，未通过系统界面点击暂停按钮。真实振动体感、勿扰模式与锁屏强制亮屏仍需进一步验证。本轮没有发现需要修改计时业务逻辑的新缺陷，源码变更仅增加可选的独立调试安装配置。
