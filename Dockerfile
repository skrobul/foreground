# Android build environment based on /tmp/opencode/toolchain, adjusted to use
# the Gradle 7.4 / Android Gradle Plugin 7.3.1 versions pinned by this project.
FROM rust:1.91.1

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    JAVA_HOME=/opt/jdk17 \
    PATH=/opt/jdk17/bin:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl unzip \
    && rm -rf /var/lib/apt/lists/*

# Gradle 7.4 (used by the wrapper) requires Java 17 or earlier. Pin Temurin 17.
RUN curl -fL 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz' \
      -o /tmp/jdk.tar.gz \
    && echo '3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e  /tmp/jdk.tar.gz' | sha256sum -c - \
    && mkdir -p "$JAVA_HOME" \
    && tar -xzf /tmp/jdk.tar.gz -C "$JAVA_HOME" --strip-components=1 \
    && rm /tmp/jdk.tar.gz

# Pin Android command-line tools and verify the archive checksum.
RUN mkdir -p "$ANDROID_HOME/cmdline-tools" \
    && curl -fL 'https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip' -o /tmp/cmdline-tools.zip \
    && echo '2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258  /tmp/cmdline-tools.zip' | sha256sum -c - \
    && unzip -q /tmp/cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools" \
    && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip \
    && yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null \
    && sdkmanager --sdk_root="$ANDROID_HOME" \
      'platform-tools' 'platforms;android-34' 'build-tools;34.0.0' \
      'platforms;android-30' 'build-tools;30.0.3'

# Keep the Android Rust target and NDK available for the project's JNI crate.
RUN rustup target add aarch64-linux-android \
    && curl -fL 'https://dl.google.com/android/repository/android-ndk-r30-linux.zip' -o /tmp/ndk.zip \
    && echo '5107f898313790e449e87eee2183d9a20602dee9  /tmp/ndk.zip' | sha1sum -c - \
    && unzip -q /tmp/ndk.zip -d /opt \
    && rm /tmp/ndk.zip

WORKDIR /workspace
COPY . .

# The checked-in local.properties is machine-specific and ignored by Git.
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=cache,target=/root/.cargo/registry \
    --mount=type=cache,target=/root/.cargo/git \
    --mount=type=cache,target=/root/.cache/cargo-target \
    export CARGO_TARGET_DIR=/root/.cache/cargo-target \
    && printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties \
    && ./gradlew assembleDebug assembleRelease --no-daemon

CMD ["sleep", "infinity"]
