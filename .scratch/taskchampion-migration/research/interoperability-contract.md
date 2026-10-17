# Taskwarrior 3 / TaskChampion interoperability contract

Research date: 2026-09-12

## Scope and source baseline

This report defines the contract for Foreground to join an existing
`taskchampion-sync-server` history used by Taskwarrior 3. It uses only current
first-party documentation and source.

Current releases at the research date are Taskwarrior 3.5.0, TaskChampion
3.1.0, and taskchampion-sync-server 0.7.1. Taskwarrior 3.5.0 declares and locks
TaskChampion 3.1.0. Relevant post-release branch source was also checked at:

- TaskChampion `04601336b607e5635096e84a07959a852ce55264`
- Taskwarrior `ad9c6d95564f92ef7011232fa970f4d51d73361d`
- taskchampion-sync-server `3f017c13e687d9ef41e374dbf05c0605023e88bd`

Sources: [TaskChampion 3.1.0 release], [Taskwarrior 3.5.0 release],
[sync-server 0.7.1 release], [Taskwarrior TaskChampion dependency], and
[Taskwarrior Cargo lock].

## Required Foreground contract

1. Foreground must use the official TaskChampion replica and remote-server API,
   not implement the wire format itself.
2. Each Foreground installation must own a separate local replica database. It
   must not copy or share Taskwarrior's local database.
3. To join an existing history, Foreground must receive and persist the exact
   same tuple as Taskwarrior: base URL, server client UUID, and encryption-secret
   bytes.
4. The server client UUID is a **history/user ID**, not a device or replica ID.
   Every Taskwarrior and Foreground replica sharing one history uses the same
   client UUID. A new client UUID creates or selects a different history.
5. Foreground must initialize a new local replica as empty and perform its first
   sync before creating or importing any local task.
6. The TaskChampion property map is authoritative. Foreground may project known
   properties into UI fields, but must preserve every other key and value and
   issue only property-level updates for fields the user changed.
7. Foreground must represent normal user deletion by setting
   `status=deleted`, through the high-level Task API where possible. It must not
   call physical `TaskData::delete` for a normal delete action.
8. Foreground must not generate recurrence instances or rewrite recurrence,
   dependency, annotation, tag, or unknown metadata it does not own.
9. All user-visible edits must be built as `Operations` and committed exactly
   once to the replica. Sync conflicts are resolved by TaskChampion; Foreground
   must not add a second merge layer.
10. Sync errors must leave the existing local replica in place. In particular,
    no authentication, decryption, HTTP, parse, 409-divergence, or 410 error may
    trigger an automatic local reset.

The official API exposes the required remote tuple as
`ServerConfig::Remote { url, client_id, encryption_secret }` and states that
replicas sharing a history use one client ID. [TaskChampion remote config]
[TaskChampion shared client ID]

## Client configuration and identity

### Taskwarrior configuration

The corresponding Taskwarrior settings are:

```sh
task config sync.server.url '<base-url>'
task config sync.server.client_id '<uuid>'
task config sync.encryption_secret '<secret>'
```

`sync.server.origin` is only a deprecated synonym for `sync.server.url`.
Taskwarrior requires a URL scheme and requires both client ID and secret for
remote sync. [Taskwarrior sync manual] [Taskwarrior sync command]

Foreground must configure:

```rust
ServerConfig::Remote {
    url: base_url,
    client_id: parsed_uuid,
    encryption_secret: secret_bytes,
}
```

Taskwarrior passes `sync.encryption_secret` as the bytes of its C++ string.
Therefore Foreground must use the exact UTF-8 bytes entered in Taskwarrior: no
Base64/hex decoding, trimming, newline insertion, Unicode normalization, or
character-set conversion. [Taskwarrior bridge] [TaskChampion remote config]

The URL is a base URL, including any reverse-proxy path. TaskChampion adds a
trailing slash when absent and joins `v1/...` below that path. For example,
`https://host/tc` and `https://host/tc/` both address
`https://host/tc/v1/...`. [TaskChampion URL construction]

