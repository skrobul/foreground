# Design Native Repository Boundary

## Problem 

The current implementation uses SharedPreferences for task persistence, but this needs to be replaced with TaskChampion's native replica functionality while preserving:
- UI behavior 
- Property-level edits
- Serialized access
- Lifecycle safety
- Typed errors

## Analysis

From examining the codebase:
1. Current task persistence is via SharedPreferences in `TaskRepository.kt`
2. The JNI wrapper (`rust/taskchampion-jni/src/lib.rs`) currently has stubbed implementations
3. TaskChampion's replica functionality can handle persistence through SQLite storage 
4. Android embedding research shows recommended patterns for native integration

## Solution Design

### Architecture Requirements

1. **TaskChampion Integration**
   - Replace SharedPreferences with TaskChampion Replica for persistence
   - Maintain the existing JNI interface (`init`, `query`, `mutate`, `sync`, `close`)
   - Use `SqliteStorage::new` with appropriate directory (noBackupFilesDir)

2. **Repository Boundary**
   - Create native repository wrapper that manages replica lifecycle 
   - Handle Android-specific considerations (noBackupFilesDir, file permissions)
   - Provide typed error handling for JNI operations
   - Support serialized access patterns to maintain thread safety

3. **Migration Strategy**  
   - Preserve existing APIs and behaviors for UI
   - Migrate from SharedPreferences serialization to TaskChampion's native structures
   - Maintain compatibility with sync server requirements

### Implementation Details

The implementation will:
- Use `SqliteStorage::new()` with the directory provided by `getNoBackupFilesDir()/taskchampion`
- Support full TaskChampion replica functionality for queries, mutations, and synchronization  
- Handle all error cases appropriately with typed errors for JNI compatibility
- Follow Android security patterns with proper file permissions and app-private storage

### Implementation Plan

1. **Modify JNI Wrapper** 
   - Implement proper TaskChampion Replica initialization
   - Add proper error handling with typed errors
   - Implement actual query, mutation and sync operations

2. **Native Repository Layer**
   - Create native repository class that wraps the TaskChampion services
   - Handle lifecycle management and access control
   - Ensure serialized access patterns are maintained 

3. **Configuration** 
   - Use `getNoBackupFilesDir()` for replica storage directory
   - Maintain appropriate file permissions for Android security model

### Technical Details

#### Storage Location  
- Use `Context.getNoBackupFilesDir()/taskchampion` as the default directory
- This ensures replica directory is app-private and excluded from automatic backup
- Avoids the security issues of using filesDir directly

#### Error Handling  
- Create typed errors that map to appropriate JNI error types
- Maintain compatibility with existing exception handling patterns
- Use TaskChampion's existing error types where appropriate

#### Synchronization Model 
- Maintain serialized access for thread safety
- Handle lifecycle events appropriately (onCreate, onDestroy)
- Support proper resource cleanup when closing

## Implementation Approach

This should be implemented in the `taskchampion-jni` Rust crate with:
1. Proper TaskChampion Replica instantiation 
2. JNI bridge that properly handles all operations
3. Resource management aligned with Android lifecycle patterns
4. Error handling aligned with existing codebase patterns

This design will maintain backward compatibility while leveraging TaskChampion's features for better persistence, sync safety, and data integrity.