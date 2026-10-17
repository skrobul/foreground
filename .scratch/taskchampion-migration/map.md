# Replace taskd with TaskChampion sync

Type: wayfinder:map
Status: open

## Destination

An implementation-ready specification for replacing Foreground's Taskwarrior 2/taskd client with an Android 16+, arm64 TaskChampion replica that synchronizes with an existing self-hosted `taskchampion-sync-server`.

## Notes

- Planning only; implementation begins after this map resolves.
- Remove taskd support completely. Do not support cloud or object-storage backends.
- Embed the official TaskChampion core when feasible; correctness and implementation simplicity outrank language preference.
- The TaskChampion replica is Foreground's authoritative task store. Avoid mirrored or dual-write task persistence.
- Existing users opt into a destructive local reset. Never delete local state automatically; reset does not alter server history.
- First sync must support downloading an existing server history.
- Preserve current Foreground task features and unknown TaskChampion string properties; no new recurrence or dependency UI.
- Preserve manual and periodic sync. HTTPS only.
- Target Android 16/API 36 or newer and `arm64-v8a` only.
- Setup accepts sync-server URL, client UUID, and encryption secret.
- Consult `CONTEXT.md`, `docs/agents/domain.md`, and official TaskChampion sources.

## Decisions so far

- [Establish supported Android embedding path](issues/01-research-android-embedding.md): embed pinned TaskChampion 3.1.0 as a narrow arm64 Rust/JNI service owning bundled SQLite and its Tokio runtime; prove unsupported Android details in a prototype.
- [Define Taskwarrior 3 interoperability contract](issues/02-research-interoperability-contract.md): share the exact remote tuple, bootstrap an empty authoritative replica before edits, preserve the open property map, and delegate synchronization semantics to TaskChampion.
- [Decide sync-server TLS trust policy](issues/03-decide-tls-trust-policy.md): support user-supplied private/self-signed CA trust with hostname verification; never use trust-all TLS.
- [Find maintainable private CA integration](issues/10-research-private-ca-integration.md): upstream a narrow DER custom-root seam and temporarily pin the same TaskChampion patch; keep WebPKI roots and HTTPS-only verification, and reject protocol/crypto duplication.
- [Design task property projection](issues/06-design-task-property-projection.md): implement lossless projection of TaskChampion's open string-property map into Foreground's UI models to preserve all data without reconstruction or conflict layers.

## Not yet specified

<!-- Research cleared the current fog. New unknowns may surface from the Android prototype. -->

## Out of scope

- Running or administering `taskchampion-sync-server` from Foreground.
- Legacy taskd compatibility or migration of taskd synchronization history.
- Automatic preservation/import of old Foreground local tasks.
- Cloud-storage and object-storage synchronization backends.
- New Taskwarrior features or UI for recurrence and dependencies.
- Android versions below API 36 or ABIs other than `arm64-v8a`.