### Identity rules

- **Server client UUID:** shared by all replicas of one task history. It is sent
  as dashed hex in `X-Client-Id` on every request and also serves as the 16-byte
  PBKDF2 salt. Changing it changes both the server namespace and encryption key.
  [HTTP protocol identity] [Sync-server overview]
- **Encryption secret:** shared byte-for-byte by all replicas of that history.
  The server never receives it. A different secret under the correct client ID
  makes existing blobs fail authenticated decryption. [TaskChampion encryption]
- **Local replica:** has separate storage and synchronization state, but no
  separate remote device UUID. The protocol deliberately treats replicas of a
  history as one client. [Protocol client definition] [Replica contents]
- **Task UUID:** identifies the same logical task in every replica. Foreground
  must preserve UUIDs received from Taskwarrior and use fresh UUIDs for new
  tasks. [TaskChampion replica API]
- **Version UUID:** identifies a point in server history. It is synchronization
  metadata, generated by the server after accepting a version; it is not a task
  or replica ID. [Protocol AddVersion] [Sync-server AddVersion]

The client UUID is not, by itself, strong authentication. The server may use an
allowlist, but defaults to allowing IDs and automatically creating an unknown
client on its first `AddVersion`; production documentation recommends disabling
automatic creation. Consequently a wrong client UUID can appear to sync
successfully against a new empty history. Foreground cannot validate an empty
history tuple cryptographically; setup must make the shared tuple explicit and
must not invent a UUID when the user says they are joining existing history.
[Sync-server client header] [Sync-server client management]

## First sync and snapshots

An empty TaskChampion storage first requests the latest snapshot. If one is
available, TaskChampion decrypts and applies it, records its version as the base,
then downloads later child versions. If no snapshot exists, it starts from the
nil UUID and replays the full version chain. [TaskChampion first-sync code]
[TaskChampion snapshot model]

Therefore Foreground setup must use this order:

1. Create a new, empty local TaskChampion storage.
2. Build `ServerConfig::Remote` from the existing Taskwarrior tuple.
3. Run `Replica::sync`.
4. Only after successful sync, expose editing and task creation.

If Foreground writes local tasks before first sync, storage is no longer empty,
so snapshot bootstrap is skipped. If the server has already pruned the child of
the nil version, sync then receives HTTP 410 and cannot reconcile the local
operations. [TaskChampion first-sync code] [Snapshot pruning constraint]

Snapshots contain the complete task UUID-to-property-map state at one version,
not local undo history or unsynchronized operations. A snapshot can only be
made from a replica with no unsynchronized operations. The server requests one
after accepting `AddVersion`; the reference server requests high urgency when
none exists. `Replica::sync(..., avoid_snapshots=true)` ignores low urgency but
still honors high urgency. [Snapshot model] [TaskChampion snapshot urgency]
[Sync-server snapshot urgency] [Implemented snapshot codec]

Taskwarrior currently passes `avoid_snapshots=false`, so it responds to both low
and high urgency requests. [Taskwarrior sync command]

## Task and property semantics

A TaskChampion task is a UUID plus an arbitrary map of UTF-8 strings to UTF-8
strings. Every map, including an empty or contradictory one, is valid. Readers
must apply defaults and make a best effort rather than reject, normalize, or
repair unfamiliar data. Integer values use decimal text; task timestamps use
integer Unix epoch seconds. [TaskChampion task model]

TaskChampion's documented common keys are:

- `status`: absent means `pending`; recognized values are `pending`,
  `completed`, `deleted`, and `recurring`; unknown statuses must be retained and
  ignored rather than deleted.
- `description`, `priority`, `project`, `modified`, `entry`, `start`, `end`,
  `due`, `wait`, `scheduled`, and `until`: ordinary string properties, with the
  date-like values represented as epoch-second decimal strings in the replica.
- `tag_<tag>`: independent set element; value is semantically ignored.
- `annotation_<epoch-seconds>`: annotation text at that time.
- `dep_<task-uuid>`: independent dependency edge; value is semantically ignored.
- Taskwarrior-specific recurrence keys including `recur`, `parent`, `imask`, and
  `mask` are ordinary TaskChampion string properties.

