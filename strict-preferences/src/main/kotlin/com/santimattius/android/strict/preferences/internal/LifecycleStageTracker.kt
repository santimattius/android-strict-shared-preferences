package com.santimattius.android.strict.preferences.internal

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock

/**
 * Process-global, lock-free snapshot of the current [LifecycleStage].
 *
 * Callbacks ([onProcessStart]/[onProcessStop] from a `ProcessLifecycleOwner` observer,
 * [onActivityPaused]/[onActivityResumedOrCreated] from [LifecycleStageCallbacks]) are expected
 * to run on the main thread, matching Android's lifecycle dispatch. [currentStage] is read
 * from `apply()`/`commit()`, which may run on any thread, so all mutable state here is
 * `@Volatile`.
 *
 * This is a heuristic approximation of lifecycle state for correlation purposes only; it does
 * not guarantee exact classification (e.g. multi-window or configuration-change edge cases).
 */
internal object LifecycleStageTracker {

    /**
     * `Activity` identity window (in ms) after which a paused Activity no longer opens an
     * [LifecycleStage.ACTIVITY_TRANSITION] window for a different Activity's resume/create.
     *
     * Internal, not a public knob: it is a heuristic constant, not a guaranteed contract.
     */
    internal const val ACTIVITY_TRANSITION_WINDOW_MS = 300L

    /** Injectable so unit tests can run entirely on the JVM without `SystemClock`. */
    @Volatile
    internal var uptimeProvider: () -> Long = { SystemClock.uptimeMillis() }

    @Volatile
    private var hasProcessStarted = false

    @Volatile
    private var isProcessStopped = false

    @Volatile
    private var lastPausedActivityIdentity: Int? = null

    @Volatile
    private var lastPausedAtUptimeMs: Long? = null

    @Volatile
    private var transitionWindowOpenedAtUptimeMs: Long? = null

    /** Called when `ProcessLifecycleOwner` reports the process as started/resumed. */
    fun onProcessStart() {
        hasProcessStarted = true
        isProcessStopped = false
    }

    /** Called when `ProcessLifecycleOwner` reports the process as stopped. */
    fun onProcessStop() {
        isProcessStopped = true
    }

    /** Called from `onActivityPaused` with [activityIdentity] = `System.identityHashCode(activity)`. */
    fun onActivityPaused(activityIdentity: Int) {
        lastPausedActivityIdentity = activityIdentity
        lastPausedAtUptimeMs = uptimeProvider()
    }

    /**
     * Called from `onActivityResumed`/`onActivityCreated` with [activityIdentity] =
     * `System.identityHashCode(activity)`. Opens a transition window only when the resumed
     * Activity differs from the last paused one and the pause happened within
     * [ACTIVITY_TRANSITION_WINDOW_MS].
     */
    fun onActivityResumedOrCreated(activityIdentity: Int) {
        val pausedIdentity = lastPausedActivityIdentity
        val pausedAt = lastPausedAtUptimeMs
        if (pausedIdentity != null && pausedIdentity != activityIdentity && pausedAt != null) {
            val now = uptimeProvider()
            if (isWithinTransitionWindow(pausedAt, now)) {
                transitionWindowOpenedAtUptimeMs = now
            }
        }
    }

    /**
     * Reads the current stage. Order: inside an open transition window ->
     * [LifecycleStage.ACTIVITY_TRANSITION]; no process start observed yet ->
     * [LifecycleStage.STARTUP]; process stopped -> [LifecycleStage.BACKGROUND]; otherwise ->
     * [LifecycleStage.FOREGROUND].
     */
    fun currentStage(): LifecycleStage {
        val openedAt = transitionWindowOpenedAtUptimeMs
        if (openedAt != null && isWithinTransitionWindow(openedAt, uptimeProvider())) {
            return LifecycleStage.ACTIVITY_TRANSITION
        }
        return when {
            !hasProcessStarted -> LifecycleStage.STARTUP
            isProcessStopped -> LifecycleStage.BACKGROUND
            else -> LifecycleStage.FOREGROUND
        }
    }

    /**
     * Shared window-check used both when deciding whether to open a transition window (from
     * the pause uptime) and when deciding whether that window is still open (from the uptime
     * it was opened at). A single [ACTIVITY_TRANSITION_WINDOW_MS] comparison keeps both call
     * sites consistent.
     */
    private fun isWithinTransitionWindow(fromUptimeMs: Long, toUptimeMs: Long): Boolean =
        toUptimeMs - fromUptimeMs <= ACTIVITY_TRANSITION_WINDOW_MS

    /** Test-only: resets all mutable state and the uptime provider. Not for production use. */
    internal fun resetForTesting() {
        hasProcessStarted = false
        isProcessStopped = false
        lastPausedActivityIdentity = null
        lastPausedAtUptimeMs = null
        transitionWindowOpenedAtUptimeMs = null
        uptimeProvider = { SystemClock.uptimeMillis() }
    }
}

/**
 * Forwards `Activity` lifecycle transitions to [LifecycleStageTracker], using
 * [System.identityHashCode] rather than holding the `Activity` reference — this tracker is a
 * process-scoped singleton and must never leak an `Activity`.
 *
 * Not registered anywhere yet: registration alongside [OverrideActivityContext] is wired in a
 * later phase.
 */
internal class LifecycleStageCallbacks : DefaultActivityLifecycleCallbacks() {

    override fun onActivityPaused(p0: Activity) {
        LifecycleStageTracker.onActivityPaused(System.identityHashCode(p0))
    }

    override fun onActivityResumed(p0: Activity) {
        LifecycleStageTracker.onActivityResumedOrCreated(System.identityHashCode(p0))
    }

    override fun onActivityCreated(p0: Activity, p1: Bundle?) {
        LifecycleStageTracker.onActivityResumedOrCreated(System.identityHashCode(p0))
    }
}
