# Private CA integration for TaskChampion 3.1.0

Research date: 2026-09-12

## Recommendation

Add one additive, DER-only custom-root path to TaskChampion's existing remote
server implementation, submit that seam upstream, and carry the identical patch
as a git dependency pinned to a full commit until an upstream release contains
it. Keep `tls-webpki-roots` enabled. The patch should call
`reqwest::ClientBuilder::add_root_certificate` before `build()` and must not call
either `danger_accept_invalid_certs` or `danger_accept_invalid_hostnames`.
Because TaskChampion itself accepts HTTP URLs and reqwest follows redirects by
default, the custom-root client path should also set `https_only(true)`; checking
only the initially entered URL would not prevent an HTTPS-to-HTTP redirect.

This is the smallest maintainable solution because it changes only construction
of the existing HTTP client. TaskChampion continues to own URL construction,
HTTP protocol behavior, encryption, and `Server` behavior. reqwest adds the
private anchor to, rather than replacing, the enabled WebPKI roots, then rustls's
normal verifier checks chain signatures, certificate time validity, DNS name,
and applicable revocation information. [TC HTTP 3.1] [reqwest client build]
[reqwest builder] [rustls verifier]

Foreground should normalize a user-selected PEM or DER certificate to exactly
one DER certificate before invoking TaskChampion. The TaskChampion API and
persisted representation should accept DER only. This keeps file-format parsing
and Android document-provider input out of the library patch and makes the
trusted bytes unambiguous.

Do not implement a custom `Server`. Although TaskChampion deliberately exposes
the four-method `Server` trait and its payload/result types, the concrete
`SyncServer`, HTTP helper, `Cryptor`, `Secret`, `Sealed`, and `Unsealed` types are
private. A downstream implementation would therefore duplicate both the HTTP
wire protocol and security-sensitive PBKDF2/ChaCha20-Poly1305 envelope code.
[TC server module 3.1] [TC server types 3.1] [TC sync 3.1]
[TC encryption 3.1]

## Verified baseline

TaskChampion 3.1.0 is commit `ddb7029882d37af8800da3a7d745f8597940dec3`.
Its lockfile resolves reqwest 0.12.28, rustls 0.23.37,
rustls-pki-types 1.14.0, rustls-webpki 0.103.13, and webpki-roots 1.0.6.
The crate manifest declares reqwest as `0.12`, disables reqwest defaults, and
maps `tls-webpki-roots` to reqwest's rustls WebPKI-root feature. [TC tag]
[TC manifest 3.1] [TC lock 3.1]

Current upstream `main` is commit
`04601336b607e5635096e84a07959a852ce55264` (2026-09-10). The relevant files
(`config.rs`, `http.rs`, `sync/mod.rs`, `encryption.rs`, `mod.rs`, `types.rs`,
and `errors.rs`) are byte-for-byte unchanged from 3.1.0. Current main is marked
`3.1.1-pre`; thus no unreleased custom-root API exists. [TC current commit]
[TC current manifest]

TaskChampion's current HTTP constructor:

- forces rustls with `use_rustls_tls()`;
- sets 10-second connect and 60-second per-read timeouts;
- applies proxy environment variables;
- turns off reqwest's bulk built-in roots, then explicitly enables either
  native or WebPKI roots according to TaskChampion features;
- builds a private `reqwest::Client` stored in private `SyncServer`.

`ServerConfig::Remote` has only `url`, `client_id`, and `encryption_secret`.
There is no certificate, client, TLS-config, or root-store injection point.
[TC HTTP 3.1] [TC config 3.1] [TC sync 3.1]

Upstream issue #460 requested optional system roots and explicitly retained
bundled roots as the default. PR #468 implemented the native-roots feature.
Later issue #635 and PR #645 consolidated remote implementations on reqwest,
specifically noting that one client simplifies TLS certificate configuration.
As of the research date, searching all current issues/PRs found no custom-root,
private-CA, or self-signed-certificate proposal; the only open PRs are #729 and
#770, neither TLS-related. [TC issue 460] [TC PR 468] [TC issue 635]
[TC PR 645] [TC open PRs]

## Why one reqwest root is safe

In reqwest 0.12.28, `add_root_certificate` appends to a `root_certs` vector.
During rustls client construction reqwest creates an empty `RootCertStore`, adds
every custom certificate with the strict single-certificate
`RootCertStore::add`, then extends that same store with
`webpki_roots::TLS_SERVER_ROOTS` when WebPKI roots are enabled. It finally gives
that store to rustls's standard client configuration. This is additive trust,
not pinning and not replacement of public roots. [reqwest builder]
[reqwest client build] [rustls roots]

