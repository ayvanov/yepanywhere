# Native Android Next Batches (2026-04-23)

## Context

This plan continues [2026-04-22-native-android-stack-plan.md](./2026-04-22-native-android-stack-plan.md).

Current status at session handoff:

- Relay login/reconnect/logout works in the current Android app flow.
- Relay URL and Identity persistence works.
- Reconnect flicker was reduced and startup preview flicker was addressed.
- Emulator-only instrumentation launcher is in place for reliable test execution.
- Main gap: data/runtime stack is still largely in-memory scaffolding; not yet fully production storage/sync.

## Operating Rules For Next Session

- Keep `packages/mobile` unchanged.
- Prioritize emulator-first verification for Android instrumentation.
- Land each batch behind passing unit tests and targeted emulator checks.
- Do not start a new batch until previous batch acceptance criteria are met.

## Batch 1 - Replace In-Memory Runtime With Real Relay Data Pipeline

Goal:
Move `android-data` from `InMemorySupervisorRuntime` orchestration to a real repository/client pipeline for projects, sessions, inbox, and active timeline updates.

Scope:

- Introduce concrete runtime wiring for real relay transport + repositories.
- Keep existing `SupervisorShellDataSource` API stable where possible.
- Preserve current UI contract (`ViewModel` + `StateFlow`) and behavior.

Tasks:

1. Implement concrete `RelayConnectionClient` integration for realtime streams.
2. Implement real `ProjectsRepository`, `SessionsRepository`, `InboxRepository`, `ApprovalsRepository` backed by transport.
3. Switch `AndroidDataLayer` default runtime wiring from in-memory to real pipeline.
4. Keep in-memory implementation only for tests/fakes.

Acceptance:

- Cold start + login + reconnect still work.
- Projects/sessions/inbox/timeline are fetched from real backend, not in-memory defaults.
- Approve/deny/reply/question actions hit real backend and update UI.

Verification:

- `./gradlew :shared-core:test`
- `./gradlew :android-data:testDebugUnitTest :app:testDebugUnitTest`
- `pwsh ./scripts/run-android-connected-tests.ps1 -TestClass com.yepanywhere.android.RelayLoginScreenTest`

## Batch 2 - Persistent Cache With Room

Goal:
Implement real local cache for offline read-only snapshots using Room.

Scope:

- Replace `InMemorySessionCacheStore` in production with Room-backed implementation.
- Add entities/DAO/mappers for projects, sessions, timeline messages, inbox items, pending requests.

Tasks:

1. Define Room schema and migration strategy for initial version.
2. Implement `SessionCacheStore` Room adapter.
3. Wire repositories to cache-first read + network refresh behavior.
4. Ensure pending request/session state remains consistent after process restart.

Acceptance:

- App shows cached data without network.
- After reconnect, cache is refreshed and UI updates cleanly.
- No writes are queued offline (read-only offline behavior maintained).

Verification:

- `./gradlew :android-data:testDebugUnitTest`
- Add/extend DAO and mapper tests.
- Emulator QA: kill app, relaunch offline, confirm cached lists/timeline render.

## Batch 3 - Settings + Secure Session Persistence Hardening

Goal:
Move auth/settings persistence to production-grade storage semantics.

Scope:

- Use DataStore for non-secret settings.
- Keep secret/session materials in secure storage path.
- Preserve current UX: remember URL + identity, auto-reconnect from persisted session.

Tasks:

1. Introduce DataStore-backed settings for relay URL/identity and related flags.
2. Keep secure storage boundary for sensitive data (password/session secret handling).
3. Ensure persistence survives force-stop/cold-start reliably.
4. Keep logout semantics: clear auth session, keep prefill fields.

Acceptance:

- URL and identity prefill after restart.
- Valid persisted session reconnects automatically.
- Broken persisted session falls back to login without app lockup.

Verification:

- `./gradlew :app:testDebugUnitTest :android-data:testDebugUnitTest`
- `pwsh ./scripts/test-broken-relay-session.ps1`
- `pwsh ./scripts/restore-relay-auth-state.ps1`

## Batch 4 - Push Integration To Production Path (FCM + Route + Resync)

Goal:
Complete push-driven reopen path per MVP with production delivery channel.

Scope:

- FCM receiver/service integration.
- Notification tap routing to inbox/session.
- Resume auth/session then fetch sensitive content.

Tasks:

1. Add FCM service plumbing and token lifecycle handling.
2. Ensure notification metadata-only payload contract.
3. On tap: route to target section, restore session/auth if needed, refresh target data.
4. Keep existing route parser behavior and extend tests as needed.

Acceptance:

- Push -> tap -> app opens correct target section.
- Sensitive content is fetched only after secure resume.
- Session/inbox invalidation works with reconnect path.

Verification:

- Unit tests for routing + handler.
- Instrumentation route tests + emulator manual push simulation.

## Batch 5 - Navigation + UX Stabilization For Supervisor MVP

Goal:
Stabilize app shell flow and remove remaining transition artifacts.

Scope:

- Finalize auth shell transition behavior.
- Harden section switching and deep-link route handoff.
- Add minimal loading/error states where missing.

Tasks:

1. Review and normalize startup/auth/shell transition states.
2. Add explicit empty/loading/error states for projects/sessions/inbox/timeline.
3. Confirm active session commands are disabled correctly during reconnect/disconnect.
4. Add regression tests for startup and section-routing edges.

Acceptance:

- No visible unintended intermediate screens during startup.
- Section routing is deterministic after notification/deeplink.
- UX states are consistent across reconnect/disconnect/offline.

Verification:

- `./gradlew :app:testDebugUnitTest`
- `pwsh ./scripts/run-android-connected-tests.ps1`

## Batch 6 - MVP Acceptance Pass + CI Guardrails

Goal:
Close MVP with executable acceptance coverage and stable CI entry points.

Scope:

- Map each acceptance scenario from the base plan to testable checks.
- Add repeatable emulator-centric instrumentation command(s) to CI docs/workflow.

Tasks:

1. Build acceptance checklist from `2026-04-22-native-android-stack-plan.md`.
2. Tie each item to automated or manual emulator verification.
3. Add CI job (or documented command path) for emulator-only Android instrumentation.
4. Freeze release-ready checklist for Android Supervisor MVP.

Acceptance:

- Every MVP scenario has explicit pass criteria and verification path.
- Emulator-only instrumentation is reproducible and documented.
- No dependency on physical devices for default verification.

Verification:

- End-to-end acceptance run on emulator.
- CI dry run for Android test entrypoint(s).

## Suggested Execution Order

1. Batch 1
2. Batch 2
3. Batch 3
4. Batch 4
5. Batch 5
6. Batch 6

## Definition Of Done For Entire Plan

- Relay-first auth/reconnect is stable across cold starts.
- Supervisor flows (projects, sessions, active session actions, inbox) run on real data pipeline.
- Offline read-only cache behavior is production-backed (Room), not in-memory.
- Push reopen flow is production-ready (FCM + secure resume + correct routing).
- Emulator-first automated verification is in place and documented.
