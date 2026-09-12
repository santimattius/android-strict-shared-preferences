# Apply Progress: queuedwork-apply-diagnostics

## Work Unit 2 / PR2

**Status:** Complete

### Completed tasks

- [x] 2.1 RED: Added factory-overload tests for named and legacy file-name attribution.
- [x] 2.2 GREEN: Added `fileName` to `StrictSharedPreferences`, the additive factory overload, and named `getInstance` forwarding.
- [x] 2.3 GREEN: Forwarded `StrictContext.getSharedPreferences`' supplied name to the named factory overload.
- [x] 2.4 RED: Added configuration tests for the default, opt-in helper, and existing positional constructor argument order.
- [x] 2.5 GREEN: Added last-position `emitPreferencesApplyEvents` (default `false`) and `withPreferencesApplyEvents()`.
- [x] 2.6 REFACTOR: Confirmed legacy factory and `getSharedPreferences` call sites compile without edits.

### Files changed

- `strict-preferences/src/main/kotlin/com/santimattius/android/strict/preferences/internal/StrictSharedPreferences.kt`
- `strict-preferences/src/main/kotlin/com/santimattius/android/strict/preferences/internal/StrictContext.kt`
- `strict-preferences/src/main/kotlin/com/santimattius/android/strict/preferences/StrictPreferencesConfiguration.kt`
- `strict-preferences/src/test/kotlin/com/santimattius/android/strict/preferences/internal/StrictSharedPreferencesFactoryTest.kt`
- `strict-preferences/src/test/kotlin/com/santimattius/android/strict/preferences/StrictPreferencesConfigurationTest.kt`
- `openspec/changes/queuedwork-apply-diagnostics/tasks.md`
- `openspec/changes/queuedwork-apply-diagnostics/apply-progress.md`

### Test commands run

1. `./gradlew :strict-preferences:testDebugUnitTest` — baseline passed before edits (17 existing tests).
2. `./gradlew :strict-preferences:testDebugUnitTest --tests '*StrictSharedPreferencesFactoryTest' --tests '*StrictPreferencesConfigurationTest'` — RED failed at test compilation before production edits: unresolved `emitPreferencesApplyEvents`, `withPreferencesApplyEvents`, `create(delegate, fileName)`, and `fileName`.
3. Same focused command — GREEN passed (5 tests).
4. `./gradlew :strict-preferences:testDebugUnitTest` — passed (22 tests).
5. `git diff --check` — passed; source grep confirmed existing factory and `getSharedPreferences` callers compile without edits.

### TDD Cycle Evidence

| Task | Test file | Layer | Safety net | RED | GREEN | TRIANGULATE | REFACTOR |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2.1–2.3 | `internal/StrictSharedPreferencesFactoryTest.kt` | JVM unit | 17/17 passed | Focused compilation failed for absent overload/property | 2/2 passed | Named and legacy-null factory paths | No further change needed; full suite passed |
| 2.4–2.5 | `StrictPreferencesConfigurationTest.kt` | JVM unit | 17/17 passed | Focused compilation failed for absent flag/helper | 3/3 passed | Default, opt-in, and five-argument positional constructor paths | No further change needed; full suite passed |
| 2.6 | Existing module sources plus both new unit suites | JVM compile/unit | 17/17 passed | N/A: compile-preservation verification | Full suite 22/22 passed | Legacy `create(delegate)` and unchanged `getSharedPreferences`/`getInstance` signatures compile | No production refactor needed |

### Deviations from design

None. This work unit intentionally adds plumbing only: it does not widen the event bus, emit events, register lifecycle callbacks, add dependencies, or alter `StrictEditor` behavior.

### Remaining tasks

- Phase 3 (3.1–3.9): emission wiring, watcher split, lifecycle registration, dependency, and reflection check.
- Phase 4 (4.1–4.5): instrumented and static verification.
- Phase 5 (5.1–5.2): README and KDoc.

### Workload / PR boundary

- **Chain strategy:** feature-branch-chain.
- **Current boundary:** PR2 only — Phase 2 file-name threading and unused configuration flag; based on PR1 and followed by PR3.
- **Out of scope:** all Phase 3+ event-bus, apply/commit emission, lifecycle, dependency, instrumentation, static-lint, and documentation changes.
- **Review budget:** within the user-approved 600 changed-line budget.

