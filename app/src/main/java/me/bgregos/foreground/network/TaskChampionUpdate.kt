package me.bgregos.foreground.network

import me.bgregos.foreground.model.Task
import java.util.Date

internal data class TaskChampionUpdate(
    val uuid: String,
    val properties: Map<String, String>
)

internal fun Task.toTaskChampionLifecycleUpdate(): TaskChampionUpdate? {
    if (status != "completed" && status != "deleted") return null

    val properties = mutableMapOf(
        "status" to status,
        "modified" to ((modifiedDate ?: Date()).time / 1000).toString()
    )
    endDate?.let { properties["end"] = (it.time / 1000).toString() }
    return TaskChampionUpdate(uuid.toString(), properties)
}