reqwest's dangerous verification switches both default to `false`. Its source
selects the normal root-certificate verifier unless certificate verification or
hostname verification has explicitly been disabled. The normal rustls
`WebPkiServerVerifier` documents checks for a chain to a configured trust
anchor, expiry, DNS identity, and applicable revocation status. No custom
`ServerCertVerifier` is needed or desirable here. [reqwest builder]
[reqwest client build] [rustls verifier]

Important scope: adding a CA is not certificate/public-key pinning. Any server
certificate that chains to that CA and is valid for the requested hostname can
authenticate. That is the intended private-PKI policy. Also, default rustls does
not gain active revocation fetching merely because a root is added; reqwest
supports configured CRLs, but TaskChampion does not configure one. This proposal
retains existing revocation behavior rather than claiming stronger behavior.
[reqwest builder] [rustls verifier]

## Exact upstream/pinned patch

Prefer an additive public entry point so existing `ServerConfig::Remote`
constructors remain source-compatible. The smallest explicit API is a new
non-WASM `ServerConfig` variant:

```rust
RemoteWithCustomRootCertificate {
    url: String,
    client_id: Uuid,
    encryption_secret: Vec<u8>,
    root_certificate_der: Vec<u8>,
}
```

Exact production changes:

1. `src/server/config.rs`: add the variant under `server-sync` and one
   `into_server` match arm calling a new private constructor. Use DER in the API;
   do not add a path, PEM auto-detection, `reqwest::Client`, or rustls type to
   TaskChampion's public API.
2. `src/server/sync/mod.rs`: add
   `SyncServer::new_with_root_certificate(url, client_id, secret, der)`. Parse
   with `reqwest::Certificate::from_der(&der)`, then use the HTTP helper below.
   Factor common URL/cryptor/field initialization only if needed to avoid
   duplicating `new`; leave all four `Server` methods untouched.
3. `src/server/http.rs`: keep `client()` for all existing call sites and make it
   delegate to a new private helper accepting `Option<reqwest::Certificate>`.
   After root-feature selection, use
   `if let Some(c) = certificate { client.add_root_certificate(c).https_only(true) } else { client }`,
   then `build()`.
   Existing AWS/GCP behavior and existing callers remain unchanged.
4. No manifest dependency or feature change is required: `server-sync` already
   enables private feature `http`, which enables reqwest; the selected
   `tls-webpki-roots` feature remains mandatory. [TC manifest 3.1]

Adding a field to the existing `Remote` struct variant would be fewer lines but
would break every external struct-variant constructor. Exposing `SyncServer` or
a raw `reqwest::Client` would broaden TaskChampion's public compatibility
surface and couple consumers to reqwest's version. The additive variant is less
elegant than a future remote-server builder, but it is narrow, semver-safe, and
auditable.

Submit these changes upstream first. Until merged and released, host the exact
same commit in a Foreground-controlled fork and pin it by full git revision in
`Cargo.toml`/`Cargo.lock`. Do not maintain an indefinite feature branch with
other changes, and do not vendor a copied crate directory unless the build
system requires vendoring. At every TaskChampion upgrade, rebase only this
commit, inspect the three touched files, rerun TLS tests, and remove the fork as
soon as the upstream API is released.

## Certificate input and storage boundary

Use Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT` plus
`CATEGORY_OPENABLE`) for explicit user selection and read the returned
`content://` URI through `ContentResolver`; do not pass a provider path or URI
to Rust. Android documents this intent as selecting a document represented by a
content URI. Read once into a bounded byte array (recommended maximum 64 KiB),
close the stream, and pass bytes to the Rust setup operation. [Android open]
[Android resolver]

The Rust setup boundary should:

- reject empty and over-limit input before parsing;
- if input is PEM, decode certificate sections and require exactly one;
- otherwise treat input as one DER certificate;
- normalize to DER and construct the patched TaskChampion server, forcing
  reqwest/rustls to parse the trust anchor before any setting is committed;
- reject zero/multiple certificates, malformed PEM/base64/DER, a PEM containing
  no certificate, and any root that rustls cannot add to `RootCertStore`;
- persist only normalized DER, never the original container, so an accidentally
  included private-key PEM block cannot be retained;
- compute and display/store a SHA-256 fingerprint so replacement is explicit;
- require an `https` sync URL at Foreground's boundary.

`rustls-pki-types::CertificateDer::pem_slice_iter` is the current rustls PEM API
for iterating certificate sections. reqwest's `Certificate::from_pem_bundle`
uses the same iterator, but its single `Certificate::from_pem` merely stores PEM
under rustls and parsing occurs later while building the client; additionally,
a PEM bundle can contain zero certificates. Therefore count first, normalize,
then pass DER. `RootCertStore::add`, not best-effort
`add_parsable_certificates`, is appropriate for one expected-valid anchor.
[rustls PEM] [reqwest certificate source] [rustls roots]

