package com.takeabreak.wearos.timer

import android.os.SystemClock

/**
 * 时钟提供者接口，将单调时钟与绝对时间解耦，便于纯 JVM 单元测试
 */
interface ClockProvider {
    /** 开机单调时间 (毫秒)，不受系统时间调整影响 */
    fun elapsedRealtime(): Long

    /** 墙上日期时间 (毫秒)，用于与 AlarmClockInfo 对齐 */
    fun currentTimeMillis(): Long
}

/**
 * 生产环境 Android 系统时钟
 */
class SystemClockProvider : ClockProvider {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
