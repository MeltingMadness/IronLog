package com.thrym.app.presentation.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.thrym.app.domain.model.Exercise
import com.thrym.core.designsystem.R
import java.text.Collator
import java.util.Locale

/**
 * Display names of the built-in exercises.
 *
 * Built-ins are stored with their German canonical name (`isCustom = false`); the name is
 * part of the database and of backups and is never rewritten. The UI shows the localized
 * `exercise_builtin_*` resource instead. The table is explicit (no `getIdentifier`) so that
 * resource shrinking keeps every entry. Custom exercises always show their raw name.
 *
 * A custom exercise cannot share an active built-in's name (see the repository's duplicate
 * check), so call sites that only know a name (e.g. analytics rows) may use the name-only
 * overloads with `isCustom = false`.
 */
object BuiltInExerciseNames {
    private val resByCanonicalName: Map<String, Int> = mapOf(
        "Bankdrücken (Langhantel)" to R.string.exercise_builtin_bench_press_barbell,
        "Bankdrücken (Kurzhantel)" to R.string.exercise_builtin_bench_press_dumbbell,
        "Schrägbankdrücken (Langhantel)" to R.string.exercise_builtin_incline_bench_press_barbell,
        "Schrägbankdrücken (Kurzhantel)" to R.string.exercise_builtin_incline_bench_press_dumbbell,
        "Negativbankdrücken (Langhantel)" to R.string.exercise_builtin_decline_bench_press_barbell,
        "Fliegende (Kurzhantel)" to R.string.exercise_builtin_chest_fly_dumbbell,
        "Cable Fly" to R.string.exercise_builtin_cable_fly,
        "Cable Fly obere Brust" to R.string.exercise_builtin_cable_fly_upper_chest,
        "Butterfly Maschine" to R.string.exercise_builtin_pec_deck_machine,
        "Brustpresse (Maschine)" to R.string.exercise_builtin_chest_press_machine,
        "Liegestütze" to R.string.exercise_builtin_push_up,
        "Dips (Brust)" to R.string.exercise_builtin_chest_dip,
        "Langhantelrudern" to R.string.exercise_builtin_bent_over_row_barbell,
        "Kurzhantelrudern" to R.string.exercise_builtin_bent_over_row_dumbbell,
        "Klimmzug" to R.string.exercise_builtin_pull_up,
        "Klimmzug (Untergriff)" to R.string.exercise_builtin_chin_up,
        "Latzug breit" to R.string.exercise_builtin_lat_pulldown_wide_grip,
        "Latzug eng" to R.string.exercise_builtin_lat_pulldown_close_grip,
        "Kabelrudern sitzend" to R.string.exercise_builtin_seated_cable_row,
        "T-Bar Rudern" to R.string.exercise_builtin_t_bar_row,
        "Rudermaschine" to R.string.exercise_builtin_seated_row_machine,
        "Kreuzheben" to R.string.exercise_builtin_deadlift,
        "Hyperextension" to R.string.exercise_builtin_back_extension,
        "Face Pulls" to R.string.exercise_builtin_face_pull,
        "Einarmiges Kabelziehen" to R.string.exercise_builtin_single_arm_cable_row,
        "Kniebeuge (Langhantel)" to R.string.exercise_builtin_squat_barbell,
        "Frontkniebeuge" to R.string.exercise_builtin_front_squat,
        "Rumänisches Kreuzheben" to R.string.exercise_builtin_romanian_deadlift,
        "Rumänisches Kreuzheben (Kurzhantel)" to R.string.exercise_builtin_romanian_deadlift_dumbbell,
        "Beinpresse" to R.string.exercise_builtin_leg_press,
        "Hackenschmidt" to R.string.exercise_builtin_hack_squat,
        "Ausfallschritte (Langhantel)" to R.string.exercise_builtin_lunge_barbell,
        "Ausfallschritte (Kurzhantel)" to R.string.exercise_builtin_lunge_dumbbell,
        "Bulgarische Kniebeuge" to R.string.exercise_builtin_bulgarian_split_squat,
        "Beinstrecker" to R.string.exercise_builtin_leg_extension,
        "Beinbeuger liegend" to R.string.exercise_builtin_lying_leg_curl,
        "Beinbeuger sitzend" to R.string.exercise_builtin_seated_leg_curl,
        "Goblet Squat" to R.string.exercise_builtin_goblet_squat,
        "Sumo Kreuzheben" to R.string.exercise_builtin_sumo_deadlift,
        "Hip Thrust (Langhantel)" to R.string.exercise_builtin_hip_thrust_barbell,
        "Hip Thrust (Maschine)" to R.string.exercise_builtin_hip_thrust_machine,
        "Glute Kickback (Kabel)" to R.string.exercise_builtin_glute_kickback_cable,
        "Abduktion (Maschine)" to R.string.exercise_builtin_hip_abduction_machine,
        "Schulterdrücken (Langhantel)" to R.string.exercise_builtin_shoulder_press_barbell,
        "Schulterdrücken (Kurzhantel)" to R.string.exercise_builtin_shoulder_press_dumbbell,
        "Schulterdrücken (Maschine)" to R.string.exercise_builtin_shoulder_press_machine,
        "Seitheben (Kurzhantel)" to R.string.exercise_builtin_lateral_raise_dumbbell,
        "Seitheben (Kabel)" to R.string.exercise_builtin_lateral_raise_cable,
        "Frontheben" to R.string.exercise_builtin_front_raise,
        "Aufrechtes Rudern" to R.string.exercise_builtin_upright_row,
        "Arnold Press" to R.string.exercise_builtin_arnold_press,
        "Reverse Fly (Kurzhantel)" to R.string.exercise_builtin_reverse_fly_dumbbell,
        "Reverse Fly (Maschine)" to R.string.exercise_builtin_reverse_fly_machine,
        "Langhantelcurls" to R.string.exercise_builtin_bicep_curl_barbell,
        "SZ-Curls" to R.string.exercise_builtin_ez_bar_curl,
        "Kurzhantelcurls" to R.string.exercise_builtin_bicep_curl_dumbbell,
        "Hammercurls" to R.string.exercise_builtin_hammer_curl,
        "Konzentrationscurls" to R.string.exercise_builtin_concentration_curl,
        "Kabelcurls" to R.string.exercise_builtin_bicep_curl_cable,
        "Schrägbankcurls" to R.string.exercise_builtin_incline_dumbbell_curl,
        "Trizepsdrücken am Kabel" to R.string.exercise_builtin_triceps_pushdown,
        "Trizepsdrücken Seil" to R.string.exercise_builtin_triceps_rope_pushdown,
        "French Press (SZ-Stange)" to R.string.exercise_builtin_skullcrusher_ez_bar,
        "Stirndrücken (Kurzhantel)" to R.string.exercise_builtin_skullcrusher_dumbbell,
        "Dips (Trizeps)" to R.string.exercise_builtin_triceps_dip,
        "Kickbacks (Kurzhantel)" to R.string.exercise_builtin_triceps_kickback_dumbbell,
        "Enges Bankdrücken" to R.string.exercise_builtin_close_grip_bench_press,
        "Plank" to R.string.exercise_builtin_plank,
        "Cable Crunch" to R.string.exercise_builtin_cable_crunch,
        "Beinheben hängend" to R.string.exercise_builtin_hanging_leg_raise,
        "Beinheben liegend" to R.string.exercise_builtin_lying_leg_raise,
        "Russian Twist" to R.string.exercise_builtin_russian_twist,
        "Ab-Roller" to R.string.exercise_builtin_ab_wheel,
        "Seitliches Plank" to R.string.exercise_builtin_side_plank,
        "Unterarmcurls" to R.string.exercise_builtin_wrist_curl,
        "Reverse Curls" to R.string.exercise_builtin_reverse_curl,
        "Wadenheben stehend (Maschine)" to R.string.exercise_builtin_standing_calf_raise_machine,
        "Wadenheben sitzend" to R.string.exercise_builtin_seated_calf_raise,
        "Wadenheben an der Beinpresse" to R.string.exercise_builtin_leg_press_calf_raise,
    )

