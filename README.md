# 休息一下 (Take a Break) - Wear OS 原生工程

专为 Samsung Galaxy Watch 7 等 Wear OS 手表设计的纯原生循环计时与系统通知振动提醒应用。
当前版本：`v2.2.0` (versionCode 5)

---

## 一、架构设计核心原则

1. **系统级调度与单次阶段闹钟**
   - 不采用容易被系统冻结的前台常驻服务循环，不使用 `CountDownTimer`、`Handler` 循环或协程 `delay` 作为后台到点依据。
   - 使用 `AlarmManager.setAlarmClock()` 配合显式 `PendingIntent`，注册一次性阶段结束闹钟。
   - 触发时间采用绝对 Wall Clock (`System.currentTimeMillis() + remainingMs`)，内部倒计时与差值计算严格基于单调开机时钟 `SystemClock.elapsedRealtime()`。

2. **Wear OS Ongoing Activity 表盘小图标集成与安全导航**
   - 原生集成 `androidx.wear:wear-ongoing:1.1.0`，在计时运行与暂停期间，在状态常驻通知上挂载 `OngoingActivity`。
   - 在支持 Wear OS Ongoing 规范的表盘（如 Galaxy Watch 7）底部常驻动态小图标。
   - 点击表盘图标触发 `MainActivity.ACTION_OPEN_TIMER`（`singleTop` / `FLAG_ACTIVITY_SINGLE_TOP`），支持初次启动与复用 Activity，真正返回当前计时主界面，绝不重新开始、恢复或重置计时。
   - 在每次阶段切换、对账更新状态通知时始终保留 Ongoing Activity 信息。

3. **统一提醒能力检查与主界面直达设置**
   - 统一“开始”、“继续”、“重试”以及状态通知“继续”的前置提醒能力校验（包含精确闹钟权限、系统通知总开关、工作与休息振动渠道启用状态）。
   - 主计时界面直接显示阻断原因及针对性直达设置入口（精确闹钟、应用通知、工作/休息/状态通知渠道），无需深入二级菜单即可排查修复。

4. **存储异常重试、明确故障状态发布与防伪重启防护**
   - 针对 DataStore 持久化引入退避重试（30ms 间隔），若发生极端 IO 异常则安全回滚并撤销系统闹钟，发布同进程权威 ERROR 故障状态，绝不留下“仓库显示 RUNNING，实际没有系统闹钟”的幽灵状态。
   - 用户主动点停止后，内存立即标记停止意图并记录废弃会话，即便极端存储写入失败，后续也绝不以系统对账或晚到广播为由偷偷重启计时。
   - 系统事件对账（TIME_SET、PACKAGE_REPLACED）非关机场景严格使用单调 `elapsedRealtime`，避免系统时间微调导致未到期阶段被误判为过期。
   - `NotificationActionReceiver` 彻底不直接操作 `TimerRepository`，所有会话匹配、前置能力校验与状态转移统一步入 `TimerEngine` 串行执行。

5. **系统通知渠道执行原生振动**
   - 正式提醒全权交由高优先级系统通知渠道 (`NotificationManager.IMPORTANCE_HIGH`)，配置独立的定制硬件振动节奏：
     - **休息开始** (`break_start_v2`)：`longArrayOf(0, 600, 200, 600, 200, 800)`
     - **工作开始** (`work_start_v2`)：`longArrayOf(0, 300, 200, 300)`
     - **计时状态** (`timer_status_v2`)：`IMPORTANCE_LOW`，无声音无振动，常驻状态卡片挂载 Ongoing Activity
     - **计时异常** (`timer_error_v2`)：独立告警渠道
   - 杜绝“同时发通知又调手写 Vibrator”的冲突逻辑，遵循 Wear OS 原生通知规范。

6. **单状态机并发防护与幂等性**
   - 所有的 UI 点击、通知动作广播、系统闹钟回调以及重启对账，统一进入 `TimerEngine` 经 `Mutex` 串行处理。
   - 区分内部非加锁执行与对外互斥锁入口，彻底消除权限恢复重试时的递归死锁。
   - 每个运行阶段携带 `sessionId` 和递增的 `generation`。废弃事件、过时广播、在途重复信号一律安全忽略。
   - 闹钟注册与持久化具备双向回滚机制，保存失败立即撤销闹钟。
   - 真实设备开机与应用包替换 (`MY_PACKAGE_REPLACED`) 精确区分处理。

