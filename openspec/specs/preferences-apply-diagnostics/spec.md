# Preferences Apply Diagnostics Specification

## Purpose

Emit correlation breadcrumbs — not a QueuedWork block detector — that let a developer cross-reference an `apply()` call or a concurrent `commit()` call with the file name, lifecycle stage, timestamp, and thread name at that moment. Value comes from cross-referencing breadcrumbs with an ANR trace, StrictMode, or Perfetto, not from any claim about `QueuedWork` internals.

## Requirements

### Requirement: Feature flag gates all emission

The system MUST NOT emit any `PreferencesApplyEvent` unless `StrictPreferencesConfiguration.emitPreferencesApplyEvents` is explicitly set to `true`. The flag MUST default to `false`.

#### Scenario: Flag left at default

- GIVEN a `StrictPreferencesConfiguration` built without setting `emitPreferencesApplyEvents`
- WHEN `apply()` is called on a `StrictEditor`
- THEN no `PreferencesApplyEvent` is emitted on `StrictPreferences.watch()`

#### Scenario: Flag explicitly disabled

- GIVEN `emitPreferencesApplyEvents` is explicitly set to `false`
- WHEN `apply()` is called, and separately a concurrent `commit()` scenario occurs on the same instance
- THEN no `PreferencesApplyEvent` is emitted in either case

#### Scenario: Flag enabled

- GIVEN `emitPreferencesApplyEvents` is set to `true`
- WHEN `apply()` is called on a `StrictEditor`
- THEN exactly one `PreferencesApplyEvent` is emitted on `StrictPreferences.watch()`

### Requirement: apply() emits one event carrying file name and lifecycle stage

When `emitPreferencesApplyEvents` is `true`, every call to `StrictEditor.apply()` MUST emit exactly one `PreferencesApplyEvent(fileName, lifecycleStage, timestamp, threadName)` on the existing `StrictPreferences.watch()` flow. `fileName` MUST be the preferences file name supplied through the wrapper's factory overload at construction time. `lifecycleStage` MUST reflect the process/activity state observed at the moment `apply()` executes, and `threadName` MUST be the name of the calling thread.

#### Scenario: apply() during startup

- GIVEN the flag is on and no `ProcessLifecycleOwner` foreground/background transition has occurred yet
- WHEN `apply()` is called
- THEN one `PreferencesApplyEvent` is emitted with `lifecycleStage = "startup"`

#### Scenario: apply() while app is foreground

- GIVEN the flag is on and `ProcessLifecycleOwner` reports the process as started/resumed
- WHEN `apply()` is called
- THEN one `PreferencesApplyEvent` is emitted with `lifecycleStage = "foreground"`

#### Scenario: apply() while app is background

- GIVEN the flag is on and `ProcessLifecycleOwner` reports the process as stopped
- WHEN `apply()` is called
- THEN one `PreferencesApplyEvent` is emitted with `lifecycleStage = "background"`

#### Scenario: apply() during an activity transition

- GIVEN the flag is on and Activity A's `onPause` occurred within the configured time window of Activity B's `onResume`/`onCreate`
- WHEN `apply()` is called inside that window
- THEN one `PreferencesApplyEvent` is emitted with `lifecycleStage = "activity_transition"`

#### Scenario: fileName travels through the new factory overload

- GIVEN a `StrictSharedPreferences` instance obtained via the additive `fileName`-carrying overload of `StrictContext.getSharedPreferences` (or `StrictSharedPreferences.create()`/`getInstance()`)
- WHEN `apply()` is called on an editor from that instance
- THEN the emitted `PreferencesApplyEvent.fileName` equals the exact name passed into that overload

#### Scenario: pre-existing call sites remain unaffected

- GIVEN a caller uses a pre-existing factory/`getSharedPreferences` signature without the new overload
- WHEN the code compiles and runs
- THEN it compiles without edits and continues to function, with `fileName` diagnostics simply unavailable through that call path

### Requirement: commit() emits only on detected concurrency

`StrictEditor.commit()` MUST track in-flight commits per preferences file name, using an atomic counter shared across every `StrictSharedPreferences` instance wrapping that file name (e.g. one instance per Activity via `OverrideActivityContext`). When `emitPreferencesApplyEvents` is `true`, a `commit()` call MUST emit a `PreferencesApplyEvent` if and only if it is not the first concurrent commit in flight on that file name at the time it starts. A single caller's `commit()` with no concurrent commit on the same file name MUST NOT emit any event.
(Previously: tracking was scoped per instance, missing cross-instance concurrency on the same file.)

#### Scenario: single caller, no concurrency

- GIVEN the flag is on and no other `commit()` is in flight on the file name
- WHEN one thread calls `commit()` and it completes
- THEN zero `PreferencesApplyEvent`s are emitted for that call

#### Scenario: two threads commit concurrently on the same fileName

- GIVEN the flag is on and two callers (the same or two distinct `StrictSharedPreferences` instances) wrap the same preferences file name
- WHEN thread A calls `commit()` and, while A's write is in flight, thread B calls `commit()` targeting that same file name
- THEN exactly one `PreferencesApplyEvent` is emitted, attributed to the non-first (queued) caller, carrying that caller's `fileName`, `lifecycleStage`, `timestamp`, and `threadName`
- AND the first (in-flight) caller's `commit()` emits no event

#### Scenario: concurrent commits on the same fileName from two different instances

- GIVEN the flag is on and two distinct `StrictSharedPreferences` instances (e.g. one per Activity via `OverrideActivityContext`) both wrap the same file name
- WHEN thread A calls `commit()` on the first instance and, while A's write is in flight, thread B calls `commit()` on the second instance targeting the same file name
- THEN exactly one `PreferencesApplyEvent` is emitted, attributed to the non-first (queued) caller
- AND the first (in-flight) caller's `commit()` emits no event

#### Scenario: concurrent commits on two different fileNames

- GIVEN the flag is on
- WHEN two threads call `commit()` at the same time but the instances wrap two different (genuinely distinct) file names
- THEN neither call is treated as concurrent with the other, and no event is emitted from that overlap alone

### Requirement: breadcrumb-only framing, no QueuedWork introspection

The system MUST NOT use reflection into `QueuedWork` or `SharedPreferencesImpl`, and MUST NOT claim or imply detection of actual `QueuedWork` blocking. Documentation (README, KDoc) MUST state that emitted events correlate `apply()`/concurrent-`commit()` calls with lifecycle stage, and MUST NOT describe the feature as a QueuedWork block detector.

#### Scenario: no reflective access in the diff

- GIVEN the shipped implementation of this capability
- WHEN the diff is inspected
- THEN it contains zero reflective references to `QueuedWork` or `SharedPreferencesImpl`

#### Scenario: documentation states the correlation-only limit

- GIVEN the README and KDoc for this feature
- WHEN a developer reads them
- THEN the text states the events correlate `apply()`/concurrent-`commit()` with lifecycle stage and explicitly does not claim to detect `QueuedWork` blocking

### Requirement: existing MainThreadAccessEvent behavior is unaffected

Adding `PreferencesApplyEvent` and its emission MUST NOT change the shape, emission conditions, or consumer contract of the existing `MainThreadAccessEvent`.

#### Scenario: MainThreadAccessEvent consumers unchanged

- GIVEN an existing consumer of `StrictPreferences.watch()` filtering for `MainThreadAccessEvent`
- WHEN this capability is added and `emitPreferencesApplyEvents` is toggled on or off
- THEN the consumer compiles and behaves exactly as before, receiving `MainThreadAccessEvent`s unaffected by `PreferencesApplyEvent` emission
