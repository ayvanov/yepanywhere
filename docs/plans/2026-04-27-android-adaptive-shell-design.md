# Android Adaptive Shell Design

## Goal

Bring only the Android app shell closer to the provided desktop-style reference while keeping it adaptive for portrait phone screens.

## Scope

- Change Android app UI only.
- Do not change the web client.
- Keep existing supervisor state, callbacks, and data flow.
- Preserve current active-session actions: reply, approve, deny, and answer pending questions.

## Layout

The Android shell uses one adaptive Compose surface.

On wide or landscape screens, the app presents a desktop-like layout:

- A persistent left sidebar with top-level navigation, project/session context, and logout/settings actions.
- A large main workspace panel with a light background, subtle border, and rounded corners.
- The selected section renders inside the workspace rather than in a full-screen mobile stack.
- The active session section can show a centered prompt/composer when the session has little content, matching the spatial rhythm of the reference image.

On portrait and compact screens, the app keeps mobile ergonomics:

- The sidebar collapses away.
- Bottom navigation remains available for section switching.
- Existing scroll behavior and action controls stay reachable.
- Styling follows the same lighter shell colors used by the wide layout.

## Components

- `SupervisorShellScreen` owns the adaptive decision and delegates to wide or compact layout helpers.
- Wide layout helpers render the sidebar and workspace.
- Existing section composables continue to render projects, sessions, inbox, and active session content.
- Test tags distinguish `supervisor-shell-wide`, `supervisor-shell-compact`, `supervisor-sidebar`, and the existing scroll/reply controls.

## Testing

Add or update Android Compose tests to cover:

- Wide layout renders a sidebar and workspace.
- Compact layout keeps the existing bottom-navigation path.
- Existing active-session actions still dispatch trimmed input and clear drafts.

