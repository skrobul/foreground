# Task Property Projection Design

## Overview

This document describes how to project TaskChampion's open string-property map into Foreground's Kotlin UI model while maintaining complete data fidelity.

## Design Principles

1. **Lossless**: All TaskChampion task properties must be preserved during projection
2. **Minimal Impact**: Existing UI logic and workflow remain unchanged 
3. **Compatibility**: Maintain compatibility with current Taskwarrior 2 concepts
4. **No Reconstruction**: Tasks are not reassembled or reconstructed during operations

## Mapping Strategy

### Core Properties (mapped directly)
- Status, description, project, priority
- Due date, wait date, start date, end date  
- Tags (as array of strings)
- Annotations (as array of objects)

### Unknown Properties (stored in 'others' map)
All TaskChampion properties that don't fall into core categories are stored in the `others: Map<String, String>` field to ensure complete data preservation.

## Implementation Approach

1. When reading from TaskChampion:
   - Core properties are directly mapped to Task model fields
   - All remaining properties are added to the `others` map

2. When writing to TaskChampion:
   - Core properties are written using standard task operations 
   - Properties in `others` map are preserved and included in sync operations

3. During sync operations:
   - All modifications pass through TaskChampion's native replica
   - No manual conversion or reconstruction occurs  

## Benefits

- **Complete Data Preservation**: All user-defined attributes remain intact
- **UI Compatibility**: Existing UI remains unchanged  
- **Robust Synchronization**: No data corruption during sync processes
- **Extensible**: Future TaskChampion features don't require UI updates

This approach ensures Foreground can fully leverage TaskChampion's capabilities while maintaining backward compatibility and complete task fidelity.