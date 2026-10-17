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
        dueDate = properties["due"]?.toTaskChampionDate(),
        createdDate = properties["entry"]?.toTaskChampionDate() ?: Date(),
        project = properties["project"]?.takeIf { it.isNotEmpty() },
        priority = properties["priority"]?.takeIf { it.isNotEmpty() },
        status = properties["status"] ?: "pending",
        modifiedDate = properties["modified"]?.toLongOrNull()?.let { Date(it * 1000) },
        waitDate = properties["wait"]?.toTaskChampionDate(),
        endDate = properties["end"]?.toTaskChampionDate(),
        tags = tags,
        annotations = annotations,
        others = extras
    )
}

private fun String.toTaskChampionDate(): Date? = toLongOrNull()?.let { Date(it * 1000) }

internal fun preserveTaskOrder(previousTasks: List<Task>, importedTasks: List<Task>): List<Task> {
    val importedByUuid = importedTasks.associateBy { it.uuid }
    val previousUuids = previousTasks.mapTo(mutableSetOf()) { it.uuid }
    val existingInOrder = previousTasks.mapNotNull { importedByUuid[it.uuid] }
    val newTasks = importedTasks
        .filter { it.uuid !in previousUuids }
        .sortedWith(Comparator { first, second ->
            val legacyOrder = Task.DateCompare().compare(first, second)
            if (legacyOrder != 0) legacyOrder
            else first.createdDate.compareTo(second.createdDate).takeIf { it != 0 }
                ?: first.uuid.toString().compareTo(second.uuid.toString())
        })
    return existingInOrder + newTasks
}
