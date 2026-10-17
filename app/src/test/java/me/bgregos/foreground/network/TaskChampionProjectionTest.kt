package me.bgregos.foreground.network

import org.junit.Assert.assertEquals
import org.junit.Test
import me.bgregos.foreground.model.Annotation
import java.util.Date
import java.util.UUID

class TaskChampionProjectionTest {
    @Test
    fun projectsTagPropertiesIntoTaskTagsAndKeepsUnrecognizedProperties() {
        val uuid = UUID.fromString("33818dad-a6d4-46c9-aad4-8e2b4890c9e8")

        val task = projectTaskChampionTask(
            uuid,
            mapOf(
                "description" to "blah",
                "status" to "pending",
                "tag_personal" to "",
                "tag_work" to "x",
                "reviewed" to "1",
                "custom_field" to "keep-me"
            )
        )!!

        assertEquals(listOf("personal", "work"), task.tags.sorted())
        assertEquals(mapOf("custom_field" to "keep-me"), task.others)
    }

    @Test
    fun modifiedTimestampIsMetadataRatherThanAnEditableCustomAttribute() {
        val task = projectTaskChampionTask(
            UUID.fromString("33818dad-a6d4-46c9-aad4-8e2b4890c9e8"),
            mapOf("description" to "blah", "modified" to "1792275800")
        )

        assertEquals(Date(1_792_275_800_000), task.modifiedDate)
        assertEquals(emptyMap<String, String>(), task.others)
    }

    @Test
    fun annotationPropertiesBecomeAnnotationsRatherThanCustomAttributes() {
        val task = projectTaskChampionTask(
            UUID.fromString("c9429e7e-5e34-438b-a32d-e5233db6bc8d"),
            mapOf(
                "description" to "annotated task",
                "annotation_1736169097" to "annotation text"
            )
        )

        assertEquals(listOf(Annotation("annotation text", Date(1_736_169_097_000))), task.annotations)
        assertEquals(emptyMap<String, String>(), task.others)
    }

    @Test
    fun projectsTaskChampionDatesUsedForOrdering() {
        val task = projectTaskChampionTask(
            UUID.fromString("33818dad-a6d4-46c9-aad4-8e2b4890c9e8"),
            mapOf(
                "description" to "dated",
                "due" to "1792275900",
                "entry" to "1792275000",
                "wait" to "1792275500",
                "end" to "1792275800"
            )
        )

        assertEquals(Date(1_792_275_900_000), task.dueDate)
        assertEquals(Date(1_792_275_000_000), task.createdDate)
        assertEquals(Date(1_792_275_500_000), task.waitDate)
        assertEquals(Date(1_792_275_800_000), task.endDate)
        assertEquals(emptyMap<String, String>(), task.others)
    }
}
