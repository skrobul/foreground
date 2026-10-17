# Design lossless task projection

Type: grilling
Status: resolved
Blocked by: 05

## Question

How should Foreground project TaskChampion's open string-property map into its current Kotlin UI model and translate each supported create/edit/complete/delete action into one property-level operation batch without reconstructing tasks, damaging unknown properties, generating recurrence, changing dependencies, or adding a second conflict layer?

## Answer

The lossless projection of TaskChampion's open string-property map requires a carefully designed mapping strategy to preserve all data while maintaining compatibility with Foreground's existing UI model.

### Core Approach

1. **Use TaskChampion's native task properties** for the core attributes that are known and supported by Foreground
2. **Leverage the 'others' map** to preserve any unknown or user-defined properties from TaskChampion (this is where most of the data is preserved)
3. **Maintain separate storage for each property type**: core fields in the Task model, unknown properties in `others` map

### Property Mapping Strategy 

#### Core supported TaskChampion properties:
- Status, description, project, priority, due date, wait date, start date, end date
- Tags (as array of strings)
- Annotations (as array of objects with description and entry fields) 

These will be projected into the corresponding Kotlin model fields as they do not require special handling.

#### Unknown properties:
TaskChampion allows any string key-value pair in its task storage. All these unknown properties must be preserved during projection to ensure no data loss. The `others` map in Foreground's Task class will contain all these properties, maintaining complete fidelity.

### Implementation Details

1. **Storage Structure**:  
   When reading from TaskChampion, we should:
   - Map core properties directly using existing mechanisms
   - Store all unknown properties in the `others: Map<String, String>` field
   - Maintain the distinction between core and non-core fields at read time

2. **Data Flow**:
   - **Write operations**: When tasks are modified, preserve all properties in `others` map (no data loss)
   - **Read operations**: Project known properties into UI model fields, unknown ones into `others` 
   - **Sync operations**: All modifications pass through the replica's internal representation

3. **UI Model Compatibility**:  
   Foreground's existing UI is designed around core task properties that match Taskwarrior 2 concepts. This compatibility is maintained by ensuring:
   - All existing functionality remains unchanged
   - Unknown properties are not lost or corrupted during operations
   - The UI doesn't need modification since it already provides a `others` field

### Benefits of this approach:

- **Full data preservation**: No information loss, even for user-defined attributes that aren't directly supported by Foreground UI
- **Minimal breaking changes**: Existing UI and logic remain functionally identical
- **Compatibility**: TaskChampion tasks can be read back into Foreground UI without loss
- **Future-proofing**: New TaskChampion properties can appear in the `others` field without requiring UI updates

This approach allows Foreground to adopt TaskChampion as a backend while maintaining complete task data fidelity, essential for any robust synchronization system.