Store `<noBackupFilesDir>/taskchampion/ca.der` with app-private permissions and
an atomic temp-file/rename, tied to the same server configuration record. The
certificate is public data and does not need Android Keystore encryption, but
its integrity is security-sensitive. Keep the previous known-good file and
settings until parsing and a TLS preflight against the configured hostname
succeed; on failure, discard the candidate. Android's `MODE_PRIVATE` limits a
created file to the app/shared UID, and `getNoBackupFilesDir` excludes files
from automatic backup. [Android Context source] [Android Context API]

Android Network Security Configuration is not an integration shortcut. It
configures Android-aware network stacks and can reference static app resources
or platform/user stores, while TaskChampion explicitly constructs a Rust
reqwest/rustls client and its own root store. Android's own
`NetworkSecurityPolicy` says platform components honor policy and third-party
libraries are only encouraged to do so. No Java/Android trust manager is called
in this path. [Android network config] [Android policy source] [TC HTTP 3.1]

## Error behavior

Fail closed at configuration time. Return stable Foreground setup categories
such as `certificate_too_large`, `certificate_empty`,
`certificate_wrong_count`, `certificate_bad_encoding`, and
`certificate_invalid_root`; do not replace current configuration on any of
these. Client construction must happen before persistence because, with the
rustls backend, reqwest's `Certificate::from_der` stores bytes and
`ClientBuilder::build` performs `RootCertStore::add`. [reqwest certificate
source] [reqwest client build]

At connection time, wrong CA, wrong hostname, expired/not-yet-valid leaf,
unusable chain, and TLS handshake failures must stop sync. TaskChampion 3.1.0
converts a reqwest error without HTTP status to `Error::Server(err.to_string())`;
it does not preserve a typed TLS error in its public error enum. Foreground
should map that to a generic actionable message such as "TLS certificate
verification failed; check server name, clock, and selected CA", while retaining
the non-secret cause for diagnostics. Never retry by disabling checks. HTTP
status errors remain TaskChampion server errors and are not certificate
failures. [TC errors 3.1]

A preflight succeeds when an HTTPS request reaches any HTTP response, including
an expected 4xx from a harmless endpoint; TLS has already authenticated by then.
Network failure is not trust success. If offline setup must be supported, allow
the parsed candidate to remain staged but clearly unverified and do not replace
the active configuration until an online preflight succeeds.

## Required tests

Patch/unit tests:

- valid DER constructs the client; valid PEM-to-DER normalization produces the
  same stored DER;
- malformed PEM, malformed DER, empty input, key-only PEM, multiple
  certificates, and over-limit input fail without changing active state;
- existing `ServerConfig::Remote` remains source-compatible and unchanged;
- existing proxy, native-root, WebPKI-root, and HTTP protocol tests still pass.

TLS integration tests should run a local rustls test server with generated
fixtures and prove all of these independently:

- certificate signed by selected private CA and correct DNS SAN succeeds;
- same server with no CA or wrong CA fails;
- correct CA with wrong hostname fails;
- expired and not-yet-valid server certificates fail;
- malformed/incomplete chain fails;
- an `http://` base URL and an HTTPS-to-HTTP redirect fail on the custom-root
  path;
- adding the private CA does not break a public-WebPKI HTTPS endpoint;
- both PEM and DER import paths reach the same successful sync protocol flow;
- none of the negative cases can mutate replica sync state.

Android instrumentation/acceptance tests should select a document-provider
certificate, kill/restart the process, confirm normalized DER reload, confirm
failed replacement preserves the old anchor, and run private-CA and public-CA
syncs on-device. Also test provider read failure, oversized streams, cancellation,
clock skew, server certificate rotation under the same CA, and CA rotation.

## Option comparison

| Option | Initial scope | Security/maintenance result | Decision |
|---|---|---|---|
| Upstream additive API | Three production files plus focused tests; no protocol or crypto changes | Best long-term ownership; API can be reviewed once and shipped to all users | Pursue now |
| Narrow pinned patch/fork | Same three-file patch, pinned full revision | Small and auditable, but Foreground must rebase and monitor security updates until release | Temporary delivery mechanism |
| Custom `Server` | New reqwest client plus four protocol methods, headers/status/content types, encryption derivation/envelopes, test vectors, and error mapping | Duplicates private security/protocol code and must track every upstream protocol/crypto change | Reject |

