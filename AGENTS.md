## Agent skills

### Issue tracker

Issues and specs tracked as local markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical roles mapped 1:1 to label strings (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at repo root. See `docs/agents/domain.md`.

### Android builds

Build debug and release APKs in the pinned Docker toolchain (`Dockerfile`):

```sh
docker build -t foreground-android-build .
container=$(docker create foreground-android-build)
docker cp "$container:/workspace/app/build/outputs/apk/debug/app-debug.apk" ./app-debug.apk
docker cp "$container:/workspace/app/build/outputs/apk/release/app-release-unsigned.apk" ./app-release-unsigned.apk
docker rm "$container"
```

The Docker image uses Java 17, the project's Gradle wrapper, and Android SDK 34; do not use system Gradle 7 from current nixpkgs (it is removed/EOL).
