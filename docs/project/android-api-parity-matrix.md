# Android API Parity Matrix

Source: `packages/client/src/api/client.ts` as of 2026-04-26.

Android transport rule: Android stays relay-first, but canonical paths are the same `/api/...` paths used by the web client. The encrypted relay request wrapper adds `/api` when the caller passes a bare path.

## Status Legend

- Implemented: usable from Android runtime today.
- Partial: a simplified Android path exists, but the web response/action semantics are incomplete.
- Missing: no Android repository/use case/screen integration yet.
- Android-only: native relay/push plumbing with no web API equivalent.
- Excluded: browser/PWA-only surface that should not be implemented as-is on Android.

## Canonical DTO Lock

The first DTO set for parity work lives in `packages/android-app/shared-core/src/commonMain/kotlin/com/yepanywhere/android/core/model/AndroidApiDtos.kt` and is covered by `AndroidApiDtosTest`.

Locked response families:

- Projects: `ProjectsResponseDto`, `ProjectResponseDto`, `ProjectDto`
- Sessions: `GlobalSessionsResponseDto`, `GlobalSessionItemDto`, `SessionDetailResponseDto`, `SessionMetadataResponseDto`, `SessionDto`
- Inbox: `InboxResponseDto`, `InboxItemDto`
- Messages: `MessageDto`, `MessageContentBlockDto`, `InputRequestDto`, `PaginationInfoDto`, `SlashCommandDto`
- Settings: `ServerSettingsResponseDto`, `ServerSettingsDto`, `NewSessionDefaultsDto`

## Endpoint Matrix

