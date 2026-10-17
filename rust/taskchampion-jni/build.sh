#!/bin/bash

# This script demonstrates how to cross-compile a Rust cdylib for Android arm64
echo "Setting up Android cross-compilation environment..."

# Set variables (you'll need to adjust paths as needed)
export ANDROID_NDK_HOME="/opt/android/ndk/30.0.16248370"
export TOOLCHAIN="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="${TOOLCHAIN}/bin/aarch64-linux-android21-clang"
export CC_aarch64_linux_android="${TOOLCHAIN}/bin/aarch64-linux-android21-clang"
export AR_aarch64_linux_android="${TOOLCHAIN}/bin/llvm-ar"

# Install the target
rustup target add aarch64-linux-android

# Set rustc flags for cross-compilation
export RUSTFLAGS="-C link-arg=-fuse-ld=lld -C link-arg=-Wl,--build-id=sha1 -C link-arg=-Wl,--hash-style=gnu"

echo "Cross-compiling Rust project for aarch64-linux-android..."
cargo +1.91.1 build --target aarch64-linux-android --release --locked

echo "Build completed. The resulting .so file is in target/aarch64-linux-android/release/libforeground_taskchampion.so"