    /** German canonical names of all built-in exercises, as stored in the database. */
    val canonicalNames: Set<String> get() = resByCanonicalName.keys

    /** Resource of the localized name, or null if [name] is not a built-in canonical name. */
    @StringRes
    fun resIdFor(name: String, isCustom: Boolean = false): Int? =
        if (isCustom) null else resByCanonicalName[name]

    /** Localized names of all built-ins in the current UI language. */
    fun localizedNames(strings: UiStrings): List<String> = resByCanonicalName.values.map { strings.get(it) }

    /**
     * Canonical names of the built-ins whose localized name contains [query] (case-insensitive).
     * Lets searches that run on stored names (e.g. workout history) also find built-ins by the
     * name the user actually sees.
     */
    fun canonicalNamesMatching(query: String, strings: UiStrings): List<String> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return resByCanonicalName.filter { (_, resId) ->
            strings.get(resId).contains(needle, ignoreCase = true)
        }.keys.toList()
    }
}

/** Localized display name: the translated name for built-ins, the stored name otherwise. */
fun UiStrings.exerciseName(exercise: Exercise): String = exerciseName(exercise.name, exercise.isCustom)

/** Name-only variant for rows that carry no [Exercise]; see [BuiltInExerciseNames]. */
fun UiStrings.exerciseName(name: String, isCustom: Boolean = false): String =
    BuiltInExerciseNames.resIdFor(name, isCustom)?.let { get(it) } ?: name

@Composable
fun Exercise.localizedName(): String = localizedExerciseName(name, isCustom)

@Composable
fun localizedExerciseName(name: String, isCustom: Boolean = false): String =
    BuiltInExerciseNames.resIdFor(name, isCustom)?.let { stringResource(it) } ?: name

/**
 * Sorts by display name with a locale-aware collator (umlauts, case) and filters by [query]
 * against both the display name and the stored name. The exercise list is small (built-ins
 * plus a few custom exercises), so this runs in memory instead of in SQL, where only the
 * stored German names are known.
 */
fun List<Exercise>.filterAndSortByDisplayName(
    query: String,
    displayName: (Exercise) -> String,
    locale: Locale = Locale.getDefault()
): List<Exercise> {
    val needle = query.trim()
    val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
    return map { it to displayName(it) }
        .filter { (exercise, shown) ->
            needle.isEmpty() ||
                shown.contains(needle, ignoreCase = true) ||
                exercise.name.contains(needle, ignoreCase = true)
        }
        .sortedWith { a, b -> collator.compare(a.second, b.second) }
        .map { it.first }
}
