# Establish supported Android embedding path

Type: research
Status: resolved
Blocked by:

## Question

Using current first-party TaskChampion, Rust, Android NDK, and Android Gradle sources, what is the least-error-prone supported way to embed TaskChampion 3.x in an API 36 arm64 Android app? Determine crate features and versions, Rust/NDK toolchain requirements, JNI approach, async runtime behavior, SQLite and TLS choices, filesystem assumptions, binary/license implications, and any blockers requiring a prototype.

## Answer

Use a pinned TaskChampion 3.1.0 Rust `cdylib` for `aarch64-linux-android`, with a narrow hand-written JNI JSON/DTO boundary. One Rust worker thread owns a current-thread Tokio runtime, the replica, bundled SQLite storage under `noBackupFilesDir`, and the remote server. Kotlin invokes the blocking facade from IO dispatchers.

Build with pinned Rust 1.91.1, NDK r30, API-36-compatible AGP/Gradle/JDK, and only `storage-sqlite`, `bundled`, `server-sync`, and `tls-webpki-roots`. Package source-built `arm64-v8a` output through generated `jniLibs`; verify 16 KiB pages, ABI dependencies, lifecycle, real sync, reproducibility, and licenses in a prototype.

TaskChampion has no documented Android CI target. Private/user CA trust is not supported by the minimal upstream HTTP client and needs a product decision before prototyping TLS behavior.

Full findings: [Embedding TaskChampion 3.x in Foreground on Android](../research/android-embedding.md).