| Web API method | Path | Android status | Notes |
| --- | --- | --- | --- |
| `getVersion` | `GET /version` | Missing | Needed for capability gating and resume protocol warnings. |
| `getServerInfo` | `GET /server-info` | Missing | Local Access settings. |
| `getNetworkBinding` | `GET /network-binding` | Missing | Local Access settings. |
| `setNetworkBinding` | `PUT /network-binding` | Missing | Local Access settings. |
| `disableNetworkBinding` | `DELETE /network-binding` | Missing | Local Access settings. |
| `restartServer` | `POST /server/restart` | Missing | Development settings/admin action. |
| `getProviders` | `GET /providers` | Missing | Required for New Session provider/model picker. |
| `getProjects` | `GET /projects` | Partial | Runtime fetches list, but only maps id/name/active count into `ProjectSummary`. |
| `addProject` | `POST /projects` | Missing | Required by Projects parity. |
| `getProject` | `GET /projects/:projectId` | Missing | Required by project detail screen. |
| `getSession` | `GET /projects/:projectId/sessions/:sessionId` | Partial | Runtime fetches messages and pending input, but drops metadata, pagination, slash commands, ownership, model, permission mode. |
| `getSessionMetadata` | `GET /projects/:projectId/sessions/:sessionId/metadata` | Missing | Required for lightweight session refresh. |
| `getAgentSession` | `GET /projects/:projectId/sessions/:sessionId/agents/:agentId` | Missing | Agents/subagent expansion. |
| `getAgentMappings` | `GET /projects/:projectId/sessions/:sessionId/agents` | Missing | Agents/subagent expansion. |
| `startSession` | `POST /projects/:projectId/sessions` | Missing | Required by New Session flow. |
| `createSession` | `POST /projects/:projectId/sessions/create` | Missing | Required by two-phase upload flow. |
| `resumeSession` | `POST /projects/:projectId/sessions/:sessionId/resume` | Missing | Android currently only queues messages by global session id. |
| `queueMessage` | `POST /sessions/:sessionId/messages` | Implemented | `SessionsRepository.sendReply` sends `message`; attachments/temp/deferred/thinking are missing. |
| `cancelDeferredMessage` | `DELETE /sessions/:sessionId/deferred/:tempId` | Missing | Session Input parity. |
| `abortProcess` | `POST /processes/:processId/abort` | Missing | Stop/abort controls. |
| `interruptProcess` | `POST /processes/:processId/interrupt` | Missing | Interrupt controls. |
| `getProcessModels` | `GET /processes/:processId/models` | Missing | Process/model controls. |
| `setProcessModel` | `POST /processes/:processId/model` | Missing | Process/model controls. |
| `respondToInput` | `POST /sessions/:sessionId/input` | Partial | Approve/deny/question exists; `approve_accept_edits` and exact answer payload parity are missing. |
| `setPermissionMode` | `PUT /sessions/:sessionId/mode` | Missing | Session controls/header. |
| `setHold` | `PUT /sessions/:sessionId/hold` | Missing | Hold controls. |
| `getProcessInfo` | `GET /sessions/:sessionId/process` | Missing | Process info screen/modal. |
| `markSessionSeen` | `POST /sessions/:sessionId/mark-seen` | Missing | Inbox/session read state. |
| `markSessionUnread` | `DELETE /sessions/:sessionId/mark-seen` | Missing | Inbox/session read state. |
| `getLastSeen` | `GET /notifications/last-seen` | Missing | Notification/read state bootstrap. |
| `updateSessionMetadata` | `PUT /sessions/:sessionId/metadata` | Missing | Star/archive/title metadata actions. |
| `cloneSession` | `POST /projects/:projectId/sessions/:sessionId/clone` | Missing | Session overflow action. |
| `getPushPublicKey` | `GET /push/vapid-public-key` | Excluded | Browser web-push only; Android uses FCM/device registration instead. |
| `subscribePush` | `POST /push/subscribe` | Excluded | Browser web-push only. Android task 16 should use server device subscription equivalent. |
| `unsubscribePush` | `POST /push/unsubscribe` | Excluded | Browser web-push only. |
| `getPushSubscriptions` | `GET /push/subscriptions` | Missing | Useful for settings display if server models Android devices here. |
| `testPush` | `POST /push/test` | Missing | Required by notification settings. |
| `deletePushSubscription` | `DELETE /push/subscriptions/:browserProfileId` | Missing | Device/profile management, naming may need Android-specific label. |
| `getConnections` | `GET /connections` | Missing | Connected devices/remote clients. |
| `getNotificationSettings` | `GET /push/settings` | Missing | Notification settings. |
| `updateNotificationSettings` | `PUT /push/settings` | Missing | Notification settings. |
| `getFile` | `GET /projects/:projectId/files?path=...` | Missing | File viewer. |
| `getFileRawUrl` | `GET /projects/:projectId/files/raw?path=...` | Missing | Android may stream/download through relay rather than expose raw URL. |
| `expandDiffContext` | `POST /projects/:projectId/diff/expand` | Missing | Diff renderer. |
| `getGitStatus` | `GET /projects/:projectId/git` | Missing | Git status page. |
| `getGitDiff` | `POST /projects/:projectId/git/diff` | Missing | Diff viewer. |
| `getInbox` | `GET /inbox`, `GET /inbox?projectId=...` | Partial | Runtime maps all priority tiers into flat `InboxItem`; project filtering not wired. |
| `getGlobalSessions` | `GET /sessions?...` | Missing | Android currently scans per-project `/projects/:id/sessions`; global filters/pagination missing. |
| `getGlobalSessionStats` | `GET /sessions/stats` | Missing | Sessions filters and counters. |
| `getAuthStatus` | `GET /auth/status` | Missing | Direct/local auth mode; relay auth exists separately. |
| `enableAuth` | `POST /auth/enable` | Missing | Direct/local auth settings. |
| `disableAuth` | `POST /auth/disable` | Missing | Direct/local auth settings. |
| `setupAccount` | `POST /auth/setup` | Missing | Deprecated web alias; do not add unless direct auth needs migration fallback. |
| `login` | `POST /auth/login` | Missing | Direct/local auth mode. |
| `logout` | `POST /auth/logout` | Missing | Direct/local auth mode; relay logout exists separately. |
| `changePassword` | `POST /auth/change-password` | Missing | Auth settings. |
| `setLocalhostAccess` | `POST /auth/localhost-access` | Missing | Local Access settings. |
| `getRecents` | `GET /recents` | Missing | Activity/recents navigation. |
| `recordVisit` | `POST /recents/visit` | Missing | Session navigation analytics/recents. |
| `clearRecents` | `DELETE /recents` | Missing | Activity/recents settings. |
| `getOnboardingStatus` | `GET /onboarding` | Excluded | Web first-run wizard. Android should use native onboarding/local preference if needed. |
| `completeOnboarding` | `POST /onboarding/complete` | Excluded | Web first-run wizard. |
| `resetOnboarding` | `POST /onboarding/reset` | Excluded | Web first-run wizard. |
| `getBrowserProfiles` | `GET /browser-profiles` | Missing | Could back Android device/profile management if reused server-side. |
| `deleteBrowserProfile` | `DELETE /browser-profiles/:browserProfileId` | Missing | Device/profile management. |
| `getServerSettings` | `GET /settings` | Missing | Settings parity. DTO locked. |
| `updateServerSettings` | `PUT /settings` | Missing | Settings parity/defaults. |
| `getRemoteExecutors` | `GET /settings/remote-executors` | Missing | New Session/settings executor picker. |
| `updateRemoteExecutors` | `PUT /settings/remote-executors` | Missing | Settings parity. |
| `testRemoteExecutor` | `POST /settings/remote-executors/:host/test` | Missing | Settings parity. |
| `getSharingStatus` | `GET /sharing/status` | Missing | Sharing action, lower priority for Android parity. |
| `shareSession` | `POST /sharing/upload` | Missing | Sharing action, lower priority for Android parity. |
| `getDevices` / `getEmulators` | `GET /devices` | Missing | Devices page. |
| `startDevice` / `startEmulator` | `POST /devices/:id/start` | Missing | Devices page. |
| `stopDevice` / `stopEmulator` | `POST /devices/:id/stop` | Missing | Devices page. |
| `downloadDeviceBridge` / `downloadEmulatorBridge` | `POST /devices/bridge/download` | Missing | Devices page/download state. |

## Android-Only Transport Surface

| Android surface | Status | Notes |
| --- | --- | --- |
| Secure relay SRP login/resume | Implemented | `RelayAuthRepository` + secure relay state machine. |
| Encrypted relay realtime request wrapper | Implemented | `RelayRealtimeGateway.request` forwards canonical paths through the encrypted socket. |
| Activity subscription | Implemented | Used to refresh projects/sessions/inbox. |
| Session subscription | Implemented | Used to refresh the active/simple timeline. |
| FCM notification handling | Partial | Payload routing/resync exists; server-side registration/settings remain in tasks 16 and 17. |

## Immediate Follow-Up Order

1. Wire these DTOs into Android data mappers, replacing ad hoc `JsonObject` reads where the endpoint is already used.
2. Add repository interfaces for missing endpoint families before adding ViewModel/screen logic.
3. Keep browser-only web-push and onboarding endpoints out of Android unless a server contract is explicitly shared.
