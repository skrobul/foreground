package me.bgregos.foreground.network

import me.bgregos.foreground.model.SyncResult
import me.bgregos.foreground.model.Task

/**
 * Interface for TaskChampion-based storage backend.
 * Implements the lossless projection strategy from issue #6.
 */
interface RemoteTaskChampionSource : RemoteTaskSource {
    /**
     * Initialize connection to TaskChampion replica
     */
    suspend fun initReplica(): SyncResult
    
    /**
     * Get current status of the replica
     */
    suspend fun getReplicaStatus(): String
    
    /**
     * Reset replica to clean state (should be used for migration/reset operations)
     */
    suspend fun resetReplica()
    
    /**
     * Rebuild replica from scratch with new configuration
     */
    suspend fun rebuildReplica(config: Map<String, String>): SyncResult
    
    /**
     * Check if this replica is properly configured and synchronized
     */
    suspend fun isReplicaReady(): Boolean
}