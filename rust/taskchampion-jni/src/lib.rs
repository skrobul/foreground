use jni::{objects::{JClass, JString}, JNIEnv};
use serde::Deserialize;
use serde_json::json;
use std::collections::HashMap;
use taskchampion::{storage::{AccessMode, Storage}, Operations, Replica, ServerConfig, SqliteStorage, Uuid};

#[derive(Deserialize)]
struct TaskUpdate {
    uuid: String,
    properties: HashMap<String, String>,
}

async fn apply_task_updates<S: Storage>(replica: &mut Replica<S>, updates_json: &str) -> Result<(), String> {
    let updates: Vec<TaskUpdate> = serde_json::from_str(updates_json).map_err(|e| e.to_string())?;
    for update in updates {
        let uuid = Uuid::parse_str(&update.uuid).map_err(|e| e.to_string())?;
        let mut task = replica.get_task_data(uuid).await.map_err(|e| e.to_string())?
            .ok_or_else(|| format!("Task {uuid} does not exist in the replica"))?;
        let mut operations = Operations::new();
        let mut changed = false;
        for (property, value) in update.properties {
            if task.get(&property) != Some(value.as_str()) {
                task.update(property, Some(value), &mut operations);
                changed = true;
            }
        }
        if changed {
            replica.commit_operations(operations).await.map_err(|e| e.to_string())?;
        }
    }
    Ok(())
}

fn sync_replica(path: String, url: String, client_id: String, secret: String, updates_json: String) -> Result<String, String> {
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build().map_err(|e| e.to_string())?;
    runtime.block_on(async move {
        let storage = SqliteStorage::new(path, AccessMode::ReadWrite, true).await.map_err(|e| e.to_string())?;
        let mut replica = Replica::new(storage);
        let client_id = Uuid::parse_str(&client_id).map_err(|e| format!("Invalid Client ID: {e}"))?;
        let mut server = ServerConfig::Remote { url, client_id, encryption_secret: secret.into_bytes() }
            .into_server().await.map_err(|e| e.to_string())?;
        apply_task_updates(&mut replica, &updates_json).await?;
        replica.sync(&mut server, false).await.map_err(|e| e.to_string())?;
        let tasks = replica.all_task_data().await.map_err(|e| e.to_string())?;
        let records: Vec<_> = tasks.into_values().map(|task| {
            let properties: serde_json::Map<String, serde_json::Value> = task.iter()
                .map(|(key, value)| (key.clone(), json!(value))).collect();
            json!({"uuid": task.get_uuid().to_string(), "properties": properties})
        }).collect();
        Ok(json!({"success": true, "tasks": records}).to_string())
    })
}

#[no_mangle]
pub extern "system" fn Java_com_foreground_taskchampion_Native_nativeSync(
    mut env: JNIEnv, _class: JClass, path: JString, url: JString, client_id: JString, secret: JString, updates: JString,
) -> jni::sys::jstring {
    let result = (|| -> Result<String, String> {
        let path: String = env.get_string(&path).map_err(|e| e.to_string())?.into();
        let url: String = env.get_string(&url).map_err(|e| e.to_string())?.into();
        let client_id: String = env.get_string(&client_id).map_err(|e| e.to_string())?.into();
        let secret: String = env.get_string(&secret).map_err(|e| e.to_string())?.into();
        let updates: String = env.get_string(&updates).map_err(|e| e.to_string())?.into();
        sync_replica(path, url, client_id, secret, updates)
    })().unwrap_or_else(|error| json!({"success": false, "error": error}).to_string());
    env.new_string(result).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[cfg(test)]
mod tests {
    use super::*;
    use taskchampion::{storage::inmemory::InMemoryStorage, TaskData};

    #[test]
    fn completion_update_changes_status_and_preserves_other_properties() {
        let runtime = tokio::runtime::Builder::new_current_thread().enable_all().build().unwrap();
        runtime.block_on(async {
            let uuid = Uuid::parse_str("33818dad-a6d4-46c9-aad4-8e2b4890c9e8").unwrap();
            let mut replica = Replica::new(InMemoryStorage::new());
            let mut operations = Operations::new();
            let mut task = TaskData::create(uuid, &mut operations);
            task.update("description", Some("blah".into()), &mut operations);
            task.update("status", Some("pending".into()), &mut operations);
            task.update("custom", Some("preserve-me".into()), &mut operations);
            replica.commit_operations(operations).await.unwrap();

            apply_task_updates(
                &mut replica,
                r#"[{"uuid":"33818dad-a6d4-46c9-aad4-8e2b4890c9e8","properties":{"status":"completed","end":"1792275799","modified":"1792275800"}}]"#,
            ).await.unwrap();

            let updated = replica.get_task_data(uuid).await.unwrap().unwrap();
            assert_eq!(updated.get("status"), Some("completed"));
            assert_eq!(updated.get("custom"), Some("preserve-me"));
            assert_eq!(updated.get("description"), Some("blah"));
            assert_eq!(updated.get("end"), Some("1792275799"));
        });
    }
}
