# Native Android MVP Acceptance Checklist (2026-04-23)

This checklist maps the MVP acceptance scenarios from
`docs/plans/2026-04-22-native-android-stack-plan.md` to reproducible checks.

## Automated Entry Points

From repo root:

```bash
./gradlew :shared-core:test
./gradlew :android-data:testDebugUnitTest :app:testDebugUnitTest
pwsh ./scripts/run-android-connected-tests.ps1 -TestClass com.yepanywhere.android.RelayLoginScreenTest
pwsh ./scripts/run-android-connected-tests.ps1 -TestClass com.yepanywhere.android.AndroidNotificationRouteIntentTest
pwsh ./scripts/run-android-connected-tests.ps1 -TestClass com.yepanywhere.android.AndroidAppContainerPushEventTest
```

Status: completed on 2026-04-26 using the Android workspace wrapper:

```powershell
cd packages/android-app
.\gradlew.bat :shared-core:test :android-data:testDebugUnitTest :app:testDebugUnitTest --continue
```

Connected emulator checks completed on `emulator-5554`:

```powershell
pwsh ./scripts/run-android-connected-tests.ps1 -TestClass com.yepanywhere.android.RelayLoginScreenTest
pwsh ./scripts/run-android-connected-tests.ps1 -TestClass com.yepanywhere.android.AndroidNotificationRouteIntentTest
pwsh ./scripts/run-android-connected-tests.ps1 -TestClass com.yepanywhere.android.AndroidAppContainerPushEventTest
```

Persistence resilience:

```bash
pwsh ./scripts/test-broken-relay-session.ps1
pwsh ./scripts/restore-relay-auth-state.ps1
```

## Scenario Matrix

1. [x] Relay login and reopen without full re-auth
- Coverage: `RelayLoginScreenTest` (instrumentation), `RelayLoginViewModelTest` (unit), `AndroidSharedPreferencesRelayAuthStateStoreTest` (unit)
- Pass criteria: app restores URL/identity, reconnect attempt executes, login screen remains responsive on failed restore.
- Status: completed by unit coverage and connected emulator run.

2. [x] Session opens with cached data, then refreshes from backend
- Coverage: `RelaySupervisorRuntimeTest`, `RoomSessionCacheStoreTest`
- Pass criteria: cache stream emits immediately, refresh pipeline updates sessions/inbox/timeline after connection.
- Status: completed by `RelaySupervisorRuntimeTest`, `RoomSessionCacheStoreTest`, and unit gate.

3. [x] Approval notification tap routes correctly and resyncs target
- Coverage: `AndroidNotificationRouteIntentTest`, `AndroidNotificationRouteResyncOrchestratorTest`
- Pass criteria: deep-link/intent payload selects expected section and triggers route-targeted refresh path.
- Status: completed by unit coverage and connected emulator run.

4. [~] Approve/deny/reply actions hit backend path
- Coverage: `RelaySupervisorRuntimeTest`, `ActiveSessionViewModelTest`
- Pass criteria: `/sessions/:id/input` and reply endpoints are called with expected payload shape.
- Status: partially completed. Implementation exists and command forwarding is covered, but `RelaySupervisorRuntimeTest` currently asserts exact backend path/payload only for approve. Add runtime assertions for reply, deny, and answer-question payloads before marking complete.

5. [x] Offline read-only snapshot behavior
- Coverage: `RoomSessionCacheStoreTest`, active-session command gating in UI
- Pass criteria: cached lists/timeline are readable; actions are disabled while status is `DISCONNECTED|CONNECTING|SYNCING`.
- Status: completed by Room cache tests and `SupervisorShellScreenTest` disconnected/read-only action gating.

6. [x] Broken persisted session fallback
- Coverage: `scripts/test-broken-relay-session.ps1`, `scripts/restore-relay-auth-state.ps1`
- Pass criteria: script can inject broken `stored_session`, app launch does not deadlock, restore script rehydrates prior state.
- Status: completed as available tooling. Requires a previously successful relay login on an emulator to execute end-to-end.

## Emulator CI Baseline

Default emulator-only gate is defined in:

- `.github/workflows/android-emulator-connected.yml`

Status: completed.

It runs:

1. `:app:testDebugUnitTest`
2. `:android-data:testDebugUnitTest`
3. connected instrumentation classes:
   - `RelayLoginScreenTest`
   - `AndroidNotificationRouteIntentTest`
   - `AndroidAppContainerPushEventTest`

`RelayLoginIntegrationTest` is intentionally excluded from default emulator CI because it requires external relay DNS/network availability.
