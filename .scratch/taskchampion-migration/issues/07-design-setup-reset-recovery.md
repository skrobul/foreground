# Design setup, reset, and recovery flows

Type: grilling
Status: open
Blocked by: 03, 05

## Question

What exact states and user flows should govern entering and securely storing the remote tuple, proving an existing history through empty-replica first sync, explicitly resetting legacy local data, handling wrong identity or secret, and recovering from pruned/divergent history without automatic data loss?

## Answer

Based on the current project context and TaskChampion architecture needs, here's a design for setup, reset, and recovery flows:

### 1. Setup Flow

The setup flow should securely establish trust with a remote sync server by:
- Prompting user to enter server URL, identity, and secret
- Encrypting and securely storing the credentials locally and in TaskChampion storage
- Establishing initial connection with empty replica to prove history
- Syncing with existing history if present

### 2. Reset Flow

The reset flow must handle different types of resets:
- **Soft reset**: Clear local data while preserving remote history
- **Hard reset**: Complete replica reset (clear all local data and history)
- **Recovery reset**: Handle cases where replica state conflicts with remote
- Handling wrong identity or secret scenarios gracefully

### 3. Recovery Flow

Support recovering from:
- Pruned history due to server maintenance or cleanup
- Divergent histories due to multiple clients 
- Corruption scenarios in local replica
- Migrating from legacy Taskwarrior data model

## Technical Implementation Details

### Setup Procedure

1. **Authentication Configuration** (using issue 03 implementation):
   - Collect server URL, identity, and secret from user
   - Validate credentials format and reachability
   - For private CA support (as per issue 03), store root certificate if needed 

2. **Replica Initialization**:
   - Initialize new or existing TaskChampion replica with the provided credentials
   - Store encrypted configuration in secure storage (SharedPreferences or Android Keystore)
   - If this is a first-time setup, use empty-replica sync to establish history

3. **Initial Sync**:
   - Perform sync with an empty replica to prove identity and access
   - Handle potential mismatch scenarios where existing sync data exists but with different credentials
   - Validate that server accepts the connection 

### Reset Procedure

**Soft Reset (Local Data Clearing)**:
- Clear tasks in current replica
- Retain existing history and remote state
- Don't delete any historical data from remote

**Hard Reset (Complete Reset)**:
- Clear the entire TaskChampion replica database 
- Require confirmation for this operation
- Re-establish new replica from clean state

**Recovery Reset**:
- Detect divergence with remote replica
- Provide options to restore from backup or reinitialize 
- Gracefully handle invalid credentials by prompting user for correction

### Error Handling and Validation

1. **Wrong Credentials**: Prompt user for correct identity/secret when authentication fails
2. **Sync Failures**: Report specific sync errors without exposing underlying system details
3. **Corruption Scenarios**: Implement fallback recovery patterns with backup restoration options
4. **Divergent History**: Detect conflicts and provide resolution choices

### Implementation Plan

1. **Enhance RemoteTaskChampionSourceImpl**:
   - Add proper TaskChampion cleanup/reset methods
   - Implement actual replica state management (not just in-memory clears)
  
2. **Secure Secret Storage**:
   - Use Android Keystore for secure credential storage 
   - Ensure encryption of credentials at rest

3. **User Interface Integration**:
   - Add reset/recovery UI components in settings
   - Implement confirmation dialogs for destructive operations
   - Show clear status information during setup/reset processes

## Security Considerations

1. **Credential Encryption**:
   - All sensitive information should be encrypted before storage
   - Use appropriate Android security patterns (KeyStore/Keystore encryption)

2. **History Preservation**:
   - Ensure no accidental data loss when resetting
   - Support recovery from previous states if needed

3. **Validation Steps**:
   - Validate new credentials before storing them
   - Confirm reset operations with user confirmation
   - Report errors during sync without exposing internals

This approach maintains compatibility with existing TaskChampion features while providing proper error handling, security and clear user flows for setup, reset, and recovery.