## Work Unit 3 / PR3a

**Status:** Complete — tasks 3.1–3.9.
**Completed tasks:** Apply/commit/watcher RED tests; bus/apply emission; commit tracker wiring; split watchers; lifecycle registration; lifecycle-process dependency; reflection check.
**Files changed:** `StrictSharedPreferences.kt`, `StrictPreferences.kt`, `StrictPreferencesInitializer.kt`, focused JVM tests, catalog/build, `tasks.md`.
**Tests:** Focused Phase 3 JVM suite passed; `./gradlew :strict-preferences:testDebugUnitTest` passed; reflection grep and `git diff --check` passed.

### TDD Cycle Evidence

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 3.1–3.9 | Phase 3 JVM tests | Unit/structural | Correction RED: six failures from unmocked `Looper`; focused suite passed | Commit test failed before wiring; watcher contract extended | Robolectric focused + full suites passed | Enabled/disabled apply, single/concurrent commit, typed/deduped watcher paths | Zero-reflection grep passed |
**Deviations / remaining / workload:** Removed broad JVM default-return stubs after observed `Looper.myLooper()` Android-stub failures; only the Phase 3 tests use Robolectric. Phase 4–5 remain unchecked. PR3a snapshot is reproducibly 420 additions+deletions; it is covered by the maintainer's accepted `size:exception` ceiling of 422.

## Work Unit 3 / PR3b split — PR3b2

**Status:** Complete — tasks 4.1–4.5 are checked; Phase 5 remains intentionally unstarted.

### Split and PR boundary

The maintainer split the former PR3b work unit into two reviewable slices:

```text
PR3a → PR3b1 (`134d75c`) → 📍 PR3b2 (current)
```

- **PR3b1 (committed):** 60-line `StrictEditor` chaining fix plus the 224-line `CommitConcurrencyInstrumentedTest` — **284 added source/test lines**.
- **PR3b2 (current):** the 151-line `PreferencesApplyEventInstrumentedTest` plus the 39-line `ForbiddenFrameworkReflectionTest` — **190 added source/test lines**. Its only additional changes are cumulative OpenSpec task/progress evidence.
- No production code, commit-concurrency test, README, KDoc, or other test is in the PR3b2 boundary. No commit was created for PR3b2.

### Completed tasks and targeted evidence

- [x] 4.1 `PreferencesApplyEventInstrumentedTest` covers named, disabled, and legacy `apply()` paths. `./gradlew :strict-preferences:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.santimattius.android.strict.preferences.internal.PreferencesApplyEventInstrumentedTest` passed all 3 tests on connected `Pixel_9(AVD) - 15`.
- [x] 4.2–4.3 are delivered in PR3b1: concurrent-commit coverage and the 60-line wrapper-preservation fix.
- [x] 4.4 `ForbiddenFrameworkReflectionTest` is the 39-line static guard. `./gradlew :strict-preferences:testDebugUnitTest --tests com.santimattius.android.strict.preferences.internal.ForbiddenFrameworkReflectionTest` passed.
- [x] 4.5 `./gradlew :strict-preferences:testDebugUnitTest` passed, preserving the existing `MainThreadAccessEvent` consumer contract after bus widening.

### Proven pre-existing full-suite limitation

The earlier unfiltered `:strict-preferences:connectedDebugAndroidTest` run reached 7 tests on Pixel_9 (API 35); all six Phase 4 tests passed after the PR3b1 chaining fix. Its sole failure was the pre-existing, out-of-scope `ExampleInstrumentedTest.useAppContext` assertion: it expected `com.santimattius.android.library.test` but observed `com.santimattius.android.strict.preferences.test`. PR3b2 did not modify that test.

### TDD Cycle Evidence

