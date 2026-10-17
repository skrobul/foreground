package com.foreground.taskchampion;

/**
 * Native interface for TaskChampion on Android
 */
public class Native {
    
    static {
        System.loadLibrary("foreground_taskchampion");
    }
    
    /**
     * Initialize the TaskChampion service
     *
     * @param noBackupDir Directory for persistent storage (no backup)
     * @param configJson  Configuration as JSON string
     * @return Handle to the initialized service
     */
    public static native long nativeInit(String noBackupDir, String configJson);
    
    /**
     * Query tasks from TaskChampion
     *
     * @param handle Handle to the service
     * @param queryJson Query as JSON string
     * @return Query result as JSON string
     */
    public static native String nativeQuery(long handle, String queryJson);
    
    /**
     * Mutate tasks in TaskChampion
     *
     * @param handle Handle to the service
     * @param commandJson Mutation command as JSON string
     * @return Mutation result as JSON string
     */
    public static native String nativeMutate(long handle, String commandJson);
    
    /**
     * Synchronize with the server
     *
     * @param handle Handle to the service
     * @return Sync result as JSON string
     */
    public static native String nativeSync(long handle);
    
    /**
     * Close the TaskChampion service
     *
     * @param handle Handle to the service
     */
    public static native void nativeClose(long handle);
}