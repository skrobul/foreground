# Foreground

Foreground is an Android task manager that interoperates with Taskwarrior through TaskChampion synchronization.

## Language

**Replica**:
A local TaskChampion task database, including task properties and synchronization history. Each Foreground installation owns one replica.
_Avoid_: Local cache, client database

**Sync server**:
A separately deployed, self-hosted `taskchampion-sync-server` that stores encrypted replica history and snapshots.
_Avoid_: Taskserver, taskd

**Task**:
A TaskChampion record identified by UUID and represented as string-valued properties. Foreground directly understands a subset while preserving the rest.
_Avoid_: Todo, item
