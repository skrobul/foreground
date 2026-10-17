# Embedding TaskChampion 3.x in Foreground on Android

Research date: 2026-09-12

## Conclusion

The least-error-prone path is a small, app-owned Rust `cdylib` with a narrow JNI API. Build it for `aarch64-linux-android` with pinned Rust 1.91.1 and Android NDK r30, package the resulting `.so` as `arm64-v8a`, and let a single Rust worker thread own a Tokio runtime, `Replica<SqliteStorage>`, and sync server. Kotlin should invoke the blocking JNI facade only from `Dispatchers.IO` or a WorkManager worker.

Use TaskChampion 3.1.0 with defaults disabled and only these features:

```toml
[dependencies]
taskchampion = { version = "=3.1.0", default-features = false, features = [
    "storage-sqlite",
    "bundled",
    "server-sync",
    "tls-webpki-roots",
] }
jni = { version = "=0.22.4", default-features = false }
tokio = { version = "1", default-features = false, features = ["rt", "net", "time", "sync"] }

[lib]
crate-type = ["cdylib"]
```

This is intentionally not TaskChampion's default feature set. The default `sync` feature also enables GCP, AWS, local, and Git backends. Those add a large dependency and license surface, and Git requires an external executable that an Android app cannot assume exists. The crate's exact feature graph and `cdylib` support are declared in its manifest. [TC manifest]

## Versions and supported floor

| Component | Pin/recommendation | Basis |
|---|---|---|
| TaskChampion | `3.1.0` exactly | Current crates.io stable; MIT; published MSRV is Rust 1.91.1. [TC registry] [TC docs] |
| Rust | `1.91.1` exactly initially | TaskChampion 3.1.0's declared MSRV. Install `aarch64-linux-android`; Android targets are Tier 2, include `std`, and require cross-compilation with an NDK. [TC manifest] [Rust Android] |
| JNI crate | `jni 0.22.4`, no features | Current release, MSRV 1.85, MIT OR Apache-2.0. Its `invocation` feature launches/locates a JVM and is explicitly unnecessary on Android. [JNI manifest] [JNI docs] |
| NDK | `30.0.16248370` (r30) | Current stable-channel package in Google's SDK repository; Rust supports the latest LTS NDK. [SDK repository] [Rust Android] |
| Android API | `compileSdk 36`, `targetSdk 36`; native API floor equals `minSdk` | `compileSdk`/`targetSdk` describe Java/Android behavior. The number appended to the NDK Clang driver selects the native minimum API. For this repo's current `minSdk 21`, use `aarch64-linux-android21-clang`; arm64 itself starts at API 21. [Android 16 setup] [NDK API] [local app Gradle] |
| Android Gradle Plugin | At least stable `8.9.3` for the first prototype, Gradle `8.11.1`, JDK 17 | Android's API 36 setup requires AGP 8.9.1 or newer; AGP 8.9's compatibility table specifies Gradle 8.11.1 and JDK 17. Use the last 8.9 patch to minimize migration scope rather than jumping immediately to AGP 9.x. [Android 16 setup] [AGP 8.9] |

The local project is not currently API-36-ready: it has AGP 7.3.1, Gradle 7.4, Kotlin 1.7.20, and `compileSdkVersion`/`targetSdkVersion` 34. Its `minSdkVersion 21` is suitable for `arm64-v8a`. The AGP/Gradle/JDK/Kotlin upgrade is a separate prerequisite and should be tested before adding Rust. [local root Gradle] [local wrapper] [local app Gradle]

## Build and Gradle integration

Android's supported external-native integrations are CMake and ndk-build; Cargo is not an AGP-native build model. Avoid making CMake pretend to build Rust and avoid an unpinned third-party Gradle/Cargo plugin. Build Cargo from an explicit Gradle task, then expose only its output directory as `jniLibs`. Android packages prebuilt native libraries from ABI-named `jniLibs` directories. [Android native build] [Android native dependencies]

Pin tool paths and all output-affecting inputs. A reproducible Linux build is conceptually:

