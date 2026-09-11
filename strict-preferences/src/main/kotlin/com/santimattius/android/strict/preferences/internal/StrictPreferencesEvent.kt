package com.santimattius.android.strict.preferences.internal

/**
 * Marker supertype for every event emitted on the library's shared event bus.
 *
 * `MainThreadAccessEvent` gains this supertype when the bus is widened (a later phase);
 * this file only introduces the new [PreferencesApplyEvent] branch.
 */
sealed interface StrictPreferencesEvent

/**
 * A correlation breadcrumb for a `SharedPreferences.Editor.apply()` call, or for a
 * `commit()` call detected as concurrent with another in-flight `commit()` on the same
 * preferences file.
 *
 * **This is not a QueuedWork block detector.** It does not observe `QueuedWork` and does not
 * measure blocking; it only records that `apply()`/a concurrent `commit()` ran, for which
 * file, at which lifecycle stage, on which thread, and when. Cross-reference it with an ANR
 * trace, StrictMode, or Perfetto to draw conclusions about blocking.
 *
 * @property fileName the preferences file name supplied through the `fileName`-carrying
 * factory overload at construction time; `null` when the instance was created through a
 * legacy factory path that carries no file-name attribution.
 * @property lifecycleStage the process/activity lifecycle stage observed at the moment the
 * triggering call executed.
 * @property timestamp wall-clock time of the event, in milliseconds.
 * @property threadName the name of the thread that triggered the event.
 */
data class PreferencesApplyEvent(
    val fileName: String?,
    val lifecycleStage: LifecycleStage,
    val timestamp: Long = System.currentTimeMillis(),
    val threadName: String = Thread.currentThread().name
) : StrictPreferencesEvent

/**
 * Coarse process/activity lifecycle stage observed at the moment a [PreferencesApplyEvent] is
 * emitted. This is a heuristic approximation, not a guaranteed classification.
 *
 * @property wireName the stable string exposed on [PreferencesApplyEvent.lifecycleStage] for
 * cross-referencing with external tooling.
 */
enum class LifecycleStage(val wireName: String) {
    STARTUP("startup"),
    FOREGROUND("foreground"),
    BACKGROUND("background"),
    ACTIVITY_TRANSITION("activity_transition")
}
