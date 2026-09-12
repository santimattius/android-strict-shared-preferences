# Design: Preferences apply() diagnostics breadcrumbs

## Technical Approach

Additive instrumentation at the only call site the library owns (`StrictEditor`). Reuse established patterns verbatim: wrapper delegation, the companion `MutableSharedFlow` bus, and the function-reference hand-off already used for `checkMainThread`. Two new internal objects: a lifecycle-stage snapshot and a process-global in-flight commit registry keyed by file name. No new module, no reflection into `QueuedWork`/`SharedPreferencesImpl`.

**Boundary (binding on implementation and docs):** this produces a *correlation breadcrumb* — that `apply()` ran, for which file, at which lifecycle stage. It does not observe `QueuedWork`, does not measure blocking, and must never be documented as a block detector.

## Architecture Decisions

| # | Decision | Choice | Rejected | Rationale |
|---|---|---|---|---|
| 1 | Event transport | Widen the companion bus to `MutableSharedFlow<StrictPreferencesEvent>` (new sealed interface); `MainThreadAccessEvent` only gains the supertype | Second independent flow | One bus shares ordering, buffer and overflow policy; consumers recompile unchanged |
| 2 | New entry point | `StrictPreferences.watchApplyEvents(scope, onEvent)`; `watch()` becomes `filterIsInstance<MainThreadAccessEvent>().distinctUntilChanged()` | Overloading `watch()` | Overloads differing only in lambda type make existing `watch(scope) { … }` ambiguous — a source break the spec forbids |
| 3 | Dedup on new stream | None | Mirror `watch()` dedup | Spec requires one event per `apply()`; equal events in the same millisecond would collapse |
| 4 | `fileName` threading | Additive `create(delegate, fileName)`; `create(delegate)` passes `null` | Default parameter | Keeps the old symbol intact; `StrictContext` already holds the name as its cache key |
| 5 | Unknown file name | `fileName: String?`, `null` on legacy paths | Sentinel `"unknown"` | Honest absence beats a string that looks like a real file |
| 6 | Concurrency scope | Process-global `CommitConcurrencyTracker` object: `ConcurrentHashMap<String, AtomicInteger>` keyed by `fileName` | Per-instance `AtomicInteger`; `synchronized` | `OverrideActivityContext` installs a fresh `StrictContext` per Activity, so one file can back several wrappers — per-instance counters miss that real concurrency. A lock adds contention to the path being diagnosed |
| 7 | `fileName == null` | Not tracked: no map entry, concurrency never reported | One shared `null` bucket | Two legacy-path instances on different files would collide there and emit false positives. Known limitation; the overload is the fix |
| 8 | Counter lifetime | Entries are never removed | `remove()` at zero | Remove-at-zero races an incoming `incrementAndGet()` and drops detections. Growth is bounded by distinct preference files per process (single digits) at one key plus an `AtomicInteger` each — accepted |
| 9 | `commit()` emit order | Emit before `delegateEditor.commit()` | After it returns | A `commit()` that never returns (the ANR case) must still leave a breadcrumb |
| 10 | Lifecycle state access | `@Volatile` snapshot in an internal object; callbacks write on main, `apply()` reads anywhere | Query `currentState` at emit time | `LifecycleRegistry` is main-thread-only; a snapshot also separates `startup` from `background` |
| 11 | Activity callbacks | New `LifecycleStageCallbacks : DefaultActivityLifecycleCallbacks()` registered beside `OverrideActivityContext` | Extend `OverrideActivityContext` | Keeps the reflective `mBase` override single-purpose |
| 12 | Activity identity | `System.identityHashCode(activity)` | The `Activity` reference | A process-scoped object holding an `Activity` leaks it |
| 13 | Clocks | `SystemClock.uptimeMillis()` (injectable) for window math; `System.currentTimeMillis()` for `timestamp` | One clock | Monotonic time survives wall-clock jumps; `timestamp` matches `MainThreadAccessEvent`; injection keeps it JVM-testable |
| 14 | Flag placement | `emitPreferencesApplyEvents` as the **last** constructor parameter | Beside `emitMainThreadAccessEvents` | Positional callers exist; inserting earlier breaks them |
| 15 | Stage type | `enum class LifecycleStage` with `wireName` | Raw `String` | Exhaustive `when`; `wireName` pins the strings spec scenarios assert |

## Data Flow

```
StrictContext.getSharedPreferences(name, mode)        name = cache key = fileName
      ▼
StrictSharedPreferences(delegate, fileName)
      │ edit() → StrictEditor(delegateEditor, ::checkMainThread,
      │                       ::emitPreferencesApplyEvent, fileName)
      ▼
apply()  ───────────────────────────────────────────────┐
commit() → CommitConcurrencyTracker.enter(fileName)     │  true = not first in flight
      │        (ConcurrentHashMap<fileName, AtomicInteger>, process-global)
      ▼                                                 ▼
emitPreferencesApplyEvent: gate on configuration.emitPreferencesApplyEvents,
stage from LifecycleStageTracker → _eventBus: MutableSharedFlow<StrictPreferencesEvent>
      │                              │
      ▼                              ▼
watch(): filterIsInstance<MainThreadAccessEvent>().distinctUntilChanged()
watchApplyEvents(): filterIsInstance<PreferencesApplyEvent>()

ProcessLifecycleOwner ON_START/ON_STOP + LifecycleStageCallbacks → LifecycleStageTracker
```

## File Changes

