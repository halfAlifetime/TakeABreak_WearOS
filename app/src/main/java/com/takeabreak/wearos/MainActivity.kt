package com.takeabreak.wearos

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.takeabreak.wearos.notification.NotificationChannels
import com.takeabreak.wearos.permission.ReminderSettingsNavigator
import com.takeabreak.wearos.permission.SettingTarget
import com.takeabreak.wearos.ui.ReminderStatusScreen
import com.takeabreak.wearos.ui.SettingsScreen
import com.takeabreak.wearos.ui.TimerScreen
import com.takeabreak.wearos.ui.TimerLoadingScreen
import com.takeabreak.wearos.ui.FeedbackType
import com.takeabreak.wearos.ui.FeedbackSource
import com.takeabreak.wearos.ui.TimerViewModel
import com.takeabreak.wearos.ui.theme.TakeABreakTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_OPEN_TIMER = "com.takeabreak.wearos.action.OPEN_TIMER"

        fun createOpenTimerIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_TIMER
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }

    private val viewModel: TimerViewModel by viewModels {
        (application as TakeABreakApplication).timerViewModelFactory
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 若由到点通知打开，短暂保持亮屏 5 秒便于查看，5 秒后或离开页面时必须安全释放
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 处理首次打开的 Intent 导航意图
        handleIntent(intent)

        // Android 13+ (API 33) 运行时通知权限动态申请
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            TakeABreakTheme {
                MainAppNavHost(
                    viewModel = viewModel,
                    onClearKeepScreenOn = { clearKeepScreenOn() },
                    onOpenNotificationSettings = {
                        val intent = ReminderSettingsNavigator.createAppNotificationSettingsIntent(this)
                        runCatching { startActivity(intent) }
                    },
                    onOpenExactAlarmSettings = {
                        val intent = ReminderSettingsNavigator.createExactAlarmSettingsIntent(this)
                        runCatching { startActivity(intent) }
                    },
                    onOpenSettingTarget = { target ->
                        val targetIntent = when (target) {
                            SettingTarget.EXACT_ALARM -> ReminderSettingsNavigator.createExactAlarmSettingsIntent(this)
                            SettingTarget.APP_NOTIFICATION -> ReminderSettingsNavigator.createAppNotificationSettingsIntent(this)
                            SettingTarget.CHANNEL_WORK -> ReminderSettingsNavigator.createChannelSettingsIntent(this, NotificationChannels.CHANNEL_WORK_ID)
                            SettingTarget.CHANNEL_BREAK -> ReminderSettingsNavigator.createChannelSettingsIntent(this, NotificationChannels.CHANNEL_BREAK_ID)
                            SettingTarget.CHANNEL_STATUS -> ReminderSettingsNavigator.createStatusChannelSettingsIntent(this)
                            SettingTarget.NONE -> null
                        }
                        if (targetIntent != null) {
                            val opened = runCatching { startActivity(targetIntent) }.isSuccess
                            if (!opened) {
                                viewModel.showFeedback("无法直达该设置页，请在手表【设置 -> 应用 -> 休息一下】中手动调整", FeedbackType.INFO)
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setUiVisible(true)
    }

    override fun onResume() {
        super.onResume()
        viewModel.setUiVisible(true)
        viewModel.refreshPermissions()
    }

    override fun onPause() {
        super.onPause()
        viewModel.setUiVisible(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        viewModel.refreshPermissions()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_TIMER) {
            // 纯粹导航意图：仅触发返回 timer 页面，不重启、不恢复、不重置计时
            viewModel.requestNavigateToTimer()
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.setUiVisible(false)
        // 离开前台时彻底释放保持常亮标志，避免手表过度耗电
        clearKeepScreenOn()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.setUiVisible(false)
        clearKeepScreenOn()
    }

    private fun clearKeepScreenOn() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
fun MainAppNavHost(
    viewModel: TimerViewModel,
    onClearKeepScreenOn: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenSettingTarget: (SettingTarget) -> Unit
) {
    val navController = rememberSwipeDismissableNavController()
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()
    val tickElapsed by viewModel.uiTickElapsed
    val permissionEvaluation by viewModel.permissionState
    val feedback by viewModel.feedback
    val navCommand by viewModel.navigationCommand

    // 监听导航命令（例如表盘小图标、常驻通知点击触发的返回 timer 页面意图）
    // 只导航，不重新开始、恢复或重置计时
    LaunchedEffect(navCommand) {
        val command = navCommand ?: return@LaunchedEffect
        if (command.route == "timer") {
            if (navController.currentDestination?.route != "timer") {
                navController.popBackStack("timer", inclusive = false)
            }
            viewModel.consumeNavigationCommand(command.id)
        }
    }

    // 5 秒后执行真正的 clearFlags 释放屏幕常亮标志，保护手表电池
    LaunchedEffect(Unit) {
        delay(5000L)
        onClearKeepScreenOn()
    }

    val loadedState = timerState
    if (loadedState == null) {
        TimerLoadingScreen()
        return
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = "timer"
    ) {
        composable("timer") {
            TimerScreen(
                state = loadedState,
                tickElapsed = tickElapsed,
                feedback = feedback?.takeIf { it.source == FeedbackSource.TIMER },
                onStart = { viewModel.startTimer() },
                onPause = { viewModel.pauseTimer() },
                onResume = { viewModel.resumeTimer() },
                onRetry = { viewModel.retryTimer() },
                onStop = { viewModel.stopTimer() },
                onClearFeedback = viewModel::clearFeedback,
                onOpenSettingTarget = onOpenSettingTarget,
                onOpenSettings = { navController.navigate("settings") }
            )
        }

        composable("settings") {
            SettingsScreen(
                state = loadedState,
                feedback = feedback?.takeIf { it.source.isDurationSetting },
                onClearFeedback = viewModel::clearFeedback,
                onSetWorkDuration = { min -> viewModel.setWorkDuration(min) },
                onSetBreakDuration = { min -> viewModel.setBreakDuration(min) },
                onOpenReminderStatus = { navController.navigate("reminder_status") },
                onOpenSystemNotificationSettings = onOpenNotificationSettings,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                onBack = { navController.popBackStack() }
            )
        }

        composable("reminder_status") {
            ReminderStatusScreen(
                state = loadedState,
                permissionEvaluation = permissionEvaluation,
                feedback = feedback?.takeIf { it.source == FeedbackSource.REMINDER_TEST },
                onTestBreakReminder = { viewModel.testBreakReminder() },
                onTestWorkReminder = { viewModel.testWorkReminder() },
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                onOpenSystemNotificationSettings = onOpenNotificationSettings,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
