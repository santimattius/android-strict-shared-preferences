# Tasks: Preferences apply() diagnostics breadcrumbs

## Review Workload Forecast

| Field | Value |
| ------- | ------- |
| Estimated changed lines | 550–700 (7 new/modified prod files, 2 new test suites, build+docs) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 → PR 2 → PR 3 |
| Delivery strategy | ask-on-risk |
| Chain strategy | feature-branch-chain |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | PR base branch | Focused test command | Runtime harness | Rollback boundary |
| ------ | ------ | ----------- | ----------------- | ---------------------- | ----------------- | ------------------- |
| 1 | New types + `CommitConcurrencyTracker` + `LifecycleStageTracker`, unit-tested in isolation | PR 1 | `queuedwork-apply-diagnostics` (tracker branch) | `./gradlew :strict-preferences:testDebugUnitTest --tests "*CommitConcurrencyTracker*" --tests "*LifecycleStageTracker*"` | N/A — pure JVM objects, no Android surface touched yet | Delete the 3 new files + their tests; nothing else references them |
| 2 | `fileName` threaded through factory overloads; `emitPreferencesApplyEvents` flag added, unused | PR 2 | PR 1's branch | `./gradlew :strict-preferences:testDebugUnitTest --tests "*StrictSharedPreferencesFactory*" --tests "*StrictPreferencesConfiguration*"` | N/A — plumbing only, nothing emits yet | Revert `StrictContext.kt`, `StrictSharedPreferences.kt` ctor, `StrictPreferencesConfiguration.kt` to PR1 baseline |
| 3 | Emission wiring, lifecycle callbacks, instrumented/static tests, docs | PR 3 | PR 2's branch | `./gradlew :strict-preferences:testDebugUnitTest --tests "*StrictPreferencesWatch*"` | `./gradlew :strict-preferences:connectedDebugAndroidTest` (device/emulator; exercises `ProcessLifecycleOwner` + concurrent `commit()` threads) | Revert emission calls in `StrictEditor`, drop callback registration in `StrictPreferencesInitializer`, remove dependency line; unused types stay (harmless per rollback plan) |

Only the `queuedwork-apply-diagnostics` tracker branch merges to `main`, once PR 3 lands on it. PR 1/2/3 stack in order; each targets the previous PR's branch, never `main` directly.

## Phase 1: Foundation Types & Tracking (PR 1)

- [x] 1.1 RED: `strict-preferences/src/test/.../internal/CommitConcurrencyTrackerTest.kt` — first `enter()` false, nested `enter()` true, `exit()` restores, distinct names independent, `null` always false/no entry
- [x] 1.2 GREEN: create `strict-preferences/src/main/kotlin/.../internal/CommitConcurrencyTracker.kt` per design interfaces
- [x] 1.3 RED: `.../internal/StrictPreferencesEventTest.kt` — `PreferencesApplyEvent` field shape, `LifecycleStage.wireName` values
- [x] 1.4 GREEN: create `.../internal/StrictPreferencesEvent.kt` (`sealed interface StrictPreferencesEvent`, `PreferencesApplyEvent`, `LifecycleStage` enum)
- [x] 1.5 RED: `.../internal/LifecycleStageTrackerTest.kt` — startup→foreground→background matrix, transition window open/expired, same-activity resume opens nothing (injected fake uptime)
- [x] 1.6 GREEN: create `.../internal/LifecycleStageTracker.kt` (tracker object, `LifecycleStageCallbacks`, injectable uptime, `ACTIVITY_TRANSITION_WINDOW_MS`)
- [x] 1.7 REFACTOR: dedupe window-check logic between tracker and its test fake; confirm no callback registration yet (deferred to Phase 3)

## Phase 2: fileName Threading + Config Flag (PR 2)

