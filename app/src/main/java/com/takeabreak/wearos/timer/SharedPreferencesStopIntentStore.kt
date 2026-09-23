package com.takeabreak.wearos.timer

import android.content.Context
import java.io.IOException

class SharedPreferencesStopIntentStore(context: Context) : StopIntentStore {
    private val prefs = context.getSharedPreferences("timer_stop_intent", Context.MODE_PRIVATE)

    override fun stoppedThroughGeneration(): Long = prefs.getLong("stopped_through_generation", -1L)

    override fun isRecoveryBlocked(sessionId: String): Boolean {
        if (sessionId in prefs.getStringSet("stopped_sessions", emptySet()).orEmpty()) return true
        // Missing key preserves compatibility with the earlier generation-only journal.
        val allowedSessionId = prefs.getString("recovery_session_id", null) ?: return false
        return allowedSessionId.isEmpty() || allowedSessionId != sessionId
    }

    override fun markStopped(generation: Long) {
        // Engine mutations run on Dispatchers.IO. commit must finish before cancelling alarms.
        val stopGeneration = maxOf(generation, stoppedThroughGeneration())
        if (!prefs.edit()
                .putLong("stopped_through_generation", stopGeneration)
                .putString("recovery_session_id", "")
                .remove("stopped_sessions")
                .commit()
        ) {
            throw IOException("停止意图保存失败")
        }
    }

    override fun markSessionStopped(sessionId: String) {
        require(sessionId.isNotEmpty())
        val stopped = prefs.getStringSet("stopped_sessions", emptySet()).orEmpty() + sessionId
        if (!prefs.edit().putStringSet("stopped_sessions", stopped).commit()) {
            throw IOException("通知停止请求保存失败")
        }
    }

    override fun allowStartedSession(sessionId: String) {
        require(sessionId.isNotEmpty())
        // Authorize exactly the new session, never all generations. If start then fails,
        // an older RUNNING snapshot remains blocked even if its generation was unknown.
        if (!prefs.edit().putString("recovery_session_id", sessionId)
                .remove("stopped_sessions").commit()
        ) {
            throw IOException("新计时会话授权保存失败，请重试开始")
        }
    }
}
