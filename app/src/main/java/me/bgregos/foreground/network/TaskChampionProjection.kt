package me.bgregos.foreground.network

import me.bgregos.foreground.model.Task
import me.bgregos.foreground.model.Annotation
import java.util.Date
import java.util.UUID

private val mappedTaskChampionProperties = setOf(
    "description", "status", "project", "priority", "due", "wait", "entry", "end", "modified", "reviewed", "tags", "uuid"
)

internal fun projectTaskChampionTask(uuid: UUID, properties: Map<String, String>): Task {
    val tags = properties.keys
        .filter { it.startsWith("tag_") }
        .map { it.removePrefix("tag_") }
        .filter { it.isNotEmpty() }

    val annotations = properties.mapNotNull { (key, description) ->
        val epochSeconds = key.removePrefix("annotation_").toLongOrNull()
            ?.takeIf { key.startsWith("annotation_") }
            ?: return@mapNotNull null
        Annotation(description, Date(epochSeconds * 1000))
    }.sortedBy { it.entry }

    val extras = properties.filterKeys { key ->
        key !in mappedTaskChampionProperties &&
            !key.startsWith("tag_") &&
            !key.startsWith("annotation_")
    }

    return Task(
        name = properties["description"].orEmpty(),
        uuid = uuid,
        project = properties["project"]?.takeIf { it.isNotEmpty() },
        priority = properties["priority"]?.takeIf { it.isNotEmpty() },
        status = properties["status"] ?: "pending",
        modifiedDate = properties["modified"]?.toLongOrNull()?.let { Date(it * 1000) },
        tags = tags,
        annotations = annotations,
        others = extras
    )
}
