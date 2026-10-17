# Find maintainable private CA integration

Type: research
Status: resolved
Blocked by:

## Question

Using TaskChampion 3.1.0, reqwest/rustls, Android trust APIs, and upstream issue/source history, what is the smallest maintainable way for Foreground to add one user-supplied PEM/DER CA trust anchor while preserving normal hostname and certificate verification? Compare an upstream API contribution, a narrowly patched/forked TaskChampion dependency, and a custom `Server` implementation; identify exact code/API changes, security constraints, testing needs, and upgrade burden.

## Answer

Add an upstreamable DER-only custom-root variant to TaskChampion's existing remote server config. The three-file patch feeds one `reqwest::Certificate` through the current HTTP-client constructor using `add_root_certificate` and `https_only(true)`. Keep `tls-webpki-roots`; never disable hostname or certificate verification. Carry the identical patch in a Foreground-controlled dependency pinned to a full commit until upstream releases it.

Foreground imports one bounded PEM or DER certificate, requires exactly one certificate, normalizes it to DER, stores it atomically in `noBackupFilesDir`, and records its SHA-256 fingerprint. Candidate configuration replaces active configuration only after parse and TLS preflight succeed.

Reject a custom TaskChampion `Server`: relevant HTTP and encryption internals are private, forcing duplication of protocol and security-sensitive crypto. Required tests cover wrong CA, hostname, validity, malformed chains, HTTP/downgrade redirects, CA rotation, and successful private/public trust without mutating replica state on failure.

Full findings: [Private CA integration for TaskChampion 3.1.0](../research/private-ca-integration.md).
