package com.takeabreak.wearos.timer

import android.os.SystemClock
import android.content.Context
import android.provider.Settings

/**
 * 生产环境 Android 系统时钟
 */
class SystemClockProvider(private val context: Context) : ClockProvider {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun bootCount(): Int? = Settings.Global.getInt(
        context.contentResolver, Settings.Global.BOOT_COUNT, -1
    ).takeIf { it >= 0 }
}