TaskChampion's high-level source currently recognizes `due` and `priority` in
addition to the narrower key list in the Book. Taskwarrior itself supplies the
meaning for its broader property vocabulary. This is version-coupled: Foreground
must not use a closed allowlist as a persistence schema. [TaskChampion task
model] [TaskChampion high-level properties] [TaskChampion unknown status]

### Lossless unknown-property rule

Use `TaskData::iter`/`properties` to retain the complete map. To edit, load the
current `TaskData`/`Task`, append updates only for changed keys, then commit.
Never reconstruct a task from Foreground's known UI fields. `TaskData::update`
changes one key and leaves all others untouched. [TaskData API]

This matches Taskwarrior's interoperability behavior: it loads every
TaskChampion item into its task map, and when modifying a task computes
property differences rather than replacing the stored map. Its import parser
also explicitly preserves orphan/UDA properties. [Taskwarrior TaskChampion
parse] [Taskwarrior differential write] [Taskwarrior orphan preservation]

An unrecognized property is a UDA only from TaskChampion's high-level API point
of view; its value format belongs to the defining application. Foreground must
round-trip such data literally. [TaskChampion UDA semantics]

## Operations, commits, and conflicts

All state changes are operations: `Create(uuid)`, `Delete(uuid)`, and
`Update(uuid, property, value-or-null, timestamp)`. `None`/JSON `null` removes a
property. Create on an existing task, delete on an absent task, and update on an
absent task are no-ops in the abstract model. [TaskChampion sync model]

The application API mutates an in-memory `Task`/`TaskData` and accumulates
`Operations`; storage changes only when `Replica::commit_operations` is called.
Foreground should commit one operation vector per logical UI command. This
gives one local storage transaction and one undo boundary if Foreground adds an
`UndoPoint`; it does **not** make all properties one indivisible conflict unit
during sync. [Replica operation contract] [TaskChampion operation type]

Sync maintains a single branch-free version chain. It first pulls server
versions and operationally transforms local operations onto them, then proposes
a new child version. A competing writer causes HTTP 409 with the now-current
parent; TaskChampion rebases and retries. Two responses requesting the same
parent are treated as divergent history and return `Error::OutOfSync`.
[TaskChampion sync process] [TaskChampion sync implementation]

Conflict rules in current TaskChampion source are:

- Different tasks or different properties survive independently.
- Equal writes to the same property collapse.
- Different writes to the same property use the operation timestamps; later
  timestamp wins. Equal timestamp has a deterministic implementation tie-break,
  but no user-level winner guarantee should be assumed.
- Physical `Delete` wins over a concurrent `Update` to the same task.
- Operations are immutable after server acceptance.

These are operation timestamps generated from each replica's wall clock, not
the task's `modified` property. Clock skew can therefore choose a surprising
winner. Foreground should keep device time sane and must not manufacture or
rewrite operation timestamps. [TaskChampion transform source]

## Deletion and tombstones

TaskChampion has two distinct concepts:

- **Logical deletion/tombstone:** set `status=deleted`. The task and all its
  properties remain in the map; the high-level setter also adds `end` if
  absent. This is Taskwarrior's normal user-visible deletion state.
- **Physical deletion/purge:** `TaskData::delete` emits `Delete` and removes the
  entire task map. It is final local removal, not a retained tombstone.

[Task status setter] [TaskData physical deletion]

`Replica::expire_tasks` physically purges tasks whose status is `deleted` and
whose `modified` time is older than 180 days. Taskwarrior invokes this after
sync only when `purge.on-sync` is enabled; its default is off. Foreground should
not silently introduce a different purge policy. [TaskChampion expiration]
[Taskwarrior purge setting]

The current transform implementation makes physical delete beat a concurrent
update. Some TaskChampion API comments still describe a delete/update outcome
where a partial task can remain; that comment conflicts with current transform
source. Treat physical-delete concurrency as version-coupled and avoid physical
deletion except an explicit, tested purge path. [TaskChampion transform source]
[TaskData physical deletion]

