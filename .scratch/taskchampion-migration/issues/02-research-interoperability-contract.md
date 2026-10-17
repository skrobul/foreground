# Define Taskwarrior 3 interoperability contract

Type: research
Status: resolved
Blocked by:

## Question

Using current first-party TaskChampion, Taskwarrior 3, and sync-server sources, define the client configuration and data contract Foreground must honor to share an existing self-hosted server history with Taskwarrior 3. Cover client UUID and encryption-secret identity, first sync and snapshots, task/property semantics, unknown-property preservation, operations and conflicts, deletions, recurrence/dependencies, errors such as HTTP 410, and protocol/version compatibility guarantees.

## Answer

All replicas for one history use the same base URL, server client UUID, and byte-exact UTF-8 encryption secret. The UUID identifies server history, not a device. Foreground creates separate empty local storage and completes first sync before allowing edits so snapshots or full history can bootstrap safely.

The TaskChampion property map is authoritative. Foreground updates only changed properties, logically deletes with `status=deleted`, preserves unknown/recurrence/dependency properties literally, and leaves operation commits, rebasing, and conflict resolution to TaskChampion.

Pin TaskChampion 3.1.0 and test against Taskwarrior 3.5.0 and sync-server 0.7.1. No capability negotiation exists. Current TaskChampion reports HTTP 410 as a generic server error, so typed destructive recovery requires an upstream API change or a tested adapter; no sync error may automatically erase the replica.

Full findings: [Taskwarrior 3 / TaskChampion interoperability contract](../research/interoperability-contract.md).
