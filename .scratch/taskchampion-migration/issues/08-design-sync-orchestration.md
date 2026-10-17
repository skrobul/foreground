# Design sync orchestration

Type: grilling
Status: open
Blocked by: 05

## Question

How should manual sync, periodic WorkManager sync, local edits, process lifecycle, cancellation, serialization, network constraints, retry/backoff, UI refresh, and typed error reporting coordinate around the single native replica owner without blocking the UI or losing committed operations?