## Recurrence and dependencies

TaskChampion recognizes `status=recurring` but does not implement recurrence.
Taskwarrior owns recurrence generation. A Taskwarrior recurring template uses
at least `status=recurring`, `recur`, and `due`, optionally `until`, `wait`, or
`scheduled`, plus a `mask`. Generated instances clone the template, receive a
new task UUID, become pending, set `parent` to the template UUID and `imask` to
the occurrence index, adjust date fields, and remove the template's `mask`.
Changing an instance updates the parent's mask. [TaskChampion recurrence scope]
[Taskwarrior recurrence generation] [Taskwarrior recurrence mask]

Only one Taskwarrior replica should generate recurring instances. The current
official sync manual directs users to set `recurrence on` on the primary client
and `recurrence off` on others to avoid duplicates. Since Foreground has no new
recurrence UI, it must act like a non-generating replica: preserve all recurrence
properties and task links exactly, but never create instances or modify masks.
[Taskwarrior recurrence configuration]

Dependencies are encoded as one property per edge,
`dep_<dependency-task-uuid>`. This permits concurrent edge additions/removals to
merge independently. Taskwarrior stores and enumerates these keys and rejects
self/circular dependencies when it creates them. Foreground must preserve every
`dep_` property, including malformed or dangling ones it cannot interpret; no
dependency UI means no writes to them. [TaskChampion dependency model]
[Taskwarrior dependency source]

## Error and recovery contract

### Non-destructive default

On any sync failure, retain the current replica and its pending operations,
report the failure, and allow retry after configuration/network repair. Do not
replace storage merely because decryption or parsing failed: likely causes
include a mistyped secret/client UUID, wrong reverse-proxy path, incompatible
client format, or corrupt server data.

Relevant cases:

- `404` from `GetChildVersion` means no child exists and is normal/up-to-date.
  For an unknown client it is indistinguishable from no child until an add is
  attempted. [HTTP protocol errors] [Sync-server 404 behavior]
- `409` from `AddVersion` is a race, not a user-visible failure; TaskChampion
  rebases and retries. Repetition with the same requested base becomes
  `Error::OutOfSync`. [TaskChampion HTTP client] [TaskChampion sync
  implementation]
- `410 Gone` from `GetChildVersion` means the requested base is neither present
  nor acceptable as the latest parent, commonly because old history was pruned.
  Pending operations based on it cannot be reconciled. [HTTP protocol errors]
  [Sync-server Gone decision]
- Wrong secret/client UUID or damaged ciphertext fails authenticated decryption;
  preserve local data and ask for corrected configuration. [TaskChampion
  encryption source]
- Other 4xx/5xx, malformed headers/content types, timeout, TLS, JSON, zlib, and
  storage failures are errors and must not mutate setup identity or erase the
  replica. [TaskChampion HTTP client]

### Explicit reset from server

For confirmed 410, offer a destructive **Reset local replica from server** flow:

1. Read `Replica::num_local_operations` and tell the user exactly whether
   unsynchronized changes will be discarded.
2. Require explicit confirmation if that count is nonzero. Do not silently
   replay those operations after reset; TaskChampion states they cannot be
   reconciled.
3. Keep the old replica untouched while creating a second, empty local storage.
4. Sync the empty replica with the same tuple. It downloads the latest snapshot
   and later versions.
5. Only after full success, atomically switch Foreground to the new storage;
   retain a recoverable backup until the switch is verified.

This uses the normal supported empty-replica snapshot path instead of decoding
or applying snapshots in Foreground. The protocol specification's lower-level
recovery is to replace the entire task database with a snapshot, discard local
operations, and resume at the snapshot version. [HTTP snapshot recovery]
[TaskChampion first-sync code]

