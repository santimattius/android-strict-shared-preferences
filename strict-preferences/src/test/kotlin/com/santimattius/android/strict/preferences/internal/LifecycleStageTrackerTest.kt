package com.santimattius.android.strict.preferences.internal

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Verifies [LifecycleStageTracker]'s stage matrix and activity-transition window heuristic
 * using an injected fake uptime provider, so it runs on the plain JVM (no Robolectric,
 * no real `Activity`/`ProcessLifecycleOwner`).
 */
class LifecycleStageTrackerTest {

    private var fakeUptimeMs = 0L

    @Before
    fun setUp() {
        fakeUptimeMs = 0L
        LifecycleStageTracker.resetForTesting()
        LifecycleStageTracker.uptimeProvider = { fakeUptimeMs }
    }

    @After
    fun tearDown() {
        LifecycleStageTracker.resetForTesting()
    }

    @Test
    fun `before any process transition is observed the stage is startup`() {
        assertEquals(LifecycleStage.STARTUP, LifecycleStageTracker.currentStage())
    }

    @Test
    fun `after the process starts the stage is foreground`() {
        LifecycleStageTracker.onProcessStart()

        assertEquals(LifecycleStage.FOREGROUND, LifecycleStageTracker.currentStage())
    }

    @Test
    fun `after the process stops the stage is background`() {
        LifecycleStageTracker.onProcessStart()
        LifecycleStageTracker.onProcessStop()

        assertEquals(LifecycleStage.BACKGROUND, LifecycleStageTracker.currentStage())
    }

    @Test
    fun `a resume for a different activity within the window reports activity_transition`() {
        LifecycleStageTracker.onProcessStart()
        LifecycleStageTracker.onActivityPaused(activityIdentity = 1)
        fakeUptimeMs += 100L

        LifecycleStageTracker.onActivityResumedOrCreated(activityIdentity = 2)

        assertEquals(LifecycleStage.ACTIVITY_TRANSITION, LifecycleStageTracker.currentStage())
    }

    @Test
    fun `the transition window expires after ACTIVITY_TRANSITION_WINDOW_MS`() {
        LifecycleStageTracker.onProcessStart()
        LifecycleStageTracker.onActivityPaused(activityIdentity = 1)
        fakeUptimeMs += 100L
        LifecycleStageTracker.onActivityResumedOrCreated(activityIdentity = 2)
        fakeUptimeMs += LifecycleStageTracker.ACTIVITY_TRANSITION_WINDOW_MS + 1L

        assertEquals(LifecycleStage.FOREGROUND, LifecycleStageTracker.currentStage())
    }

    @Test
    fun `a resume for the same activity identity opens no transition window`() {
        LifecycleStageTracker.onProcessStart()
        LifecycleStageTracker.onActivityPaused(activityIdentity = 1)
        fakeUptimeMs += 100L

        LifecycleStageTracker.onActivityResumedOrCreated(activityIdentity = 1)

        assertEquals(LifecycleStage.FOREGROUND, LifecycleStageTracker.currentStage())
    }

    @Test
    fun `a resume for a different activity outside the window opens no transition`() {
        LifecycleStageTracker.onProcessStart()
        LifecycleStageTracker.onActivityPaused(activityIdentity = 1)
        fakeUptimeMs += LifecycleStageTracker.ACTIVITY_TRANSITION_WINDOW_MS + 1L

        LifecycleStageTracker.onActivityResumedOrCreated(activityIdentity = 2)

        assertEquals(LifecycleStage.FOREGROUND, LifecycleStageTracker.currentStage())
    }
}
