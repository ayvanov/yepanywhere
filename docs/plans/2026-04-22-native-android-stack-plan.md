# Native Android Stack Plan

## Summary

Build the first native mobile client as a **Hybrid Compose Multiplatform** app focused on a **Supervisor MVP** for Android. Keep `packages/mobile` as the existing Tauri/web wrapper and do **not** evolve it into the native app. Create a new Android-native codebase under `packages/android-app` with a Gradle-based Android application and shared Kotlin modules for protocol/domain logic.

The MVP scope is:

- `relay-first` authentication and reconnect
- projects list
- sessions list
- session detail with read/reply/approve/deny/ask-user-question flows
- inbox
- push-driven reopen flow
- cached read-only offline snapshots

The MVP explicitly excludes:

- new session creation
- attachments
- emulator/devices flows
- file viewer
- git status
- broad settings parity with web
- direct-mode support in v1

## Implementation Changes

### Repo/module layout

- Keep `packages/mobile` unchanged as the current Tauri remote wrapper.
- Add a new Gradle mobile workspace at `packages/android-app`.
- Inside `packages/android-app`, create:
  - `app`: Android application module
  - `shared-core`: Kotlin Multiplatform library for protocol/domain/data contracts
  - `shared-ui`: Compose Multiplatform library for design system and pure reusable composables
  - `android-data`: Android library for Room, DataStore, secure storage, push integration helpers

### Stack decisions

- UI: `Jetpack Compose` on Android, using `Compose Multiplatform` only in a hybrid/shared-ready form.
- Design system: `Material 3` plus shared tokens/resources in `shared-ui`.
- Navigation: Android-only `Navigation Compose`.
- Presentation: Android `ViewModel` + `StateFlow`; keep screen orchestration Android-specific in v1.
- Networking/realtime: `Ktor Client` in `shared-core` for HTTP and WebSocket.
- Serialization: `kotlinx.serialization`.
- Async/state streams: `kotlinx.coroutines` + `Flow`.
- Local cache: `Room` in `android-data`.
- Settings: `DataStore`.
- Secure session persistence: Android secure storage / Keystore-backed secret handling.
- Background work: `WorkManager`.
- Push: `Firebase Cloud Messaging`.
- DI: start with manual composition/AppContainer; do not introduce Hilt/Koin in v1.

### Shared vs platform boundaries

- `shared-core` owns:
  - DTOs and protocol models
  - relay auth flow
  - SRP handshake and session resume
  - NaCl transport encryption and binary envelope logic
  - reconnect/session restoration rules
  - repositories and use cases for auth, projects, sessions, messages, inbox, approvals
- `shared-ui` owns:
  - colors, typography, spacing, icons/resources
  - pure composables such as rows, chips, badges, approval cards, message cells
- `app` owns:
  - Android lifecycle
  - navigation graph
  - ViewModels
  - deep links/intents
  - notification channels and handlers
  - FCM registration and routing
  - permission prompts and platform UX
- `android-data` owns:
  - Room entities and DAO
  - cache mappers
  - DataStore-backed preferences
  - secure relay-session persistence

### Feature slicing for v1

- `auth-relay`
  - relay login
  - stored-session restore
  - re-auth fallback
- `projects-sessions`
  - cached project/session summaries
  - refresh on enter/resume
- `session-detail`
  - cached timeline bootstrap
  - foreground realtime stream when the session screen is open
  - reply
  - approve/deny
  - ask-user-question responses
- `inbox`
  - cached snapshot
  - refresh on resume
  - notification-targeted invalidation
- `notifications`
  - FCM receives minimal metadata only
  - tap routes to inbox or session
  - app resumes auth/session, then fetches sensitive content

### Public interfaces/types to lock early

Define these interfaces in `shared-core` so Android UI and future iOS code depend on stable boundaries:

- `RelayAuthRepository`
  - `login`, `restoreSession`, `clearSession`
- `RelayConnectionClient`
  - `connect`, `disconnect`, `ensureConnected`, `sessionStream`, `inboxInvalidationStream`
- `ProjectsRepository`
  - `observeProjects`, `refreshProjects`
- `SessionsRepository`
  - `observeSessions`, `refreshSessions`, `observeSessionTimeline`, `sendReply`
- `ApprovalsRepository`
  - `observePendingApprovals`, `approve`, `deny`, `answerQuestion`
- `SessionCacheStore`
  - abstract cache contract used by `shared-core`; Android implementation lives in `android-data`

Use `Flow` for observed state and `suspend` functions for commands. Keep protocol details hidden behind repositories/clients.

## Test Plan

### Shared-core tests

- SRP login success/failure
- session resume success/failure and fallback to full auth
- transport key derivation and message envelope validation
- replay/sequence rejection behavior
- reconnect behavior after socket loss
- repository behavior for cache-first read and network refresh

### Android tests

- Room DAO and mapper tests
- DataStore/settings persistence tests
- secure-session persistence tests
- notification routing tests for session and inbox targets
- deep-link/open-app behavior after notification tap
- ViewModel tests for projects list, sessions list, inbox, and session detail

### UI/instrumentation

- Compose UI tests for:
  - projects/sessions list rendering
  - session message timeline
  - approval card actions
  - ask-user-question flow
  - disconnected/cached states
- Emulator QA for:
  - relay login
  - app restart with session restore
  - notification tap -> correct route
  - session screen foreground realtime
  - network drop -> recover -> resync

### Acceptance scenarios

- User logs in through relay and reopens app without full re-auth.
- User opens a session and sees cached messages immediately, then fresh data.
- User receives approval notification, taps it, app routes correctly, restores auth, and shows pending action.
- User can approve/deny/reply from the native app.
- Without network, the app still shows cached lists/messages but blocks network actions cleanly.

## Assumptions And Defaults

- Existing server APIs and relay protocol remain the source of truth; Android adapts to them rather than redefining protocol semantics.
- `packages/mobile` stays as a separate web-wrapper product until the native Android app proves out.
- Android is the only shipping target in phase 1; iOS is future-facing but not a delivery constraint for v1.
- Compose Multiplatform is used in a **hybrid** way, not as a full shared app shell.
- Push payloads contain only minimal metadata; sensitive content is fetched after secure app resume.
- Realtime is foreground-only in v1; background behavior is push + resync, not persistent background sockets.
- Offline behavior is cached read-only; no queued writes or offline drafts in v1.

## References

- Compose Multiplatform setup: [JetBrains docs](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-getting-started.html)
- Compose Multiplatform navigation: [JetBrains docs](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-navigation.html)
- Common ViewModel in Compose Multiplatform: [JetBrains docs](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-viewmodel.html)
- Compose Multiplatform resources: [JetBrains docs](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-resources.html)
- Ktor multiplatform client: [Ktor docs](https://ktor.io/docs/client-create-new-application.html)
- Android Navigation Compose: [Android docs](https://developer.android.com/develop/ui/compose/navigation)
- Room: [Android docs](https://developer.android.com/room)
- DataStore: [Android docs](https://developer.android.com/datastore)
- WorkManager: [Android docs](https://developer.android.com/guide/background/persistent/getting-started)
- FCM Android client: [Firebase docs](https://firebase.google.com/docs/cloud-messaging/android/client)