**Current API gap:** TaskChampion 3.1.0's HTTP implementation special-cases 404
but not 410; 410 becomes generic `Error::Server`, while `Error::OutOfSync` is
used for repeated 409 divergence. Snapshot application is private and automatic
only for empty storage. Robust 410-specific UI therefore requires either an
upstream typed `Gone` error/API or version-coupled inspection of the generic
error. Foreground should not rely permanently on error-message text. This needs
an interoperability prototype or upstream change before implementation is
called complete. [TaskChampion HTTP client] [TaskChampion error enum]

## Protocol and version compatibility

The current HTTP protocol uses `/v1/client/...`, `X-Client-Id`, UUID version
headers, and two opaque media types. The server stores a branch-free sequence of
opaque encrypted history segments plus one opaque snapshot. It does not parse
tasks or operations. [TaskChampion HTTP protocol] [Sync-server API constants]

Encryption is envelope version byte `1`, a random 12-byte nonce, and
ChaCha20-Poly1305 ciphertext/tag. The 32-byte key is PBKDF2-HMAC-SHA256 over the
secret with the client UUID bytes as salt and 600,000 iterations. AAD is byte
`1` plus the relevant parent/snapshot version UUID. Unknown envelope versions
are rejected. [TaskChampion encryption protocol] [TaskChampion encryption
source]

There is no protocol negotiation or capability handshake. The `/v1` path and
envelope byte are the only explicit wire version markers; the server's `/`
version string is informational and the TaskChampion client does not query it.
Thus compatibility is established by testing, not negotiation.

### Specification/implementation mismatches

Foreground must not reimplement the wire codec because current first-party
documentation and implementation disagree:

- The Book says decrypted version data is a JSON array of operations. Current
  TaskChampion serializes an object: `{"operations":[...]}`.
- The Book presents a decrypted snapshot directly as a JSON UUID-to-map object.
  Current TaskChampion zlib-compresses that JSON before encryption.
- The sync-server documentation says the protocol specification is not
  battle-tested and directs implementers to the reference server to resolve
  ambiguities.

[Documented version payload] [Implemented version payload] [Implemented snapshot
codec] [Sync-server protocol warning]

TaskChampion's policy says major versions may be incompatible, minor versions
are intended to be backward-compatible, and storage will not be incompatibly
upgraded without explicit request. However, that policy file still contains
stale pre-1.0 wording despite current 3.x releases. It is not a strong protocol
compatibility guarantee. Pin Foreground to a tested TaskChampion 3.x release,
run cross-sync fixtures against the exact Taskwarrior-bundled TaskChampion and
deployed sync-server versions, and retest before any upgrade. [TaskChampion
compatibility policy]

The sync server is less coupled to task semantics because blobs are opaque, but
HTTP behavior still changes and its 0.x version communicates no SemVer stability
promise for this client contract. The server's own protocol-implementation guide
explicitly gives its implementation precedence over ambiguities. Pin deployment
tests to at least the current matrix: Taskwarrior 3.5.0 / TaskChampion 3.1.0 /
sync-server 0.7.1.

## Ambiguities and version-coupled decisions

- **HTTP 410 typing/recovery:** specified, but not represented by the current
  TaskChampion public server result or error type. Upstream API work or a tested
  adapter is needed.
- **Wire payload documentation:** history and snapshot plaintext descriptions
  omit implementation wrappers/compression. Only official TaskChampion should
  own these codecs.
- **Physical delete conflict:** current transform says delete wins, while stale
  API comments describe a partial-task outcome. Foreground should avoid this
  path and lock tests to the selected crate version.
- **Known property set:** the Book is narrower than current high-level source and
  Taskwarrior's property set. Persistence must remain an open string map.
- **Recurrence:** TaskChampion only transports it; Taskwarrior behavior and its
  primary-replica workaround define current semantics. Foreground must remain a
  passive round-tripper.
- **No identity handshake:** a wrong client ID can select/create an empty
  history. Setup UX must distinguish "join existing" from "create new" and must
  never silently generate replacement identity.

## Sources

