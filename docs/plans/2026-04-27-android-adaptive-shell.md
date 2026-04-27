# Android Adaptive Shell Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build an Android-only adaptive supervisor shell that resembles the provided desktop reference on wide/landscape screens and remains usable on portrait phones.

**Architecture:** Keep the existing ViewModel and callback contracts intact. Refactor `SupervisorShellScreen` into adaptive layout branches backed by shared section rendering: wide screens get a persistent sidebar plus workspace, compact screens keep bottom navigation with refreshed styling.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android instrumented Compose UI tests, Gradle.

---

### Task 1: Add Layout Selection Tests

**Files:**
- Modify: `packages/android-app/app/src/androidTest/kotlin/com/yepanywhere/android/SupervisorShellScreenTest.kt`
- Modify: `packages/android-app/shared-ui/src/commonMain/kotlin/com/yepanywhere/android/ui/SupervisorShellScreen.kt`

**Step 1: Write failing tests**

Add tests that render the shell at wide and compact sizes and assert:

- Wide mode exposes `supervisor-shell-wide` and `supervisor-sidebar`.
- Compact mode exposes `supervisor-shell-compact` and keeps the scroll container.

**Step 2: Run test to verify failure**

Run:

```bash
cd packages/android-app
./gradlew connectedDebugAndroidTest --tests com.yepanywhere.android.SupervisorShellScreenTest
```

Expected: the new tag assertions fail before implementation.

### Task 2: Implement Adaptive Shell Branches

**Files:**
- Modify: `packages/android-app/shared-ui/src/commonMain/kotlin/com/yepanywhere/android/ui/SupervisorShellScreen.kt`

**Step 1: Add adaptive layout detection**

Use Compose constraints to choose wide layout when the available width can support a sidebar and workspace. Keep compact behavior for narrow portrait screens.

**Step 2: Add wide shell helpers**

Create helpers for:

- `WideSupervisorShell`
- `SidebarPanel`
- `WorkspacePanel`

Reuse the existing section rendering instead of duplicating business logic.

**Step 3: Keep compact branch functional**

Move the existing Scaffold/bottom-navigation implementation into a compact helper and apply the new shell background.

### Task 3: Tune Active Workspace Presentation

**Files:**
- Modify: `packages/android-app/shared-ui/src/commonMain/kotlin/com/yepanywhere/android/ui/SupervisorShellScreen.kt`

**Step 1: Preserve existing active session controls**

Keep reply, approval, denial, and question-answer controls in the active section.

**Step 2: Improve empty/light active state**

For wide screens, center the active session heading and composer when there are no urgent pending requests and little timeline content.

### Task 4: Verify Android App

**Files:**
- Test: `packages/android-app/app/src/androidTest/kotlin/com/yepanywhere/android/SupervisorShellScreenTest.kt`

**Step 1: Run targeted tests**

Run:

```bash
cd packages/android-app
./gradlew connectedDebugAndroidTest --tests com.yepanywhere.android.SupervisorShellScreenTest
```

**Step 2: Run compile checks**

Run:

```bash
cd packages/android-app
./gradlew testDebugUnitTest
```

**Step 3: Emulator smoke test when available**

Use `packages/android-app/local.properties` for local testing configuration and verify the shell on the Android emulator if `adb devices` shows an available target.