| Task | RED | GREEN / verification | TRIANGULATE / REFACTOR |
| --- | --- | --- | --- |
| 4.1 | The chained `apply()` device case previously exposed the wrapper-delegation gap. | PR3b2's targeted Pixel_9 run passed named, disabled, and legacy paths (3/3). | Collector readiness is synchronized through `subscriptionCount`; positive cases use a 5-second latch and disabled uses a 250-ms no-event window. |
| 4.2–4.3 | Device failure identified the delegated-editor path. | PR3b1 contains the wrapper fix and 224-line concurrency suite; all Phase 4 tests passed in the prior full connected run except the unrelated package assertion. | The split keeps the fix and its concurrency test together. |
| 4.4–4.5 | Guard and unchanged consumer contract were selected before production changes. | Focused guard and full JVM suite pass. | Guard checks all main Kotlin/Java sources for framework-private reflection. |

### Remaining tasks and workload

- Phase 5 (5.1–5.2) remains unchecked and was not implemented.
- **Chain strategy:** feature-branch-chain. **Current boundary:** PR3b2 only, based on PR3b1 (`134d75c`); follow-up PR3c is documentation only.
- PR3b2's source/test payload is 190 added lines; its complete allowed-surface review diff is **241 additions + deletions** (151 apply test + 39 guard + 51 OpenSpec evidence), below the 400-line budget. No size exception is used.

## Work Unit 3 / PR3c

**Status:** Complete — tasks 5.1–5.2. Documentation/KDoc only; no behavior change.

### Completed tasks

- [x] 5.1 Added concise README usage for `emitPreferencesApplyEvents` and `watchApplyEvents()`, with breadcrumb-only framing and an explicit statement that it is not a QueuedWork block detector.
- [x] 5.2 Documented `PreferencesApplyEvent`, `emitPreferencesApplyEvents`, and `CommitConcurrencyTracker`: legacy factory paths may yield `fileName = null`, do not report concurrent-`commit()` breadcrumbs, and wrapper-observed concurrency is approximate rather than framework visibility.

### Files changed

- `README.md`
- `strict-preferences/src/main/kotlin/com/santimattius/android/strict/preferences/internal/StrictPreferencesEvent.kt`
- `strict-preferences/src/main/kotlin/com/santimattius/android/strict/preferences/StrictPreferencesConfiguration.kt`
- `strict-preferences/src/main/kotlin/com/santimattius/android/strict/preferences/internal/CommitConcurrencyTracker.kt`
- `openspec/changes/queuedwork-apply-diagnostics/tasks.md`
- `openspec/changes/queuedwork-apply-diagnostics/apply-progress.md`

### Verification evidence

1. `./gradlew :strict-preferences:testDebugUnitTest` — **BUILD SUCCESSFUL** (16 actionable tasks: 4 executed, 12 up-to-date).
2. `git diff --check` — passed with no whitespace errors.
3. `git diff --word-diff=porcelain -- README.md strict-preferences/src/main/kotlin/com/santimattius/android/strict/preferences/internal/StrictPreferencesEvent.kt strict-preferences/src/main/kotlin/com/santimattius/android/strict/preferences/StrictPreferencesConfiguration.kt strict-preferences/src/main/kotlin/com/santimattius/android/strict/preferences/internal/CommitConcurrencyTracker.kt openspec/changes/queuedwork-apply-diagnostics/tasks.md openspec/changes/queuedwork-apply-diagnostics/apply-progress.md` — confirmed the production Kotlin changes are KDoc plus non-semantic formatting; no executable statements, declarations, or signatures changed, so production bytecode behavior is unchanged.

### TDD Cycle Evidence

| Task | RED | GREEN | TRIANGULATE | REFACTOR |
| --- | --- | --- | --- | --- |
| 5.1–5.2 | Not applicable: the assigned work is passive README/KDoc documentation only, with no production behavior to specify through a failing test. | Full required JVM unit suite passed after the documentation edits. | Documentation names both no-file-name and approximate-concurrency limits across the event, flag, and tracker surfaces. | Reviewed the source diff to confirm only documentation and non-semantic formatting changed; no behavior refactor was needed. |

### Deviations and remaining tasks

- No deviations from the documentation design.
- No implementation-owned tasks remain for this change.

### Workload / PR boundary

- **Chain strategy:** feature-branch-chain.
- **Current boundary:** PR3c only, based on `b74b0f8`; README and KDoc limits plus OpenSpec completion evidence. No production behavior, tests, dependencies, or instrumentation changes are included.
- **Review budget:** below the 400-line budget; no size exception is used.
- **Commit state:** no commit created.
