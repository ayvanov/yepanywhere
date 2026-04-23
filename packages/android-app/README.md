# Yep Anywhere Android App

Native Android client scaffold for Yep Anywhere.

## Modules

- `app` - Android application entrypoint, navigation, lifecycle, notifications
- `shared-core` - Kotlin Multiplatform protocol/domain contracts
- `shared-ui` - Compose Multiplatform design system and shared composables
- `android-data` - Android-only data/storage integration layer

## Commands

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
./gradlew test
# from repo root: picks a connected emulator and ignores physical devices by default
pwsh ./scripts/run-android-connected-tests.ps1
# single instrumentation class
pwsh ./scripts/run-android-connected-tests.ps1 -TestClass com.yepanywhere.android.RelayLoginScreenTest
```

## Notes

- This workspace intentionally coexists with `packages/mobile`, which remains the Tauri/web wrapper.
- The first scaffold focuses on module boundaries and a compileable baseline, not feature parity.

