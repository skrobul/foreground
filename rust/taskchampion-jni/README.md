# TaskChampion JNI Prototype for Android

This is a prototype Rust cdylib that demonstrates embedding TaskChampion 3.1.0 in Android applications via JNI.

## Structure

This crate implements a narrow JNI interface to TaskChampion that:
- Provides a service-level handle management system
- Implements minimal methods for initialization, queries, mutations, synchronization, and cleanup
- Uses Tokio runtime to manage asynchronous operations on a dedicated worker thread
- Follows the Android embedding guidance from the research document

## Build Process

The build process demonstrates how to cross-compile this code for Android ARM64 architecture:

1. Install required Rust toolchain (1.91.1) and Android NDK r30 target
2. Set environment variables pointing to the Android NDK toolchain
3. Run the provided build script to compile for `aarch64-linux-android`

## Implementation Details

This prototype follows the research document's guidance:
- Uses TaskChampion 3.1.0 with pinned features: `storage-sqlite`, `bundled`, `server-sync`, `tls-webpki-roots`
- Cross-compiles using `aarch64-linux-android` target
- Implements a single-threaded service model with Tokio runtime for async operations
- Uses minimal JNI surface as recommended in the research document

## License

This prototype is provided under MIT license terms consistent with the upstream TaskChampion and dependencies.