# Proposal: Preferences apply() diagnostics breadcrumbs

## Intent

Developers hitting main-thread stalls around `SharedPreferences` cannot tell which file was written, or when in the lifecycle. AOSP review (`SharedPreferencesImpl.enqueueDiskWrite`, `QueuedWork.queue`) confirms file identity is trapped in a private closure and never reaches `QueuedWork`, which holds anonymous `Runnable`s and exposes no public hook. Per-file attribution of a real `waitToFinish()` block is structurally impossible from outside the framework.

**Boundary (explicit):** this emits a *correlation breadcrumb* at the one call site the library owns — `StrictEditor.apply()`. It is **not** a QueuedWork block detector and must never be documented as one. Value comes from cross-referencing breadcrumbs with an ANR trace, StrictMode, or Perfetto.

## Scope

### In Scope
- `fileName` threaded into the wrapper via an **additive** factory overload (`StrictContext.getSharedPreferences`, `StrictSharedPreferences.create()`/`getInstance()`); existing signatures untouched.
- Lifecycle stage via `androidx.lifecycle:lifecycle-process` (`ProcessLifecycleOwner`).
- New `PreferencesApplyEvent(fileName, lifecycleStage, timestamp, threadName)` on the existing `StrictPreferences.watch()` flow.
- `activity_transition` stage by time-window heuristic (`onPause` of A near `onResume`/`onCreate` of B).
- Emission gated behind a new `StrictPreferencesConfiguration` flag (e.g. `emitPreferencesApplyEvents`), mirroring `emitMainThreadAccessEvents`; default off.
- `commit()` emits `PreferencesApplyEvent` **only when a concurrent `commit()` is detected on the same instance**. Verified against AOSP (`SharedPreferencesImpl.commitToMemory()` / `enqueueDiskWrite()`, master branch): the first concurrent commit's disk-write Runnable runs synchronously in-place (`mDiskWritesInFlight == 1`); any later concurrent commit's Runnable is routed through the same `QueuedWork.queue()` path `apply()` uses, instead of running immediately — the caller still blocks on the latch, but the write now competes in the shared queue. Detecting this requires the wrapper's own concurrency tracking (e.g. an atomic in-flight counter per `StrictSharedPreferences` instance), since AOSP's `mDiskWritesInFlight` is private.
- README/KDoc wording stating the breadcrumb-only limit.

### Out of Scope
- Reflection into `QueuedWork`/`SharedPreferencesImpl`.
- `commit()` duration measurement (no duration field in the confirmed event shape).
- Emitting on every single-caller `commit()` (no QueuedWork interaction in that case — nothing to correlate).
- Any change to `MainThreadAccessEvent`.
- Guaranteed stage accuracy — heuristic by design.

## Capabilities

### New Capabilities
- `preferences-apply-diagnostics`: breadcrumb events carrying preferences file name, lifecycle stage, timestamp, thread name — emitted on every `apply()` and on concurrent `commit()` calls on the same instance, gated by `emitPreferencesApplyEvents`.

### Modified Capabilities
- None — no `openspec/specs/` exists; this is the first change.

## Approach

`StrictContext` already holds both name and instance: pass `name` through an overload into `StrictSharedPreferences`/`StrictEditor`. Register a `ProcessLifecycleOwner` observer plus expanded `ActivityLifecycleCallbacks` in `StrictPreferencesInitializer`, exposing a current-stage value. `apply()` reads both and emits the new event when the flag is on. `commit()` wraps the real call with an atomic in-flight counter per instance; only the non-first concurrent caller emits (mirrors AOSP's own `mDiskWritesInFlight` check that decides whether a commit's write runs in-place or through `QueuedWork.queue()`). Purely additive; no consumer breaks.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `internal/StrictSharedPreferences.kt` | Modified | `fileName` field, overload, emit in `apply()` |
| `internal/StrictContext.kt` | Modified | Pass cached `name` to wrapper |
| `internal/OverrideActivityContext.kt`, `internal/DefaultActivityLifecycleCallbacks.kt` | Modified | Pause/resume/create for transition heuristic |
| `internal/StrictPreferencesInitializer.kt` | Modified | Register process lifecycle observer |
| `internal/StrictSharedPreferences.kt` (Editor) | Modified | In-flight `commit()` counter to detect concurrency |
| `StrictPreferences.kt`, `StrictPreferencesConfiguration.kt` | Modified | New event type; `emitPreferencesApplyEvents` flag (default off) |
| `strict-preferences/build.gradle.kts` | Modified | Add `lifecycle-process` |
| `README.md` | Modified | Honest framing |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Mis-marketed as QueuedWork block detection | High | Limit stated in spec, README, KDoc |
| Public API break in factory | Med | Overload only; API-compat test |
| Stage misclassified (multi-window, config change) | High | Document as approximate hint |
| New AndroidX dependency in consumer graph | Low | Single small artifact |
| Event volume on write-heavy apps | Med | Gated behind `emitPreferencesApplyEvents`, default off |
| False negative: concurrent-commit detection race (counter check vs. actual AOSP `mDiskWritesInFlight`) | Low | Document as approximate; our counter mirrors but doesn't read AOSP's private state |

## Rollback Plan

Code-only, no data or migration; revertable per commit:
1. Remove `PreferencesApplyEvent` and its emission (`MainThreadAccessEvent` consumers unaffected — separate type).
2. Drop the lifecycle observer registration in `StrictPreferencesInitializer`.
3. Remove `lifecycle-process` from `strict-preferences/build.gradle.kts`.
4. Overloads may stay (harmless, additive) or be removed; original signatures never changed.

## Dependencies

- New: `androidx.lifecycle:lifecycle-process`.
- `openspec/config.yaml` and `openspec/specs/` absent — project SDD config never initialized.

## Success Criteria

- [ ] One `apply()` emits exactly one `PreferencesApplyEvent` with the correct `fileName`, gated by `emitPreferencesApplyEvents` (off by default; no event when disabled).
- [ ] `lifecycleStage` reports startup, foreground, background, and `activity_transition` in an instrumented sample run.
- [ ] A single-caller `commit()` (no concurrency) emits zero `PreferencesApplyEvent`.
- [ ] Two threads calling `commit()` concurrently on the same instance emit exactly one `PreferencesApplyEvent` for the non-first (queued) call.
- [ ] Existing `MainThreadAccessEvent` consumers compile and behave unchanged.
- [ ] Pre-existing factory/`getSharedPreferences` call sites compile without edits.
- [ ] Zero reflective references to `QueuedWork`/`SharedPreferencesImpl` in the diff.
- [ ] README and KDoc state this correlates `apply()`/concurrent-`commit()` with lifecycle stage and does **not** detect QueuedWork blocking.
