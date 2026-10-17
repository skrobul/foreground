use jni::sys::jlong;
use jni::{
    objects::{JClass, JString, JObject},
    JNIEnv,
};
use std::sync::Arc;
use std::collections::HashMap;
use tokio::runtime::Runtime;
use taskchampion::Replica;
use taskchampion::storage::SqliteStorage;
use serde_json;

// Opaque handle type
type Handle = usize;

// Global registry to store the TaskChampion service instances
lazy_static::lazy_static! {
    static ref HANDLE_REGISTRY: std::sync::Mutex<HashMap<Handle, Arc<Replica>>> =
        std::sync::Mutex::new(HashMap::new());
}

// Error handling for JNI functions
#[derive(Debug)]
pub enum JniError {
    SerializationError(String),
    TaskChampionError(String),
    InvalidHandle,
    Other(String),
}

impl From<JniError> for String {
    fn from(err: JniError) -> Self {
        match err {
            JniError::SerializationError(msg) => format!("Serialization error: {}", msg),
            JniError::TaskChampionError(msg) => format!("TaskChampion error: {}", msg),
            JniError::InvalidHandle => "Invalid handle".to_string(),
            JniError::Other(msg) => msg,
        }
    }
}

// Simple JSON serialization for errors
fn serialize_error(error: &JniError) -> String {
    let error_str: String = error.into();
    serde_json::json!({
        "error": error_str,
        "type": "jni_error"
    }).to_string()
}

#[no_mangle]
pub extern "C" fn JNI_OnLoad(_vm: jni::sys::JavaVM, _reserved: *mut std::os::raw::c_void) -> jni::sys::jint {
    jni::sys::JNI_VERSION_1_6
}

#[no_mangle]
pub extern "C" fn Java_com_foreground_taskchampion_Native_init(
    env: JNIEnv,
    _class: JClass,
    no_backup_dir: JString,
    config_json: JString,
) -> jlong {
    // Parse the parameters from JNI
    let dir_path = match env.get_string(no_backup_dir) {
        Ok(s) => s.into_utf8().unwrap().into_owned(),
        Err(_) => return -1,
    };
    
    let config_str = match env.get_string(config_json) {
        Ok(s) => s.into_utf8().unwrap().into_owned(),
        Err(_) => return -1,
    };
    
    // Create a new TaskChampion replica with SQLite storage
    let storage = SqliteStorage::new(dir_path)
        .map_err(|e| JniError::TaskChampionError(format!("Failed to create storage: {:?}", e)))
        .unwrap();
        
    let replica = Replica::new(storage)
        .map_err(|e| JniError::TaskChampionError(format!("Failed to create replica: {:?}", e)))
        .unwrap();
    
    // Add the replica to the registry
    let id = rand::random::<usize>();
    HANDLE_REGISTRY.lock().unwrap().insert(id, Arc::new(replica));
    
    id as jlong
}

#[no_mangle]
pub extern "C" fn Java_com_foreground_taskchampion_Native_query(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    query_json: JString,
) -> JString {
    // Parse the query from JSON
    let query_str = match env.get_string(query_json) {
        Ok(s) => s.into_utf8().unwrap().into_owned(),
        Err(_) => return JString::from_str(&env, &serialize_error(&JniError::Other("Failed to parse query".to_string()))).unwrap(),
    };
    
    // Get the replica from registry
    let registry = HANDLE_REGISTRY.lock().unwrap();
    let replica = match registry.get(&(handle as usize)) {
        Some(r) => r,
        None => return JString::from_str(&env, &serialize_error(&JniError::InvalidHandle)).unwrap(),
    };

    // Attempt to parse query and execute it
    let query: serde_json::Value = match serde_json::from_str(&query_str) {
        Ok(q) => q,
        Err(e) => return JString::from_str(&env, &serialize_error(&JniError::SerializationError(format!("Failed to parse query JSON: {:?}", e)))).unwrap(),
    };

    // Here we would actually execute the query against TaskChampion
    // For now, let's simulate a basic answer
    let result = serde_json::json!({
        "success": true,
        "query": query,
        "handle": handle as u64,
        "type": "query_result"
    }).to_string();

    JString::from_str(&env, &result).unwrap()
}

#[no_mangle]
pub extern "C" fn Java_com_foreground_taskchampion_Native_mutate(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    command_json: JString,
) -> JString {
    // Parse the command from JSON
    let command_str = match env.get_string(command_json) {
        Ok(s) => s.into_utf8().unwrap().into_owned(),
        Err(_) => return JString::from_str(&env, &serialize_error(&JniError::Other("Failed to parse command".to_string()))).unwrap(),
    };

    // Get the replica from registry
    let registry = HANDLE_REGISTRY.lock().unwrap();
    let replica = match registry.get(&(handle as usize)) {
        Some(r) => r,
        None => return JString::from_str(&env, &serialize_error(&JniError::InvalidHandle)).unwrap(),
    };

    // Attempt to parse command and execute it
    let command: serde_json::Value = match serde_json::from_str(&command_str) {
        Ok(c) => c,
        Err(e) => return JString::from_str(&env, &serialize_error(&JniError::SerializationError(format!("Failed to parse command JSON: {:?}", e)))).unwrap(),
    };

    // Here we would actually execute the mutation against TaskChampion
    // For now, let's simulate a basic answer
    let result = serde_json::json!({
        "success": true,
        "command": command,
        "handle": handle as u64,
        "type": "mutation_result"
    }).to_string();

    JString::from_str(&env, &result).unwrap()
}

#[no_mangle]
pub extern "C" fn Java_com_foreground_taskchampion_Native_sync(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> JString {
    // Get the replica from registry
    let registry = HANDLE_REGISTRY.lock().unwrap();
    let replica = match registry.get(&(handle as usize)) {
        Some(r) => r,
        None => return JString::from_str(&env, &serialize_error(&JniError::InvalidHandle)).unwrap(),
    };

    // Perform synchronization with server - this is a placeholder
    // In real implementation we would:
    // 1. Check connection status
    // 2. Initiate sync operation with replica
    // 3. Return sync result details
    
    let result = serde_json::json!({
        "success": true,
        "handle": handle as u64,
        "type": "sync_result",
        "sync_status": "completed"
    }).to_string();

    JString::from_str(&env, &result).unwrap()
}

#[no_mangle]
pub extern "C" fn Java_com_foreground_taskchampion_Native_close(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    // Clean up resources associated with the handle
    let mut registry = HANDLE_REGISTRY.lock().unwrap();
    registry.remove(&(handle as usize));
}