```sh
rustup toolchain install 1.91.1 --profile minimal \
  --component rustfmt,clippy \
  --target aarch64-linux-android

export NDK="$ANDROID_SDK_ROOT/ndk/30.0.16248370"
export TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TOOLCHAIN/bin/aarch64-linux-android21-clang"
export CC_aarch64_linux_android="$TOOLCHAIN/bin/aarch64-linux-android21-clang"
export AR_aarch64_linux_android="$TOOLCHAIN/bin/llvm-ar"

cargo +1.91.1 build \
  --manifest-path rust/taskchampion-jni/Cargo.toml \
  --target aarch64-linux-android \
  --release --locked
```

The Gradle task should copy `target/aarch64-linux-android/release/libforeground_taskchampion.so` to a generated directory such as `build/generated/taskchampionJniLibs/arm64-v8a/libforeground_taskchampion.so`. Register Rust sources, `Cargo.toml`, `Cargo.lock`, `rust-toolchain.toml`, target triple, profile, NDK version, and native API level as inputs, and that generated tree as the output; wire the appropriate `merge<Variant>NativeLibs` task to depend on it. Gradle only gives correct up-to-date/cache behavior when all inputs and outputs are declared. [Gradle incremental]

Configure the Android module with:

```groovy
android {
    compileSdkVersion 36
    ndkVersion "30.0.16248370"

    defaultConfig {
        minSdkVersion 21
        targetSdkVersion 36
        ndk { abiFilters "arm64-v8a" }
    }

    sourceSets.main.jniLibs.srcDir(layout.buildDirectory.dir("generated/taskchampionJniLibs"))
}
```

Do not write generated libraries into `src/main/jniLibs` and do not commit them. Keep variant outputs separate so debug/release cannot contaminate each other. For an APK, `abiFilters "arm64-v8a"` deliberately makes the app unavailable to non-arm64 devices. For an App Bundle, the bundle tool still creates ABI-specific delivery, but only arm64 is present. Android maps `arm64-v8a` to AArch64; the Rust target is `aarch64-linux-android`. [NDK ABIs] [Rust Android]

Set release policy explicitly in the JNI crate, for example `panic = "abort"`, thin LTO, one codegen unit, and symbol stripping after retaining separate native debug symbols. `panic = "abort"` guarantees that Rust unwinding cannot cross JNI, but any release panic terminates the process and cannot be converted to a Java exception. In unwind-enabled development builds, jni-rs 0.22's `EnvUnowned::with_env` catches native-entry-point panics so an `ErrorPolicy` can convert them to Java exceptions or default return values. All ordinary failures should remain `Result` values and be converted without panicking in every build. [JNI docs]

## JNI seam

Use `jni` 0.22.4 directly. Do not expose TaskChampion Rust types to Kotlin, and do not introduce UniFFI unless a prototype proves the hand-written seam too large. TaskChampion's data model is string properties plus UUIDs, so a small DTO/JSON boundary is adequate and easier to audit.

Recommended native surface:

- `nativeInit(noBackupDirectory, configJson) -> long handle`
- `nativeQuery(handle, queryJson) -> resultJson`
- `nativeMutate(handle, commandJson) -> resultJson`
- `nativeSync(handle) -> resultJson`
- `nativeClose(handle)`

Prefer one exported `JNI_OnLoad` and `RegisterNatives` over many name-mangled exports. Android's JNI guidance recommends registration from `JNI_OnLoad`; it catches signature mismatches at library load, permits hidden symbol visibility, and `FindClass` in `JNI_OnLoad` uses the application's class loader. jni-rs supports `register_native_methods`. Keep the Kotlin bridge class and native method names/signatures from R8 renaming, even though this repo currently has `minifyEnabled false`. [Android JNI] [JNI docs] [local app Gradle]

Use an opaque handle backed by a checked registry, not a raw Rust pointer cast directly to `jlong`. Reject stale handles, make close idempotent, and never retain local JNI references beyond a call. Enter every native method through `EnvUnowned::with_env`; this contains panics only in unwind-enabled profiles, while an aborting release profile remains fail-stop. Convert expected domain errors to a stable result type; reserve Java exceptions for programming/setup failures.

