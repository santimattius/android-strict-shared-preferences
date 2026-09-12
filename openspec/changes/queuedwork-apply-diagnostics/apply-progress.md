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
