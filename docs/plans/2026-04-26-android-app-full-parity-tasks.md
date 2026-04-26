# Android App Full Parity Task Plan

## Summary

Goal: turn `packages/android-app` from the current Supervisor MVP into a native Android client with practical feature parity against `packages/client`.

Current Android state:

- Implemented: native Gradle/Compose modules, relay login/reconnect/logout, SRP/session resume, encrypted realtime relay WebSocket, Room cache, FCM service, notification routing/resync, projects/sessions/inbox/active-session shell, reply/approve/deny/answer flows, unit and instrumentation test coverage.
- Still partial: Android UI is a simplified four-tab shell; session timeline renders plain text only; only a small subset of web API surface is wired.
- Existing dirty files to preserve: `packages/android-app/app/src/androidTest/kotlin/com/yepanywhere/android/SupervisorShellScreenTest.kt`, `packages/android-app/shared-ui/src/commonMain/kotlin/com/yepanywhere/android/ui/SupervisorShellScreen.kt`.

## Key Changes

- Expand Android domain models to match web/shared session data: provider, model, ownership, process state, permission mode, pending input type, context usage, unread/star/archive metadata, message content blocks, tool calls/results, files, git status, devices, settings.
- Keep Android relay-first by default, but reuse the same `/api/...` paths as web through the encrypted relay transport.
- Add Android repositories/use cases for missing web features instead of placing endpoint logic directly in ViewModels.
- Replace the current single active-session binding with real navigation to project/session-specific detail screens.
- Keep platform-native UX, but match web behavior and data semantics.

## Task Backlog

1. **Shared API Contract Audit**
   - Create an Android parity matrix from `packages/client/src/api/client.ts`.
   - Mark each endpoint as implemented, missing, Android-only, or intentionally excluded.
   - Lock canonical Kotlin DTOs for project/session/inbox/message/settings responses.

2. **Navigation Shell**
   - Replace the four-section placeholder shell with a real navigation graph: Projects, Sessions, Agents, Inbox, Settings, Session Detail, New Session, File, Git Status, Devices, Activity.
   - Preserve notification deep-link routing into Inbox or Session Detail.
   - Add back-stack behavior and route arguments for `projectId`, `sessionId`, file path, and device id.

3. **Projects Parity**
   - Add project detail fetch and add-project by path.
   - Match web sorting: needs-attention first, then recent activity.
   - Show active/thinking/attention counts per project.

4. **Global Sessions Parity**
   - Implement `/sessions` list with project/search/status/provider/executor/age filters.
   - Add pagination/load-more.
   - Add star/archive/read-unread metadata actions.
   - Add long-press selection and bulk archive/star/read actions.

5. **New Session Flow**
   - Add native new-session screen with project picker, provider picker, model picker, permission mode, thinking setting, executor picker, prompt draft, and start action.
   - Support both direct `startSession` and two-phase `createSession` + upload + `queueMessage`.
   - Save defaults through server settings.

6. **Session Detail Core**
   - Navigate to real `projectId/sessionId`.
   - Fetch full session, metadata, pagination, pending input request, slash commands, ownership, process state, permission mode, and model.
   - Add refresh/reconnect behavior equivalent to web `useSession`.

7. **Message Rendering**
   - Replace plain-text timeline with typed content block rendering.
   - Support text, thinking, tool use, tool result, bash output, edit/read/write, web search/fetch, task/subagent, todo/update-plan, image/file/document blocks, fallback renderer.
   - Add expand/collapse behavior for long tool output and diffs.

8. **Session Input**
   - Add queued/deferred messages, cancel deferred message, optimistic pending messages, draft persistence, attachment chips, upload progress, and paste/file attach.
   - Add Android speech input if available; otherwise omit voice as platform-dependent with a documented gap.
   - Add stop/interrupt/abort and hold controls.

9. **Approvals And Questions**
   - Extend current approve/deny/answer flow to support `approve_accept_edits`.
   - Match web payload shape for `respondToInput`.
   - Keep actions disabled while disconnected/syncing.

10. **Process And Model Controls**
    - Add process info screen/modal using `/sessions/:id/process`.
    - Add process model list and switch action.
    - Display provider/model/thinking state consistently in session headers and lists.

11. **Agents Page**
    - Add global active agents page from activity/session data.
    - Add lazy-loaded subagent content through `/agents` mappings and agent session fetch.
    - Reuse task renderers in session detail.

12. **Inbox Parity**
    - Preserve current inbox, then add project filtering and web priority tiers: needs attention, active, recent activity, unread 8h, unread 24h.
    - Mark session seen/unread from Android where applicable.

13. **Files And Git**
    - Add file viewer with raw/highlighted content fetch.
    - Add git status page, file list, diff viewer, expanded diff context.
    - Support file path links from message renderers.

14. **Devices Page**
    - Add devices/emulator list, start/stop, bridge download state.
    - Add stream screen only if Android-native rendering is feasible; otherwise record as manual parity exception.

15. **Settings Parity**
    - Add settings categories for Appearance, Notifications, Providers, Model defaults, Remote Access, Local Access, Remote Executors, Devices, Development, Agent Context, Lifecycle Webhooks, About.
    - Wire each category to existing server endpoints and Android-local preferences where web uses localStorage.

16. **Notifications And Push Registration**
    - Finish FCM token registration with the server-side push/device subscription model.
    - Add notification settings screen and test push action.
    - Keep payload metadata-only; fetch sensitive content after secure resume.

17. **Diagnostics And Client Logs**
    - Add Android client log collector equivalent to web remote log collection.
    - Include connection, relay, notification, upload, and renderer errors.
    - Send logs to `/api/client-logs` with Android device metadata.

18. **Offline Cache Hardening**
    - Extend Room cache schema for full project/session/message/inbox/settings snapshots.
    - Cache list/detail screens read-only while offline.
    - Do not queue offline writes in this phase.

19. **Compatibility And Capability Gating**
    - Read `/version` capabilities and gate unsupported features.
    - Show update-required warning for old relay resume protocol versions.
    - Handle missing endpoints gracefully with disabled controls and actionable errors.

20. **Acceptance And Release Readiness**
    - Update Android acceptance checklist to cover every parity area.
    - Add emulator-first instrumentation flows for login, reconnect, notification tap, session detail, new session, approvals, settings, and offline cache.
    - Add CI/documented commands for unit tests and selected connected tests.

## Test Plan

- Run Android unit tests:
  - `cd packages/android-app && ./gradlew :shared-core:test`
  - `cd packages/android-app && ./gradlew :android-data:testDebugUnitTest :app:testDebugUnitTest`
- Run connected emulator tests:
  - `pwsh ./scripts/run-android-connected-tests.ps1`
- Add focused tests per feature:
  - DTO/mapper tests for every new API response.
  - Repository tests with fake relay gateway.
  - ViewModel tests for loading, error, offline, and command states.
  - Compose tests for navigation, filters, session input, approvals, renderers, and settings.
- Manual emulator acceptance:
  - relay login and cold-start restore
  - create session, attach file, send reply, queue while running
  - approve/deny/question
  - notification tap to target session
  - offline launch with cached lists/detail
  - settings changes reflected after app restart

## Assumptions

- "Full implementation" means practical parity with `packages/client`, not only the older Supervisor MVP plan.
- Relay-first remains the Android default; direct LAN/local-auth mode is lower priority unless explicitly required.
- Android may use native UI patterns, but server API behavior must match web.
- Browser-only/PWA features are implemented as Android-native equivalents where possible and documented as exceptions where not possible.
- Existing uncommitted Android changes are treated as user work and must not be reverted.

