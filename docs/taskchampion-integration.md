# TaskChampion Integration

This document describes the integration of TaskChampion into the Foreground task management application, following the design specifications.

## Design Principle (Ticket #6: Lossless Task Projection)

The implementation follows a carefully designed mapping strategy to enable lossless projection of TaskChampion's open string-property map into Foreground's existing UI model:

### Core Approach
1. **Use TaskChampion's native task properties** for core attributes that are known and supported by Foreground
2. **Leverage the 'others' map** to preserve any unknown or user-defined properties from TaskChampion
3. **Maintain separate storage for each property type**: core fields in the Task model, unknown properties in `others` map

### Implementation Details

#### Core supported TaskChampion properties:
- Status, description, project, priority, due date, wait date, start date, end date
- Tags (as array of strings)
- Annotations (as array of objects with description and entry fields)

These are projected into the corresponding Kotlin model fields as they do not require special handling.

#### Unknown properties:
TaskChampion allows any string key-value pair in its task storage. All these unknown properties must be preserved during projection to ensure no data loss. The `others` map in Foreground's Task class will contain all these properties, maintaining complete fidelity.

## Integration Components

### RemoteTaskChampionSource Interface
Provides interface for TaskChampion-based backend operations with:
- Proper initialization of TaskChampion replica
- Support for reset and recovery flows  
- Data synchronization capabilities

### RemoteTaskChampionSourceImpl
Implementation that:
- Follows the lossless projection strategy
- Maintains compatibility with existing UI workflows
- Implements setup, reset, and recovery operations as defined in Ticket #7

## Benefits

- **Full data preservation**: No information loss, even for user-defined attributes not directly supported by Foreground UI
- **Minimal breaking changes**: Existing UI and logic remain functionally identical  
- **Compatibility**: TaskChampion tasks can be read back into Foreground UI without loss
- **Future-proofing**: New TaskChampion properties can appear in the `others` field without requiring UI updates

This implementation enables Foreground to adopt TaskChampion as a backend while maintaining complete task data fidelity, essential for any robust synchronization system.