package com.thrym.app.fakes

import com.thrym.core.designsystem.R
import com.thrym.feature.history.R as HistoryR
import com.thrym.feature.workout.R as WorkoutR
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** The JVM fake must resolve resources like Android does, or tests compare against wrong copy. */
class TestUiStringsTest {

    @Test
    fun `default copy is English and German overlays values-de`() {
        assertEquals(Locale.US, TestUiStrings.locale)
        assertEquals(Locale.GERMANY, TestUiStrings.german.locale)
        assertEquals("Settings", TestUiStrings.get(R.string.settings_title))
        assertEquals("Einstellungen", TestUiStrings.german.get(R.string.settings_title))
    }

    @Test
    fun `untranslatable strings fall back to values`() {
        // Defined only in values/ (translatable="false"); the quotes keep the leading space.
        assertEquals(" @ RPE 8", TestUiStrings.german.get(HistoryR.string.history_intensity_rpe, "8"))
    }

    @Test
    fun `plurals pick one or other per language`() {
        assertEquals("Add 1 exercise", TestUiStrings.getQuantity(WorkoutR.plurals.workout_picker_add_selected, 1, 1))
        assertEquals("Add 3 exercises", TestUiStrings.getQuantity(WorkoutR.plurals.workout_picker_add_selected, 3, 3))
        assertEquals("1 Übung hinzufügen", TestUiStrings.german.getQuantity(WorkoutR.plurals.workout_picker_add_selected, 1, 1))
        assertEquals("3 Übungen hinzufügen", TestUiStrings.german.getQuantity(WorkoutR.plurals.workout_picker_add_selected, 3, 3))
    }

    @Test
    fun `arguments are formatted with the instance locale`() {
        assertEquals("1.5", String.format(TestUiStrings.locale, "%.1f", 1.5))
        assertEquals("1,5", String.format(TestUiStrings.german.locale, "%.1f", 1.5))
    }

    @Test
    fun `app name comes from branding properties`() {
        assertEquals("Thrym", TestUiStrings.get(R.string.app_name))
    }

    @Test
    fun `android string escapes are resolved like aapt2`() {
        assertEquals("Don't", unescapeAndroidString("Don\\'t"))
        assertEquals("Say \"hi\"", unescapeAndroidString("Say \\\"hi\\\""))
        assertEquals("@home ?attr", unescapeAndroidString("\\@home \\?attr"))
        assertEquals("a\nb\tc", unescapeAndroidString("a\\nb\\tc"))
        assertEquals("back\\slash", unescapeAndroidString("back\\\\slash"))
        assertEquals("× ä", unescapeAndroidString("\\u00D7 \\u00e4"))
        assertEquals("  kept  ", unescapeAndroidString("\"  kept  \""))
        assertEquals("one two", unescapeAndroidString("\n    one\n    two\n"))
        assertEquals("%1\$s removed", unescapeAndroidString("\"%1\$s\" removed"))
    }
}