- [x] 2.1 RED: `.../internal/StrictSharedPreferencesFactoryTest.kt` — `create(delegate, fileName)` carries name; `create(delegate)` yields `fileName == null`
- [x] 2.2 GREEN: modify `internal/StrictSharedPreferences.kt` — `fileName: String?` ctor param, additive `create(delegate, fileName)`, `getInstance` passes cached name
- [x] 2.3 GREEN: modify `internal/StrictContext.kt` — pass cached `name` as `fileName` into `create`
- [x] 2.4 RED: `StrictPreferencesConfigurationTest.kt` — default `emitPreferencesApplyEvents == false`; `withPreferencesApplyEvents()` flips it, positional callers still compile
- [x] 2.5 GREEN: modify `StrictPreferencesConfiguration.kt` — add `emitPreferencesApplyEvents: Boolean = false` as last ctor param + `withPreferencesApplyEvents()`
- [x] 2.6 REFACTOR: verify pre-existing `getSharedPreferences`/factory call sites compile without edits (spec scenario)

## Phase 3: Emission Wiring & Lifecycle Integration (PR 3a)

- [x] 3.1 RED: `StrictSharedPreferencesTest.kt` — `apply()` emits one `PreferencesApplyEvent` on `watchApplyEvents()` with fileName/stage/threadName when flag on; zero when off
- [x] 3.2 GREEN: modify `internal/StrictSharedPreferences.kt` — widen bus to `MutableSharedFlow<StrictPreferencesEvent>`, add `emitPreferencesApplyEvent()`, `StrictEditor` takes `fileName`, emits in `apply()`
- [x] 3.3 RED: `StrictEditorCommitTest.kt` — single caller emits zero; concurrent `enter()==true` path emits exactly one, before `delegateEditor.commit()` returns
- [x] 3.4 GREEN: implement `StrictEditor.commit()` per design (`enter`/emit/`finally exit` wrapping `delegateEditor.commit()`)
- [x] 3.5 RED: `StrictPreferencesWatchTest.kt` — `watch()` still filters/dedupes only `MainThreadAccessEvent`; `watchApplyEvents()` filters only `PreferencesApplyEvent`, no dedup
- [x] 3.6 GREEN: modify `StrictPreferences.kt` — split `watch()`, add `watchApplyEvents(scope, onEvent)`
- [x] 3.7 GREEN: modify `internal/StrictPreferencesInitializer.kt` — register `LifecycleStageCallbacks` + `ProcessLifecycleOwner` observer, `"default"` fileName in `overridePreferenceManager`
- [x] 3.8 GREEN: add `androidx.lifecycle:lifecycle-process` alias to `gradle/libs.versions.toml` (reuse `lifecycle = 2.9.4`) and dependency to `strict-preferences/build.gradle.kts`
- [x] 3.9 REFACTOR: grep module sources; confirm zero reflective `QueuedWork`/`SharedPreferencesImpl` references

## Phase 4: Instrumented & Static Verification (PR 3b)

- [ ] 4.1 RED: `androidTest/.../PreferencesApplyEventInstrumentedTest.kt` — `apply()` emits exactly one event w/ right fileName; flag off emits none; legacy path → `fileName == null`
- [ ] 4.2 RED: `androidTest/.../CommitConcurrencyInstrumentedTest.kt` — `CountDownLatch`-overlapped `commit()` on same fileName (same instance, then two `StrictContext` instances) emits exactly one for the non-first caller; two distinct fileNames emit zero
- [ ] 4.3 GREEN: close any gap 4.1/4.2 surface against Phase 3 implementation
- [ ] 4.4 Add a static grep/lint assertion (module-level test or CI script) enforcing 3.9's zero-reflection result
- [ ] 4.5 Verify existing `MainThreadAccessEvent` consumer test passes unchanged after the bus widening

## Phase 5: Documentation (PR 3c)

- [ ] 5.1 Update `README.md` — breadcrumb-only framing, explicit "not a QueuedWork block detector" statement
- [ ] 5.2 Add KDoc to `PreferencesApplyEvent`, `emitPreferencesApplyEvents`, `CommitConcurrencyTracker` — null-fileName limitation, approximate-concurrency caveat
