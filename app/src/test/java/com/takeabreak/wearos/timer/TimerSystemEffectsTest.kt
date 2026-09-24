package com.takeabreak.wearos.timer

import com.takeabreak.wearos.alarm.AlarmScheduler
import com.takeabreak.wearos.timer.support.FakeAlarmScheduler
import com.takeabreak.wearos.timer.support.FakeReminderNotifier
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import org.junit.Assert.*
import org.junit.Test

class TimerSystemEffectsTest {
    @Test fun scheduleFailureLogsOriginalExceptionAndSessionContext() = recordLogs { records ->
        val cause = IOException("Alarm service unavailable")
        val scheduler = object : AlarmScheduler by FakeAlarmScheduler() {
            override fun schedulePhaseAlarm(state: TimerState, previousGeneration: Long?): Boolean {
                throw cause
            }
        }
        val effects = TimerSystemEffects(scheduler, FakeReminderNotifier())
        val state = TimerState(sessionId = "failed-session", generation = 42)
        assertFalse(effects.schedulePhaseAlarm(state))
        val record = records.single()
        assertSame(cause, record.thrown)
        assertTrue(record.message.contains("schedule"))
        assertTrue(record.message.contains("session=${state.sessionId}"))
        assertTrue(record.message.contains("generation=${state.generation}"))
    }

    @Test fun scheduleCancellationPropagatesWithoutFailureLog() = recordLogs { records ->
        val cancellation = CancellationException("Scheduling cancelled")
        val scheduler = object : AlarmScheduler by FakeAlarmScheduler() {
            override fun schedulePhaseAlarm(state: TimerState, previousGeneration: Long?): Boolean {
                throw cancellation
            }
        }
        val effects = TimerSystemEffects(scheduler, FakeReminderNotifier())
        val thrown = assertThrows(CancellationException::class.java) {
            effects.schedulePhaseAlarm(TimerState())
        }
        assertSame(cancellation, thrown)
        assertTrue(records.isEmpty())
    }

    private fun recordLogs(assertions: (List<LogRecord>) -> Unit) {
        val logger = Logger.getLogger(TimerSystemEffects::class.java.name)
        val records = mutableListOf<LogRecord>()
        val handler = object : Handler() {
            override fun publish(record: LogRecord) { records += record }
            override fun flush() = Unit
            override fun close() = Unit
        }
        logger.addHandler(handler)
        try {
            assertions(records)
        } finally {
            logger.removeHandler(handler)
        }
    }
}