| File | Action | Description |
|---|---|---|
| `internal/StrictPreferencesEvent.kt` | Create | `sealed interface StrictPreferencesEvent`, `PreferencesApplyEvent`; `MainThreadAccessEvent` stays in place, gaining only the supertype |
| `internal/CommitConcurrencyTracker.kt` | Create | Process-global `internal object`: per-`fileName` in-flight commit counters, `enter`/`exit`, `null` untracked |
| `internal/LifecycleStageTracker.kt` | Create | `LifecycleStage` enum, tracker object (volatile snapshot, transition heuristic, injectable uptime), `LifecycleStageCallbacks`, process observer |
| `internal/StrictSharedPreferences.kt` | Modify | `fileName` ctor param, `create(delegate, fileName)`, `getInstance` passes `name`, `emitPreferencesApplyEvent()`, widened bus, `StrictEditor` takes `fileName` and consults the tracker |
| `internal/StrictContext.kt` | Modify | Pass the cache key as `fileName` into `create` |
| `internal/StrictPreferencesInitializer.kt` | Modify | Register callbacks and process observer (main-thread-posted); `"default"` as `fileName` in `overridePreferenceManager` |
| `StrictPreferences.kt` | Modify | `watch()` filters by type; add `watchApplyEvents()` |
| `StrictPreferencesConfiguration.kt` | Modify | `emitPreferencesApplyEvents = false` (last param) + `withPreferencesApplyEvents()` |
| `strict-preferences/build.gradle.kts`, `gradle/libs.versions.toml` | Modify | `androidx.lifecycle:lifecycle-process` (`implementation`, reusing `lifecycle = 2.9.4`) |
| `README.md` | Modify | Breadcrumb-only framing |

## Interfaces / Contracts

```kotlin
sealed interface StrictPreferencesEvent

data class PreferencesApplyEvent(
    val fileName: String?,                 // null via legacy factory paths
    val lifecycleStage: LifecycleStage,
    val timestamp: Long = System.currentTimeMillis(),
    val threadName: String = Thread.currentThread().name
) : StrictPreferencesEvent

enum class LifecycleStage(val wireName: String) {
    STARTUP("startup"), FOREGROUND("foreground"),
    BACKGROUND("background"), ACTIVITY_TRANSITION("activity_transition")
}

internal object CommitConcurrencyTracker {
    private val counters = ConcurrentHashMap<String, AtomicInteger>()

    /** true when this commit is NOT the first in flight for [fileName]; always false when null. */
    fun enter(fileName: String?): Boolean {
        val name = fileName ?: return false
        return counters.computeIfAbsent(name) { AtomicInteger(0) }.incrementAndGet() > 1
    }

    fun exit(fileName: String?) { counters[fileName ?: return]?.decrementAndGet() }
}

// StrictEditor
override fun commit(): Boolean {
    checkMainThread("Editor.commit")
    val concurrent = CommitConcurrencyTracker.enter(fileName)
    try {
        if (concurrent) emitPreferencesApplyEvent(Thread.currentThread().name)
        return delegateEditor.commit()
    } finally {
        CommitConcurrencyTracker.exit(fileName)
    }
}
```

`computeIfAbsent` is available at the module's `minSdk = 24`. `currentStage()` reads lock-free: inside the transition window → `ACTIVITY_TRANSITION`; no start observed → `STARTUP`; stopped → `BACKGROUND`; else `FOREGROUND`. The window opens only when a resume/create arrives for an activity identity *different* from the last paused one, within `ACTIVITY_TRANSITION_WINDOW_MS = 300L` of that pause, then extends one window.

## Testing Strategy

TDD is mandatory (RED first, per project mode).

| Layer | What to test | Approach |
|---|---|---|
| Unit (JVM) | Stage matrix: startup → foreground → background, window open/expired, same-activity resume opens nothing | Pure Kotlin with an injected fake uptime provider |
| Unit (JVM) | `CommitConcurrencyTracker`: first `enter` false, nested `enter` true, `exit` restores, distinct names independent, `null` always false and creates no entry | Direct object calls; each test uses a unique file name because the object is process-global |
| Instrumented | `apply()` emits exactly one event with the right `fileName`; flag off emits nothing; legacy path yields `fileName == null`; `MainThreadAccessEvent` consumers unaffected | `androidTest` collecting `watchApplyEvents` |
| Instrumented | Two threads `commit()` on the same file name — same instance and two instances from different `StrictContext`s — emit exactly one event for the non-first caller; single caller emits zero; two distinct file names emit zero | `CountDownLatch` to overlap the calls |
| Static | Zero reflective references to `QueuedWork`/`SharedPreferencesImpl` | Grep/lint assertion over module sources |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or OS process-integration boundary. `ProcessLifecycleOwner` is an in-process lifecycle observer.

## Migration / Rollout

No migration. Purely additive and off by default (`emitPreferencesApplyEvents = false`). Rollback is the proposal's four steps; removing the feature leaves `MainThreadAccessEvent` consumers untouched.

## Open Questions

- [ ] **Accepted tradeoff:** the tracker is process-global mutable state. Tests must isolate by file name (no reset hook is exposed, to keep the internal surface minimal). Revisit only if a test genuinely needs a reset.
- [ ] **Documented limitation:** with `fileName == null` (legacy factory path) concurrency is never reported. State it in KDoc alongside the overload that fixes it.
- [ ] Our counter never aligns exactly with AOSP's `mDiskWritesInFlight` (incremented outside their lock, before `commitToMemory()`), so false positives and negatives are possible by construction. Document in KDoc; no fix intended.
- [ ] `ACTIVITY_TRANSITION_WINDOW_MS` stays an internal constant, not a public knob. Revisit only if field data demands it.
- [ ] The module consumes `kotlinx.coroutines.flow` transitively. Consider declaring coroutines explicitly while touching the build file (out of scope).
