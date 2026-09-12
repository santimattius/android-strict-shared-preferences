package com.santimattius.android.strict.preferences.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictPreferencesEventTest {

    @Test
    fun `PreferencesApplyEvent carries the supplied fileName, stage, timestamp and threadName`() {
        val event = PreferencesApplyEvent(
            fileName = "user_prefs",
            lifecycleStage = LifecycleStage.FOREGROUND,
            timestamp = 42L,
            threadName = "worker-1"
        )

        assertEquals("user_prefs", event.fileName)
        assertEquals(LifecycleStage.FOREGROUND, event.lifecycleStage)
        assertEquals(42L, event.timestamp)
        assertEquals("worker-1", event.threadName)
    }

    @Test
    fun `PreferencesApplyEvent allows a null fileName for legacy factory paths`() {
        val event = PreferencesApplyEvent(
            fileName = null,
            lifecycleStage = LifecycleStage.STARTUP,
            timestamp = 1L,
            threadName = "main"
        )

        assertEquals(null, event.fileName)
    }

    @Test
    fun `PreferencesApplyEvent is a StrictPreferencesEvent`() {
        val event: StrictPreferencesEvent = PreferencesApplyEvent(
            fileName = "user_prefs",
            lifecycleStage = LifecycleStage.BACKGROUND,
            timestamp = 2L,
            threadName = "main"
        )

        assertTrue(event is PreferencesApplyEvent)
    }

    @Test
    fun `LifecycleStage wireName values match the spec-defined wire strings`() {
        assertEquals("startup", LifecycleStage.STARTUP.wireName)
        assertEquals("foreground", LifecycleStage.FOREGROUND.wireName)
        assertEquals("background", LifecycleStage.BACKGROUND.wireName)
        assertEquals("activity_transition", LifecycleStage.ACTIVITY_TRANSITION.wireName)
    }
}