`System.loadLibrary("foreground_taskchampion")` loads `libforeground_taskchampion.so`. Load once in the bridge object's static initializer. Do not call JNI work from static initialization beyond registration.

## Runtime and ownership

TaskChampion 3.1 is async throughout. `SqliteStorage::new`, replica reads/writes, server construction, and sync are futures. The crate depends on Tokio with `macros`, `sync`, and `rt`; enabling `server-sync` adds reqwest, whose non-WASM dependency enables Tokio `net` and `time`. Feature unification makes the required drivers available, but the embedding crate should declare its own runtime requirements rather than rely on transitive feature details. [TC manifest] [reqwest manifest] [TC Replica]

Own exactly one long-lived Rust service per Android process:

1. `nativeInit` starts one Rust worker thread.
2. That thread creates `Builder::new_current_thread().enable_all().build()` and enters `block_on` for a command loop.
3. The worker constructs and exclusively owns `Replica<SqliteStorage>` and any `Box<dyn Server>`.
4. JNI calls send typed commands and wait for a typed response.
5. `nativeClose` sends shutdown, awaits resource drop, then joins the worker off Android's main thread.

This avoids a mutex held across `.await`, concurrent mutable access to `Replica`, nested `block_on`, and accidental runtime creation per JNI call. A current-thread runtime only progresses while `block_on` drives it, which the permanent command-loop future does. Tokio documents that `Runtime::block_on` blocks its caller and that dropping a runtime can wait indefinitely for blocking work, so neither action belongs on the UI thread. [Tokio builder] [Tokio runtime]

TaskChampion's SQLite backend already creates its own dedicated OS thread and a current-thread Tokio runtime to contain rusqlite's non-`Send` connection, serializes transactions through channels, and joins that thread on drop. Thus the proposed service has one owner/runtime thread plus TaskChampion's one SQLite actor thread; do not add a connection pool. [TC storage wrapper]

Kotlin coroutines do not substitute for Tokio. Kotlin should call the synchronous JNI facade under `withContext(Dispatchers.IO)`; WorkManager sync should do likewise. Cancellation cannot safely interrupt a blocking Rust request without an explicit cancellation protocol, so treat cancellation as a prototype item rather than dropping the handle while work is live.

## SQLite and filesystem

Keep `storage-sqlite` plus `bundled`:

- TaskChampion 3.1.0 requests rusqlite `^0.39` with `fallible_uint`; its release lock resolves rusqlite 0.39.0 and `libsqlite3-sys` 0.37.0. [TC manifest] [TC lock]
- `bundled` compiles rusqlite's SQLite amalgamation into the Rust `.so`. This avoids dependence on Android's private/platform SQLite ABI and avoids version drift across API levels. rusqlite defines `bundled` exactly this way. [rusqlite manifest]
- The bundled build uses checked-in bindings, so host bindgen/libclang is not needed. `libsqlite3-sys` detects Android and sets `SQLITE_TEMP_STORE=3`, because Android does not provide SQLite a conventional temp directory. [libsqlite3 build]
- Do not use SQLCipher features: TaskChampion does not expose them, they pull a separate crypto/OpenSSL decision, and they do not replace sync protocol encryption. If at-rest database encryption becomes a requirement, it needs separate design/prototype work.

Pass a directory, not a database filename, to `SqliteStorage::new`. TaskChampion creates the directory and opens `<directory>/taskchampion.sqlite3`; it forces WAL mode, so `taskchampion.sqlite3-wal` and `taskchampion.sqlite3-shm` can coexist beside it. It uses immediate transactions and performs schema upgrades when opened read-write. [TC SQLite module] [TC SQLite inner]

Use `Context.getNoBackupFilesDir()/taskchampion` as the default directory. It is app-private and excluded from automatic backup. Restoring a replica directory and credentials onto another installation risks duplicating replica identity/state and capturing a live WAL inconsistently; `filesDir` is therefore the less safe default. If product requirements demand backup/restore, define a TaskChampion-aware export/import operation after closing the replica rather than letting Android copy live files. [Android Context]