The custom `Server` route is technically possible: `Server`, `VersionId`,
`HistorySegment`, `Snapshot`, `AddVersionResult`, `GetVersionResult`, and
`SnapshotUrgency` are public. But TaskChampion's HTTP implementation is about
240 production lines and encryption implementation about 200 production lines,
and neither implementation's internals are public. Reuse would require upstream
visibility changes roughly equivalent to, but broader than, the proposed root
seam. [TC server types 3.1] [TC sync 3.1] [TC encryption 3.1]

## Upgrade burden and acceptance gate

For each pinned TaskChampion or reqwest update:

1. Verify the patch still applies only to `config.rs`, `sync/mod.rs`, and
   `http.rs`.
2. Inspect reqwest root-store composition and dangerous-verifier defaults;
   these are implementation details beneath stable builder APIs.
3. Refresh generated TLS fixtures and run all positive/negative TLS tests on
   host and Android.
4. Review TaskChampion issues, PRs, release notes, lockfile security changes,
   and protocol/encryption diffs.
5. Confirm WebPKI roots remain enabled and public HTTPS still succeeds.

Production acceptance requires a pinned patch commit, upstream PR, passing
negative hostname/time/CA tests, successful private and public HTTPS on Android,
atomic certificate replacement, and an audit showing no trust-all or custom
verifier path. Upstream rejection does not justify a custom `Server`; retain the
narrow fork unless upstream offers an equally narrow client/root injection API.

## Sources

- [TC tag]: https://github.com/GothenburgBitFactory/taskchampion/commit/ddb7029882d37af8800da3a7d745f8597940dec3
- [TC manifest 3.1]: https://github.com/GothenburgBitFactory/taskchampion/blob/v3.1.0/Cargo.toml
- [TC lock 3.1]: https://github.com/GothenburgBitFactory/taskchampion/blob/v3.1.0/Cargo.lock
- [TC config 3.1]: https://github.com/GothenburgBitFactory/taskchampion/blob/v3.1.0/src/server/config.rs
- [TC HTTP 3.1]: https://github.com/GothenburgBitFactory/taskchampion/blob/v3.1.0/src/server/http.rs
- [TC sync 3.1]: https://github.com/GothenburgBitFactory/taskchampion/blob/v3.1.0/src/server/sync/mod.rs
- [TC encryption 3.1]: https://github.com/GothenburgBitFactory/taskchampion/blob/v3.1.0/src/server/encryption.rs
- [TC server module 3.1]: https://github.com/GothenburgBitFactory/taskchampion/blob/v3.1.0/src/server/mod.rs
- [TC server types 3.1]: https://github.com/GothenburgBitFactory/taskchampion/blob/v3.1.0/src/server/types.rs
- [TC errors 3.1]: https://github.com/GothenburgBitFactory/taskchampion/blob/v3.1.0/src/errors.rs
- [TC current commit]: https://github.com/GothenburgBitFactory/taskchampion/commit/04601336b607e5635096e84a07959a852ce55264
- [TC current manifest]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/Cargo.toml
- [TC issue 460]: https://github.com/GothenburgBitFactory/taskchampion/issues/460
- [TC PR 468]: https://github.com/GothenburgBitFactory/taskchampion/pull/468
- [TC issue 635]: https://github.com/GothenburgBitFactory/taskchampion/issues/635
- [TC PR 645]: https://github.com/GothenburgBitFactory/taskchampion/pull/645
- [TC open PRs]: https://github.com/GothenburgBitFactory/taskchampion/pulls?q=is%3Apr+is%3Aopen
- [reqwest builder]: https://docs.rs/reqwest/0.12.28/reqwest/struct.ClientBuilder.html#method.add_root_certificate
- [reqwest client build]: https://docs.rs/crate/reqwest/0.12.28/source/src/async_impl/client.rs
- [reqwest certificate source]: https://docs.rs/crate/reqwest/0.12.28/source/src/tls.rs
- [rustls verifier]: https://docs.rs/rustls/0.23.37/rustls/client/struct.WebPkiServerVerifier.html
- [rustls roots]: https://docs.rs/rustls/0.23.37/rustls/struct.RootCertStore.html
- [rustls PEM]: https://docs.rs/rustls-pki-types/1.14.0/rustls_pki_types/pem/trait.PemObject.html
- [Android open]: https://developer.android.com/reference/android/content/Intent#ACTION_OPEN_DOCUMENT
- [Android resolver]: https://developer.android.com/reference/android/content/ContentResolver#openInputStream(android.net.Uri)
- [Android network config]: https://developer.android.com/privacy-and-security/security-config
- [Android policy source]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/security/NetworkSecurityPolicy.java
- [Android Context source]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/content/Context.java
- [Android Context API]: https://developer.android.com/reference/android/content/Context#getNoBackupFilesDir()
