package com.thrym.app.presentation.common

import com.thrym.app.data.seed.ExerciseSeedData
import com.thrym.app.domain.model.Exercise
import com.thrym.app.domain.model.ExerciseCategory
import com.thrym.app.domain.model.MuscleGroup
import com.thrym.app.fakes.TestUiStrings
import com.thrym.core.designsystem.R
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class BuiltInExerciseNamesTest {

    private fun exercise(name: String, isCustom: Boolean, id: Long = 0L) = Exercise(
        id = id,
        name = name,
        primaryMuscleGroup = MuscleGroup.BEINE,
        category = ExerciseCategory.LANGHANTEL,
        isCustom = isCustom
    )

    @Test
    fun `table covers exactly the seeded built-in exercises`() {
        val seeded = ExerciseSeedData.getAll().map { it.name }.toSet()

        assertEquals(79, seeded.size)
        assertEquals(seeded, BuiltInExerciseNames.canonicalNames)
    }

    @Test
    fun `German resources are byte-identical to the stored canonical names`() {
        // values-de must reproduce what the database and old backups contain, otherwise a
        // German UI would show a name that no longer matches search or duplicate checks.
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(root, "core/designsystem/src/main/res/values-de/exercise_names.xml"))
        val nodes = document.getElementsByTagName("string")
        val germanById = (0 until nodes.length).associate { index ->
            val node = nodes.item(index) as Element
            R.string::class.java.getField(node.getAttribute("name")).getInt(null) to node.textContent
        }

        for (name in BuiltInExerciseNames.canonicalNames) {
            val resId = requireNotNull(BuiltInExerciseNames.resIdFor(name))
            assertEquals(name, germanById[resId])
        }
    }

    @Test
    fun `built-ins show the localized name, custom exercises their stored name`() {
        assertEquals("Deadlift", TestUiStrings.exerciseName(exercise("Kreuzheben", isCustom = false)))
        assertEquals("Kreuzheben", TestUiStrings.exerciseName(exercise("Kreuzheben", isCustom = true)))
        assertEquals("Mein Rudern", TestUiStrings.exerciseName(exercise("Mein Rudern", isCustom = false)))
        assertEquals("Bench Press (Barbell)", TestUiStrings.exerciseName("Bankdrücken (Langhantel)"))
    }

    @Test
    fun `every built-in has an English name`() {
        for (name in BuiltInExerciseNames.canonicalNames) {
            assertTrue(name, TestUiStrings.exerciseName(name).isNotBlank())
        }
        assertNotEquals("Kniebeuge (Langhantel)", TestUiStrings.exerciseName("Kniebeuge (Langhantel)"))
    }

    @Test
    fun `filter and sort use the display name and still match the stored name`() {
        val exercises = listOf(
            exercise("Kreuzheben", isCustom = false, id = 1),
            exercise("Beinpresse", isCustom = false, id = 2),
            exercise("Übungsrudern", isCustom = true, id = 3),
            exercise("Ab-Roller", isCustom = false, id = 4)
        )
        val displayName = { e: Exercise -> TestUiStrings.exerciseName(e) }

        val sorted = exercises.filterAndSortByDisplayName("", displayName, Locale.US)
        assertEquals(listOf("Ab Wheel", "Deadlift", "Leg Press", "Übungsrudern"), sorted.map(displayName))

        assertEquals(listOf(1L), exercises.filterAndSortByDisplayName("dead", displayName, Locale.US).map { it.id })
        assertEquals(listOf(1L), exercises.filterAndSortByDisplayName("kreuz", displayName, Locale.US).map { it.id })
    }

    @Test
    fun `history search maps a localized query to canonical names`() {
        val matches = BuiltInExerciseNames.canonicalNamesMatching("deadlift", TestUiStrings)

        assertEquals(
            setOf("Kreuzheben", "Rumänisches Kreuzheben", "Rumänisches Kreuzheben (Kurzhantel)", "Sumo Kreuzheben"),
            matches.toSet()
        )
        assertTrue(BuiltInExerciseNames.canonicalNamesMatching("  ", TestUiStrings).isEmpty())
    }
}