Assumptions that must be enforced:

- One process and one live Rust service own this directory. Do not expose the replica through a second Android process, Room, or Android's SQLite APIs.
- The directory remains internal persistent storage, not cache/external/document-provider storage.
- Disk-full, read-only, schema-upgrade, WAL recovery, abrupt process death, and app upgrade are surfaced as recoverable errors.
- The database is plaintext at rest. Android file-based encryption protects app data according to device lock state, but TaskChampion itself only encrypts payloads sent to synchronization servers. [Android FBE] [TC encryption]

## TLS, roots, and secrets

Use `server-sync` and `tls-webpki-roots`, not `sync` and not `tls-native-roots`.

TaskChampion's HTTP client forces rustls, disables all built-in roots, then enables either reqwest webpki roots or native roots according to crate features. It fails compilation if HTTP is enabled without either root feature. If both are enabled, native roots win. [TC HTTP]

`tls-native-roots` does **not** mean Android `NetworkSecurityPolicy` or the Java trust manager in this graph. reqwest 0.12 implements that feature with `rustls-native-certs`; on Unix/Android, that crate probes filesystem locations through `openssl-probe`. It neither calls Android's Java certificate verifier nor consumes app Network Security Configuration. [reqwest manifest] [rustls native certs]

`tls-webpki-roots` gives deterministic Mozilla roots bundled in the binary and works without filesystem assumptions. The tradeoff is deliberate: user-installed/private CAs and Android trust-policy changes are ignored until the Rust dependency/root bundle is updated. The current root package uses `CDLA-Permissive-2.0`, so preserve its notice/license material. [webpki roots]

If private/self-hosted sync servers must trust user-installed CAs, TaskChampion 3.1.0 has no client/root injection API. `rustls-platform-verifier` demonstrates that true Android platform verification requires JVM/context initialization and a Kotlin component, but TaskChampion's reqwest client is private and does not use it. That requirement is an upstream API/fork or custom `Server` implementation, not a feature toggle. [platform verifier]

Store the sync encryption secret using Android Keystore-backed application design, retrieve it only for service initialization, pass bytes to Rust, and zero temporary JNI/Kotlin buffers where practical. TaskChampion derives a ChaCha20-Poly1305 key with PBKDF2-HMAC-SHA256 at 600,000 iterations and notes derivation costs tens of milliseconds; create/reuse the server rather than rebuilding it per sync. The sync `client_id` is the salt and must remain stable with the secret. [TC encryption] [TC server config]

## ABI, page size, and packaging checks

NDK r30 plus its Clang driver is the only native linker/toolchain input. Do not use host `cc`, Android platform SQLite, or GNU standalone toolchains. The NDK's LLVM prebuilt contains the target-prefixed Clang drivers expected by non-CMake build systems. The target driver suffix must be the app's native minimum API (`21` here), not `36`. [NDK other build systems] [NDK API]

API 36 devices may use 16 KiB pages, and current Play requirements also require 16 KiB-compatible native libraries. NDK r28 and newer link 16 KiB-aligned ELF segments by default, so r30 is the safe baseline; nevertheless verify the actual Rust `.so` and packaged APK because Cargo build scripts also compile C/assembly (`libsqlite3-sys`, `ring`). Run Android Studio's APK Analyzer and NDK `llvm-readelf -lW`; all `LOAD` segment alignment must be at least `0x4000`. Test on an arm64 16 KiB-page API 36 emulator/device. [Android page sizes]

Also verify with `llvm-readelf -dW`/`llvm-readelf -sW` that:

- only expected Android system libraries are `NEEDED`;
- no host `libsqlite3.so`, `libssl.so`, or `libstdc++.so` leaked in;
- only `JNI_OnLoad` (and unavoidable runtime symbols) is globally exported;
- APK path is `lib/arm64-v8a/libforeground_taskchampion.so`;
- release native debug symbols are retained separately for tombstone symbolication.

## Reproducibility and F-Droid

