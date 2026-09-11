package com.santimattius.android.strict.preferences.internal

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-global registry of in-flight `commit()` calls keyed by preferences file name.
 *
 * This is purely a *correlation breadcrumb* helper: it counts, per [String] file name, how
 * many `commit()` calls made through this library's own wrapper are currently in flight. It
 * never reads AOSP's private `SharedPreferencesImpl.mDiskWritesInFlight` state and never uses
 * reflection into `SharedPreferencesImpl`/`QueuedWork`.
 *
 * The registry is process-global (not per-instance) because `OverrideActivityContext` can
 * install a fresh `StrictSharedPreferences` wrapper per Activity while all of them back the
 * same underlying file: a per-instance counter would miss that real cross-instance
 * concurrency.
 *
 * Entries are never removed once created. Removing an entry at zero would race an incoming
 * [enter] call on another thread and could drop a genuine concurrency detection; the accepted
 * cost is bounded growth by the (small, single-digit in practice) number of distinct
 * preferences files opened in a process.
 *
 * A `null` file name (legacy factory paths that carry no file-name attribution) is never
 * tracked: [enter] always returns `false` and no map entry is created for it, so two legacy
 * instances backed by different files never collide in a shared bucket.
 */
internal object CommitConcurrencyTracker {

    private val counters = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * Registers the start of a `commit()` call for [fileName].
     *
     * @return `true` when this call is NOT the first commit currently in flight for
     * [fileName] (i.e. concurrency was detected against another in-flight `commit()` on the
     * same file name); always `false` when [fileName] is `null`.
     */
    fun enter(fileName: String?): Boolean {
        val name = fileName ?: return false
        return counters.computeIfAbsent(name) { AtomicInteger(0) }.incrementAndGet() > 1
    }

    /**
     * Registers the end of a `commit()` call for [fileName]. No-op when [fileName] is `null`
     * or when no entry exists for it yet.
     */
    fun exit(fileName: String?) {
        counters[fileName ?: return]?.decrementAndGet()
    }
}