[TaskChampion 3.1.0 release]: https://github.com/GothenburgBitFactory/taskchampion/releases/tag/v3.1.0
[Taskwarrior 3.5.0 release]: https://github.com/GothenburgBitFactory/taskwarrior/releases/tag/v3.5.0
[sync-server 0.7.1 release]: https://github.com/GothenburgBitFactory/taskchampion-sync-server/releases/tag/v0.7.1
[Taskwarrior TaskChampion dependency]: https://github.com/GothenburgBitFactory/taskwarrior/blob/ad9c6d95564f92ef7011232fa970f4d51d73361d/src/taskchampion-cpp/Cargo.toml#L11-L14
[Taskwarrior Cargo lock]: https://github.com/GothenburgBitFactory/taskwarrior/blob/ad9c6d95564f92ef7011232fa970f4d51d73361d/Cargo.lock#L2629-L2634
[TaskChampion remote config]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/server/config.rs#L39-L51
[TaskChampion shared client ID]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/server/sync/mod.rs#L28-L53
[Taskwarrior sync manual]: https://github.com/GothenburgBitFactory/taskwarrior/blob/ad9c6d95564f92ef7011232fa970f4d51d73361d/doc/man/task-sync.5.in#L42-L84
[Taskwarrior sync command]: https://github.com/GothenburgBitFactory/taskwarrior/blob/ad9c6d95564f92ef7011232fa970f4d51d73361d/src/commands/CmdSync.cpp#L59-L188
[Taskwarrior bridge]: https://github.com/GothenburgBitFactory/taskwarrior/blob/ad9c6d95564f92ef7011232fa970f4d51d73361d/src/taskchampion-cpp/src/lib.rs#L899-L909
[TaskChampion URL construction]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/server/sync/mod.rs#L28-L66
[HTTP protocol identity]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/http.md#L1-L10
[Sync-server overview]: https://github.com/GothenburgBitFactory/taskchampion-sync-server/blob/3f017c13e687d9ef41e374dbf05c0605023e88bd/docs/src/introduction.md#L16-L28
[TaskChampion encryption]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/encryption.md#L1-L38
[Protocol client definition]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/sync-protocol.md#L7-L18
[Replica contents]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/usage.md#L8-L15
[TaskChampion replica API]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/replica.rs#L17-L32
[Protocol AddVersion]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/sync-protocol.md#L64-L81
[Sync-server AddVersion]: https://github.com/GothenburgBitFactory/taskchampion-sync-server/blob/3f017c13e687d9ef41e374dbf05c0605023e88bd/core/src/server.rs#L144-L194
[Sync-server client header]: https://github.com/GothenburgBitFactory/taskchampion-sync-server/blob/3f017c13e687d9ef41e374dbf05c0605023e88bd/server/src/api/mod.rs#L36-L54
[Sync-server client management]: https://github.com/GothenburgBitFactory/taskchampion-sync-server/blob/3f017c13e687d9ef41e374dbf05c0605023e88bd/docs/src/integration/pre-built.md#L29-L40
[TaskChampion first-sync code]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/taskdb/sync.rs#L15-L28
[TaskChampion snapshot model]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/snapshots.md#L1-L12
[Snapshot pruning constraint]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/snapshots.md#L20-L29
[Snapshot model]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/snapshots.md#L31-L39
[TaskChampion snapshot urgency]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/taskdb/sync.rs#L100-L125
[Sync-server snapshot urgency]: https://github.com/GothenburgBitFactory/taskchampion-sync-server/blob/3f017c13e687d9ef41e374dbf05c0605023e88bd/core/src/server.rs#L177-L194
[TaskChampion task model]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/tasks.md#L1-L49
[TaskChampion high-level properties]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/task/task.rs#L47-L61
[TaskChampion unknown status]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/task/status.rs#L1-L35
[TaskData API]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/task/data.rs#L43-L94
[Taskwarrior TaskChampion parse]: https://github.com/GothenburgBitFactory/taskwarrior/blob/ad9c6d95564f92ef7011232fa970f4d51d73361d/src/Task.cpp#L719-L738
[Taskwarrior differential write]: https://github.com/GothenburgBitFactory/taskwarrior/blob/ad9c6d95564f92ef7011232fa970f4d51d73361d/src/TDB2.cpp#L118-L193
[Taskwarrior orphan preservation]: https://github.com/GothenburgBitFactory/taskwarrior/blob/ad9c6d95564f92ef7011232fa970f4d51d73361d/src/Task.cpp#L649-L714
[TaskChampion UDA semantics]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/tasks.md#L45-L50
[TaskChampion sync model]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/sync-model.md#L37-L68
[Replica operation contract]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/replica.rs#L27-L64
[TaskChampion operation type]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/operation.rs#L7-L40
[TaskChampion sync process]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/sync-model.md#L127-L137
[TaskChampion sync implementation]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/taskdb/sync.rs#L35-L149
[TaskChampion transform source]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/server/op.rs#L34-L123
[Task status setter]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/task/task.rs#L303-L325
[TaskData physical deletion]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/task/data.rs#L96-L113
[TaskChampion expiration]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/replica.rs#L473-L496
[Taskwarrior purge setting]: https://github.com/GothenburgBitFactory/taskwarrior/blob/ad9c6d95564f92ef7011232fa970f4d51d73361d/doc/man/taskrc.5.in#L223-L227
[TaskChampion recurrence scope]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/tasks.md#L41-L44
[Taskwarrior recurrence generation]: https://github.com/GothenburgBitFactory/taskwarrior/blob/ad9c6d95564f92ef7011232fa970f4d51d73361d/src/recur.cpp#L63-L150
[Taskwarrior recurrence mask]: https://github.com/GothenburgBitFactory/taskwarrior/blob/ad9c6d95564f92ef7011232fa970f4d51d73361d/src/recur.cpp#L365-L394
[Taskwarrior recurrence configuration]: https://github.com/GothenburgBitFactory/taskwarrior/blob/ad9c6d95564f92ef7011232fa970f4d51d73361d/doc/man/task-sync.5.in#L314-L329
[TaskChampion dependency model]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/tasks.md#L37-L42
[Taskwarrior dependency source]: https://github.com/GothenburgBitFactory/taskwarrior/blob/ad9c6d95564f92ef7011232fa970f4d51d73361d/src/Task.cpp#L991-L1066
[HTTP protocol errors]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/http.md#L26-L45
[Sync-server 404 behavior]: https://github.com/GothenburgBitFactory/taskchampion-sync-server/blob/3f017c13e687d9ef41e374dbf05c0605023e88bd/server/src/api/get_child_version.rs#L31-L46
[TaskChampion HTTP client]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/server/sync/mod.rs#L120-L237
[Sync-server Gone decision]: https://github.com/GothenburgBitFactory/taskchampion-sync-server/blob/3f017c13e687d9ef41e374dbf05c0605023e88bd/core/src/server.rs#L108-L141
[TaskChampion encryption source]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/server/encryption.rs#L39-L118
[HTTP snapshot recovery]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/http.md#L41-L65
[TaskChampion error enum]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/errors.rs#L4-L24
[TaskChampion HTTP protocol]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/http.md
[Sync-server API constants]: https://github.com/GothenburgBitFactory/taskchampion-sync-server/blob/3f017c13e687d9ef41e374dbf05c0605023e88bd/server/src/api/mod.rs#L11-L28
[TaskChampion encryption protocol]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/encryption.md#L8-L38
[Documented version payload]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/docs/src/sync-protocol.md#L27-L45
[Implemented version payload]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/taskdb/sync.rs#L10-L13
[Implemented snapshot codec]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/src/taskdb/snapshot.rs#L58-L108
[Sync-server protocol warning]: https://github.com/GothenburgBitFactory/taskchampion-sync-server/blob/3f017c13e687d9ef41e374dbf05c0605023e88bd/docs/src/integration/protocol-impl.md#L1-L10
[TaskChampion compatibility policy]: https://github.com/GothenburgBitFactory/taskchampion/blob/04601336b607e5635096e84a07959a852ce55264/POLICY.md#L1-L18