Commit Rust source, `Cargo.toml`, `Cargo.lock`, and `rust-toolchain.toml`; pin TaskChampion/JNI exactly and run Cargo with `--locked`. Pin `ndkVersion`, AGP, Gradle wrapper distribution and checksum, JDK, target triple, and native API. Use Cargo source vendoring or a locked crates.io fetch accepted by the build environment, and run an offline rebuild in CI. Record `cargo metadata`, `cargo tree -e features`, `cargo tree --target aarch64-linux-android`, and a generated license report as release artifacts.

TaskChampion and rusqlite are MIT; JNI is MIT OR Apache-2.0; ring is Apache-2.0 AND ISC and includes BoringSSL/other notices; webpki-roots is CDLA-Permissive-2.0; bundled SQLite itself is public domain, while its Rust sys wrapper is MIT. No listed license is inherently an F-Droid blocker, but the complete resolved graph must be reviewed, notices shipped, and regenerated whenever `Cargo.lock` changes. [TC manifest] [rusqlite manifest] [JNI manifest] [ring manifest] [webpki roots] [SQLite copyright]

F-Droid requires FLOSS dependencies and reproducible source builds; prebuilt Rust/rustup tools can be acceptable, but checking a precompiled `.so` into the app is the wrong path. Its builders need a deterministic command that builds the Rust library from published source without secrets or proprietary plugins. Keep AWS/GCP/Git default features disabled, both to shrink this audit and to avoid irrelevant build tools/dependencies. [F-Droid policy]

For stronger byte reproducibility, prototype path remapping (`--remap-path-prefix`), fixed locale/time zone, deterministic archive/link behavior, and two clean builds in different absolute directories. Reproducibility of the final signed APK is a separate whole-build concern; this report only establishes inputs needed for the Rust artifact.

## Prototype blockers and acceptance tests

Research establishes a credible path but not proof that TaskChampion 3.1.0 supports Android as a tested target. Upstream documents Android only through Rust's generic target support; TaskChampion's published CI/docs do not show Android builds. These items block production adoption and need a small on-device prototype:

1. **Cross-build:** build the exact minimal feature graph with Rust 1.91.1 and NDK r30 for `aarch64-linux-android`; prove bundled SQLite and ring assembly compile without host-library leakage.
2. **Load/ABI:** install an API 36 arm64 build, load with `System.loadLibrary`, register JNI methods, and inspect `logcat`/ELF dependencies.
3. **16 KiB:** inspect ELF alignment and execute on a 16 KiB-page API 36 image.
4. **SQLite lifecycle:** initialize in `noBackupFilesDir`, create/read/update tasks, close/reopen, kill process during/after commit, verify WAL recovery, and upgrade an older fixture.
5. **Runtime lifecycle:** repeat init/query/sync/close, rotate/recreate activities, cancel WorkManager, and confirm no UI blocking, deadlock, leaked worker, or indefinite runtime/SQLite-thread join.
6. **Real sync:** sync twice against the project's supported `taskchampion-sync-server` over public HTTPS; test airplane mode, timeout, server conflict, large snapshot with `avoid_snapshots = true`, and secret/client-ID persistence.
7. **Trust policy:** prove public WebPKI certificates work. Decide explicitly whether user/private CAs are a requirement; if yes, minimal TaskChampion features are insufficient and a custom server/upstream injection seam is required.
8. **Resource envelope:** measure first server construction (600,000-iteration PBKDF2), sync CPU/RSS, `.so` compressed/uncompressed size, and cold-start impact on representative arm64 hardware.
9. **Rebuild/license:** clean offline build from lock/vendor data, compare two artifacts, run license inventory, and ensure F-Droid source build contains no committed native binary.
10. **Project upgrade:** upgrade this repo from AGP 7.3.1/Gradle 7.4/API 34 to the pinned API-36-compatible toolchain without mixing that diagnosis with Rust/JNI failures.

Production go/no-go should wait for all ten. Private-CA support is the only identified likely design blocker; all other items are implementation validation.

## Sources

