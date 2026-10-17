package me.bgregos.foreground.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskListVisibilityTest {
    @Test
    fun completedTasksAreExcludedFromTaskList() {
        val tasks = listOf(
            Task(name = "active", status = "pending"),
            Task(name = "done", status = "completed")
        )

        assertEquals(listOf("active"), tasks.filter { Task.isVisibleInTaskList(it) }.map { it.name })
    }
}
