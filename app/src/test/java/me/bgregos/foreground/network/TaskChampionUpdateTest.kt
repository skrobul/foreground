package me.bgregos.foreground.network

import me.bgregos.foreground.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date
import java.util.UUID

class TaskChampionUpdateTest {
    @Test
    fun completionCreatesLifecyclePatchWithoutReplacingOtherProperties() {
        val task = Task(
            name = "blah",
            uuid = UUID.fromString("33818dad-a6d4-46c9-aad4-8e2b4890c9e8"),
            status = "completed",
            modifiedDate = Date(1_792_275_800_000),
            endDate = Date(1_792_275_799_000),
            others = mapOf("custom" to "keep")
        )

        val update = task.toTaskChampionLifecycleUpdate()!!

        assertEquals(task.uuid.toString(), update.uuid)
        assertEquals(
            mapOf("status" to "completed", "modified" to "1792275800", "end" to "1792275799"),
            update.properties
        )
    }

    @Test
    fun pendingTaskDoesNotCreateLifecyclePatch() {
        assertNull(Task(name = "blah", status = "pending").toTaskChampionLifecycleUpdate())
    }
}