---

## 二、真实环境配置与依赖版本

| 组件 | 版本 | 说明 |
| :--- | :--- | :--- |
| **Gradle** | 8.9 | 使用官方 Gradle Wrapper |
| **AGP (Android Gradle Plugin)** | 8.6.0 | 兼容 Android Studio Ladybug / Meerkat |
| **Kotlin** | 2.0.21 | 启用 Compose Compiler 插件 |
| **compileSdk** | 35 (Android 15) | 支持最新 API 平台编译 |
| **targetSdk** | 34 (Android 14) | Wear OS 5 标准目标 |
| **minSdk** | 30 (Wear OS 3.0) | 覆盖主流 Wear OS 3/4/5 设备 |
| **Wear Compose Material3** | 1.0.0-alpha28 | Wear OS 专用 Material 3 规范组件 |
| **Wear Compose Foundation** | 1.4.0 | 适配圆屏弧度与手势返回 |
| **Wear Ongoing Activity** | 1.1.0 | 表盘持续活动状态小图标与快捷返回 |
| **DataStore Preferences** | 1.1.1 | 线程安全轻量级持久化存储 |

---

## 三、目录结构

```text
TakeABreak_WearOS/
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── gradlew
├── gradlew.bat
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/takeabreak/wearos/
        │   │   ├── MainActivity.kt
        │   │   ├── TakeABreakApplication.kt
        │   │   ├── timer/
        │   │   │   ├── TimerState.kt
        │   │   │   ├── TimerEngine.kt
        │   │   │   ├── TimerRepository.kt
        │   │   │   └── ClockProvider.kt
        │   │   ├── alarm/
        │   │   │   ├── AlarmScheduler.kt
        │   │   │   ├── AlarmReceiver.kt
        │   │   │   └── SystemEventReceiver.kt
        │   │   ├── notification/
        │   │   │   ├── NotificationChannels.kt
        │   │   │   ├── ReminderNotifier.kt
        │   │   │   └── NotificationActionReceiver.kt
        │   │   ├── permission/
        │   │   │   └── ReminderPermissionChecker.kt
        │   │   └── ui/
        │   │       ├── ActionFeedback.kt
        │   │       ├── TimerViewModel.kt
        │   │       ├── TimerScreen.kt
        │   │       ├── SettingsScreen.kt
        │   │       ├── ReminderStatusScreen.kt
        │   │       └── theme/
        │   └── res/
        │       ├── values/ (strings.xml, colors.xml, themes.xml)
        │       ├── drawable/ (ic_launcher_foreground.xml, background)
        │       └── mipmap-anydpi-v26/
        └── test/java/com/takeabreak/wearos/
            ├── timer/
            │   ├── TimerEngineTest.kt
            │   └── TimeCalculationTest.kt
            └── alarm/
                └── SessionValidationTest.kt
```

---

## 四、本地构建与测试方法

在装有 Android Studio（或安装了 JDK 17 与 Android SDK 34/35）的本地开发机上执行：

1. **执行纯逻辑单元测试**：
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```
2. **编译 Debug APK**：
   ```bash
   ./gradlew :app:assembleDebug
   ```
   产物位于：`app/build/outputs/apk/debug/app-debug.apk`
3. **代码规范与 Lint 检查**：
   ```bash
   ./gradlew :app:lintDebug
   ```

---

## 五、系统限制与注意事项

- **非百分之百绝对振动声明**：本应用严格遵循 Android 系统规范，提醒受用户系统级“勿扰模式 (DND)”、手表“剧场模式/睡眠模式”、手表系统整体振动开关以及系统电池优化策略限制。
- **无无障碍与私有权限绕过**：不使用任何黑客式保活或隐藏 API，确保通过 Google Play 审核与手表日常能耗合规。
