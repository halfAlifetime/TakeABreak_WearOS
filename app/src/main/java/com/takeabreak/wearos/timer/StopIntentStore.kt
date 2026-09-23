package com.takeabreak.wearos.timer


/** Independent stop journal, including a session gate when the saved generation is unknown. */
interface StopIntentStore {
    fun stoppedThroughGeneration(): Long
    fun isRecoveryBlocked(sessionId: String): Boolean
    fun markStopped(generation: Long)
    fun markSessionStopped(sessionId: String)
    fun allowStartedSession(sessionId: String)
}
