# Android Parity Gaps

## Session Input

- Speech input is intentionally omitted from the current native Android session input surface. The shared Compose shell now exposes draft, queued/deferred messages, attachments, upload progress, hold, and stop controls, but voice capture needs Android `SpeechRecognizer` or a platform speech intent wired through the app layer. Add it when the Android UI moves from shared controls to an Activity-backed input launcher.
- Native file selection is wired through Android's document picker for attachment chips. Full relay upload streaming is still a platform gap: selected documents are represented with Android content URIs until the native client implements the web upload WebSocket protocol and receives server-side upload paths.
