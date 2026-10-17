# Prove TaskChampion on Android

Type: prototype
Status: in-progress
Blocked by: 10

## Question

Does a minimal API 36 `arm64-v8a` app successfully build, load, persist, reopen, and synchronize TaskChampion 3.1.0 through a narrow JNI bridge under the selected TLS policy, while meeting 16 KiB page, lifecycle, resource, reproducibility, and license constraints? Produce the smallest throwaway artifact that establishes a production go/no-go and exposes required boundary changes.

## Implementation Status

I have created the basic project structure for a Rust JNI wrapper around TaskChampion 3.1.0 that will be built for Android ARM64 architecture. This prototype includes:

1. Cargo.toml with pinned dependencies for TaskChampion 3.1.0, jni 0.22.4, and Tokio runtime
2. Rust toolchain file specifying Rust 1.91.1 for cross-compilation compatibility
3. Basic JNI wrapper structure implementing the minimal API surface:
   - `nativeInit` - initializes the service
   - `nativeQuery` - handles query operations
   - `nativeMutate` - handles task mutations
   - `nativeSync` - performs synchronization
   - `nativeClose` - cleans up resources
4. Build script to demonstrate the cross-compilation process for Android

## Next Steps

The prototype needs:
1. Actual implementation of the TaskChampion integration 
2. Integration with Android build system (Gradle)
3. Testing on actual Android devices/emulators
4. Verification of 16 KiB page alignment and ABI compatibility
5. Full testing against the TaskChampion sync server

Note: This is a minimal prototype that shows the structure but contains unimplemented core functionality for demonstration purposes only.