- [TC registry]: https://crates.io/api/v1/crates/taskchampion
- [TC docs]: https://docs.rs/taskchampion/3.1.0/taskchampion/
- [TC manifest]: https://docs.rs/crate/taskchampion/3.1.0/source/Cargo.toml.orig
- [TC lock]: https://github.com/GothenburgBitFactory/taskchampion/blob/v3.1.0/Cargo.lock
- [TC Replica]: https://docs.rs/taskchampion/3.1.0/taskchampion/struct.Replica.html
- [TC server config]: https://github.com/GothenburgBitFactory/taskchampion/blob/v3.1.0/src/server/config.rs
- [TC HTTP]: https://github.com/GothenburgBitFactory/taskchampion/blob/v3.1.0/src/server/http.rs
- [TC encryption]: https://github.com/GothenburgBitFactory/taskchampion/blob/v3.1.0/src/server/encryption.rs
- [TC SQLite module]: https://github.com/GothenburgBitFactory/taskchampion/blob/v3.1.0/src/storage/sqlite/mod.rs
- [TC SQLite inner]: https://github.com/GothenburgBitFactory/taskchampion/blob/v3.1.0/src/storage/sqlite/inner.rs
- [TC storage wrapper]: https://github.com/GothenburgBitFactory/taskchampion/blob/v3.1.0/src/storage/send_wrapper/wrapper.rs
- [Rust Android]: https://doc.rust-lang.org/rustc/platform-support/android.html
- [SDK repository]: https://dl.google.com/android/repository/repository2-3.xml
- [Android 16 setup]: https://developer.android.com/about/versions/16/setup-sdk
- [AGP 8.9]: https://developer.android.com/build/releases/past-releases/agp-8-9-0-release-notes
- [Android native build]: https://developer.android.com/studio/projects/gradle-external-native-builds
- [Android native dependencies]: https://developer.android.com/studio/build/native-dependencies
- [NDK ABIs]: https://developer.android.com/ndk/guides/abis
- [NDK API]: https://developer.android.com/ndk/guides/sdk-versions
- [NDK other build systems]: https://developer.android.com/ndk/guides/other_build_systems
- [Android page sizes]: https://developer.android.com/guide/practices/page-sizes
- [Android JNI]: https://developer.android.com/training/articles/perf-jni
- [Android Context]: https://developer.android.com/reference/android/content/Context#getNoBackupFilesDir()
- [Android FBE]: https://source.android.com/docs/security/features/encryption/file-based
- [Gradle incremental]: https://docs.gradle.org/current/userguide/incremental_build.html
- [JNI docs]: https://docs.rs/jni/0.22.4/jni/
- [JNI manifest]: https://docs.rs/crate/jni/0.22.4/source/Cargo.toml
- [Tokio builder]: https://docs.rs/tokio/1.53.1/tokio/runtime/struct.Builder.html
- [Tokio runtime]: https://docs.rs/tokio/1.53.1/tokio/runtime/struct.Runtime.html
- [reqwest manifest]: https://docs.rs/crate/reqwest/0.12.28/source/Cargo.toml.orig
- [rustls native certs]: https://docs.rs/crate/rustls-native-certs/0.8.4/source/src/unix.rs
- [platform verifier]: https://docs.rs/crate/rustls-platform-verifier/0.7.0/source/src/android.rs
- [rusqlite manifest]: https://docs.rs/crate/rusqlite/0.39.0/source/Cargo.toml.orig
- [libsqlite3 build]: https://docs.rs/crate/libsqlite3-sys/0.37.0/source/build.rs
- [ring manifest]: https://docs.rs/crate/ring/0.17.14/source/Cargo.toml.orig
- [webpki roots]: https://docs.rs/crate/webpki-roots/1.0.9/source/Cargo.toml.orig
- [SQLite copyright]: https://www.sqlite.org/copyright.html
- [F-Droid policy]: https://f-droid.org/docs/Inclusion_Policy/
- [local root Gradle]: ../../../build.gradle
- [local wrapper]: ../../../gradle/wrapper/gradle-wrapper.properties
- [local app Gradle]: ../../../app/build.gradle
