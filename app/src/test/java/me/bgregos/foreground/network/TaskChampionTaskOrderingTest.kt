package me.bgregos.foreground.network

import me.bgregos.foreground.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date
import java.util.UUID

class TaskChampionTaskOrderingTest {
    @Test
    fun syncPreservesExistingTaskOrderAndAppendsNewTasksByEntryTime() {
        val first = task("00000000-0000-0000-0000-000000000001", "first", 200)
        val second = task("00000000-0000-0000-0000-000000000002", "second", 100)
        val newEarlier = task("00000000-0000-0000-0000-000000000003", "new earlier", 50)
        val newLater = task("00000000-0000-0000-0000-000000000004", "new later", 300)

        val ordered = preserveTaskOrder(
            previousTasks = listOf(first, second),
            importedTasks = listOf(newLater, second, newEarlier, first)
        )

        assertEquals(listOf("first", "second", "new earlier", "new later"), ordered.map { it.name })
    }

    private fun task(uuid: String, name: String, entrySeconds: Long) = Task(
        name = name,
        uuid = UUID.fromString(uuid),
        createdDate = Date(entrySeconds * 1000)
    )
}
