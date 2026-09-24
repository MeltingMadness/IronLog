package com.ironlog.shared.plates

import kotlin.math.max
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The result of loading one side of a barbell.
 *
 * [weightPerSideKg] is the requested load excluding the barbell.  The
 * [platesPerSide] list is ordered from the heaviest denomination to the
 * lightest one and may contain a denomination more than once.  The result
 * carries [reachableWeightPerSideKg] explicitly so it is also present in the
 * JSON/Swift representation; it is always the sum of [platesPerSide].
 */
@Serializable
data class PlateCalculationResult(
    val targetWeightKg: Double,
    val barbellWeightKg: Double,
    val weightPerSideKg: Double,
    val platesPerSide: List<Double>,
    val reachableWeightPerSideKg: Double,
    val remainderKg: Double,
    val isExact: Boolean
)

/**
 * Common implementation of the plate loading algorithm.
 *
 * The Android implementation historically used java.math.BigDecimal.  This
 * version keeps the same bounded integer dynamic-programming algorithm while
 * representing decimal values with a small, platform-independent decimal
 * parser.  That keeps the exact-fit and never-overweight guarantees on iOS,
 * Android and any future KMP target without a Java dependency.
 */
object SharedPlateCalculator {
    val STANDARD_AVAILABLE_PLATES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5)
    val DEFAULT_USER_PLATES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
    const val DEFAULT_BARBELL_KG = 20.0

    private const val MAX_DECIMAL_PLACES = 6
    private const val MIN_REPRESENTABLE_PLATE_KG = 0.000001
    private const val MAX_SEARCH_UNITS = 2_000_000
    private const val MAX_RESULT_PLATES = 10_000
    private const val EXACT_TOLERANCE_KG = 0.000001
    private val json = Json { encodeDefaults = true }

    /**
     * Calculates the plates needed on each side of a barbell.
     *
     * Every valid denomination may be used more than once.  The exact
     * combination with the fewest plates is selected when one exists.  When
     * the requested load is not representable, the largest reachable load
     * below the request is selected and its remainder is reported.
     */
    fun calculate(
        targetWeightKg: Double,
        barbellWeightKg: Double = DEFAULT_BARBELL_KG,
        availablePlates: List<Double> = DEFAULT_USER_PLATES
    ): PlateCalculationResult {
        if (!targetWeightKg.isFinite() || !barbellWeightKg.isFinite() || barbellWeightKg < 0.0) {
            return invalidResult(targetWeightKg, barbellWeightKg)
        }

        if (targetWeightKg <= barbellWeightKg) {
            return result(
                targetWeightKg = targetWeightKg,
                barbellWeightKg = barbellWeightKg,
                weightPerSideKg = 0.0,
                plates = emptyList(),
                isExact = targetWeightKg == barbellWeightKg
            )
        }

        val neededPerSideKg = (targetWeightKg - barbellWeightKg) / 2.0
        if (!neededPerSideKg.isFinite() || neededPerSideKg <= 0.0) {
            return invalidResult(targetWeightKg, barbellWeightKg)
        }

        val finitePositivePlates = availablePlates
            .asSequence()
            .filter { it.isFinite() && it >= MIN_REPRESENTABLE_PLATE_KG }
            .distinct()
            .sortedDescending()
            .toList()

        if (finitePositivePlates.isEmpty()) {
            return resultFor(targetWeightKg, barbellWeightKg, neededPerSideKg, emptyList())
        }

        val requiredDecimalPlaces = max(
            decimalPlaces(neededPerSideKg),
            finitePositivePlates.maxOf(::decimalPlaces)
        )
        val minimumDecimalPlaces = finitePositivePlates
            .maxOf(::decimalPlaces)
            .coerceAtMost(MAX_DECIMAL_PLACES)
        var decimalPlaces = requiredDecimalPlaces.coerceAtMost(MAX_DECIMAL_PLACES)
        var targetUnits = toUnits(neededPerSideKg, decimalPlaces, DecimalRounding.FLOOR)

        // Imported/display values can carry many decimal places.  Reduce the
        // scale until the bounded DP table is affordable, always flooring the
        // target so this fallback cannot turn an under-target load overweight.
        while (true) {
            val unitsAtScale = targetUnits ?: break
            if (unitsAtScale <= MAX_SEARCH_UNITS || decimalPlaces <= minimumDecimalPlaces) break
            decimalPlaces--
            targetUnits = toUnits(neededPerSideKg, decimalPlaces, DecimalRounding.FLOOR)
        }

        val normalizedTargetUnits = targetUnits ?: return largeInputResult(
            targetWeightKg,
            barbellWeightKg,
            neededPerSideKg,
            finitePositivePlates
        )
        if (normalizedTargetUnits <= 0L) {
            return resultFor(targetWeightKg, barbellWeightKg, neededPerSideKg, emptyList())
        }

        val plateUnits = finitePositivePlates.mapNotNull { plate ->
            toUnits(plate, decimalPlaces, DecimalRounding.CEILING)
                ?.takeIf { it > 0L && it <= normalizedTargetUnits }
                ?.let { units -> plate to units }
        }.distinctBy { it.second }

        if (plateUnits.isEmpty()) {
            return resultFor(targetWeightKg, barbellWeightKg, neededPerSideKg, emptyList())
        }

        if (normalizedTargetUnits > MAX_SEARCH_UNITS || normalizedTargetUnits > Int.MAX_VALUE) {
            return largeInputResult(
                targetWeightKg,
                barbellWeightKg,
                neededPerSideKg,
                plateUnits.map { it.first }
            )
        }

        val target = normalizedTargetUnits.toInt()
        val searchPlateUnits = plateUnits.filter { it.second <= Int.MAX_VALUE.toLong() }
        if (searchPlateUnits.isEmpty()) {
            return resultFor(targetWeightKg, barbellWeightKg, neededPerSideKg, emptyList())
        }

        val denominations = searchPlateUnits.map { it.first }
        val units = searchPlateUnits.map { it.second.toInt() }
        val plateCount = denominations.size
        val unreachable = Int.MAX_VALUE
        val bestCount = IntArray(target + 1) { unreachable }
        val preferenceScore = LongArray(target + 1)
        val previousSum = IntArray(target + 1) { -1 }
        val previousPlate = IntArray(target + 1) { -1 }
        bestCount[0] = 0

        for (sum in 0..target) {
            if (bestCount[sum] == unreachable) continue
            for (plateIndex in 0 until plateCount) {
                val nextLong = sum.toLong() + units[plateIndex].toLong()
                if (nextLong > target) continue
                val next = nextLong.toInt()
                val candidateCount = bestCount[sum] + 1
                val candidatePreference = preferenceScore[sum] + (plateCount - plateIndex).toLong()
                if (
                    candidateCount < bestCount[next] ||
                    (candidateCount == bestCount[next] && candidatePreference > preferenceScore[next])
                ) {
                    bestCount[next] = candidateCount
                    preferenceScore[next] = candidatePreference
                    previousSum[next] = sum
                    previousPlate[next] = plateIndex
                }
            }
        }

        var reachableUnits = target
        while (reachableUnits > 0 && bestCount[reachableUnits] == unreachable) reachableUnits--
        val selectedIndices = reconstructIndices(reachableUnits, previousSum, previousPlate)
        val selectedPlates = selectedIndices.sorted().map { denominations[it] }
        return resultFor(targetWeightKg, barbellWeightKg, neededPerSideKg, selectedPlates)
    }

    /** Stable JSON output for a thin Swift bridge or snapshot tests. */
    fun calculateJson(
        targetWeightKg: Double,
        barbellWeightKg: Double = DEFAULT_BARBELL_KG,
        availablePlates: List<Double> = DEFAULT_USER_PLATES
    ): String = json.encodeToString(
        calculate(targetWeightKg, barbellWeightKg, availablePlates)
    )

    private fun resultFor(
        targetWeightKg: Double,
        barbellWeightKg: Double,
        neededPerSideKg: Double,
        plates: List<Double>
    ): PlateCalculationResult {
        val boundedPlates = plates.toMutableList()
        while (
            boundedPlates.isNotEmpty() &&
            boundedPlates.sum() > neededPerSideKg + EXACT_TOLERANCE_KG
        ) {
            boundedPlates.removeAt(boundedPlates.lastIndex)
        }
        val reachablePerSideKg = boundedPlates.sum()
        val remainderKg = (neededPerSideKg - reachablePerSideKg).coerceAtLeast(0.0)
        return result(
            targetWeightKg = targetWeightKg,
            barbellWeightKg = barbellWeightKg,
            weightPerSideKg = neededPerSideKg,
            plates = boundedPlates,
            isExact = remainderKg <= EXACT_TOLERANCE_KG,
            remainderKg = remainderKg
        )
    }

    private fun result(
        targetWeightKg: Double,
        barbellWeightKg: Double,
        weightPerSideKg: Double,
        plates: List<Double>,
        isExact: Boolean,
        remainderKg: Double = 0.0
    ): PlateCalculationResult {
        val reachable = plates.sum()
        return PlateCalculationResult(
            targetWeightKg = targetWeightKg,
            barbellWeightKg = barbellWeightKg,
            weightPerSideKg = weightPerSideKg,
            platesPerSide = plates,
            reachableWeightPerSideKg = reachable,
            remainderKg = if (isExact) 0.0 else remainderKg,
            isExact = isExact
        )
    }

    private fun invalidResult(targetWeightKg: Double, barbellWeightKg: Double): PlateCalculationResult {
        val safeTarget = targetWeightKg.takeIf { it.isFinite() } ?: 0.0
        val safeBarbell = barbellWeightKg.takeIf { it.isFinite() && it >= 0.0 } ?: DEFAULT_BARBELL_KG
        return result(
            targetWeightKg = safeTarget,
            barbellWeightKg = safeBarbell,
            weightPerSideKg = 0.0,
            plates = emptyList(),
            isExact = false
        )
    }

    private fun largeInputResult(
        targetWeightKg: Double,
        barbellWeightKg: Double,
        neededPerSideKg: Double,
        plates: List<Double>
    ): PlateCalculationResult {
        val largestPlate = plates.firstOrNull { it.isFinite() && it > 0.0 }
            ?: return resultFor(targetWeightKg, barbellWeightKg, neededPerSideKg, emptyList())
        val largestCount = (neededPerSideKg / largestPlate).toLong()
        if (largestCount <= 0L || largestCount > MAX_RESULT_PLATES.toLong()) {
            return resultFor(targetWeightKg, barbellWeightKg, neededPerSideKg, emptyList())
        }
        return resultFor(
            targetWeightKg,
            barbellWeightKg,
            neededPerSideKg,
            List(largestCount.toInt()) { largestPlate }
        )
    }

    private fun reconstructIndices(
        reachableUnits: Int,
        previousSum: IntArray,
        previousPlate: IntArray
    ): List<Int> {
        if (reachableUnits <= 0) return emptyList()
        val indices = ArrayList<Int>()
        var current = reachableUnits
        while (current > 0) {
            val plateIndex = previousPlate[current]
            val prior = previousSum[current]
            if (plateIndex < 0 || prior < 0 || prior >= current) return emptyList()
            indices += plateIndex
            current = prior
        }
        return indices
    }

    /**
     * Returns the non-negative decimal scale of Double.toString(), matching
     * BigDecimal.valueOf(value).stripTrailingZeros().scale() for finite values.
     */
    private fun decimalPlaces(value: Double): Int {
        val text = value.toString()
        val exponentMarker = text.indexOfFirst { it == 'e' || it == 'E' }
        val mantissa: String
        val exponent: Int
        if (exponentMarker >= 0) {
            mantissa = text.substring(0, exponentMarker)
            exponent = text.substring(exponentMarker + 1).toIntOrNull() ?: return MAX_DECIMAL_PLACES
        } else {
            mantissa = text
            exponent = 0
        }
        val unsigned = mantissa.removePrefix("-").removePrefix("+")
        val dot = unsigned.indexOf('.')
        val fractionalDigits = if (dot >= 0) unsigned.length - dot - 1 else 0
        val significantFractionalDigits = if (dot >= 0) {
            unsigned.substring(dot + 1).trimEnd('0').length
        } else {
            0
        }
        return (significantFractionalDigits - exponent).coerceAtLeast(0)
    }

    /** Parse and scale a finite non-negative decimal without BigDecimal. */
    private fun toUnits(value: Double, scale: Int, rounding: DecimalRounding): Long? {
        if (!value.isFinite() || value < 0.0 || scale < 0) return null
        val text = value.toString()
        val exponentMarker = text.indexOfFirst { it == 'e' || it == 'E' }
        val mantissa: String
        val exponent: Int
        if (exponentMarker >= 0) {
            mantissa = text.substring(0, exponentMarker)
            exponent = text.substring(exponentMarker + 1).toIntOrNull() ?: return null
        } else {
            mantissa = text
            exponent = 0
        }
        val unsigned = mantissa.removePrefix("+")
        val negative = unsigned.startsWith('-')
        if (negative) return null
        val digitsWithZeros = unsigned.removePrefix("-")
            .replace(".", "")
            .trimStart('0')
        if (digitsWithZeros.isEmpty()) return 0L
        val dot = unsigned.indexOf('.')
        val fractionalDigits = if (dot >= 0) unsigned.length - dot - 1 else 0
        val shift = exponent - fractionalDigits + scale
        if (shift >= 0) {
            val multiplier = powerOfTen(shift) ?: return null
            val digits = digitsWithZeros.toLongOrNull() ?: return null
            return multiplyExact(digits, multiplier)
        }

        val divisor = powerOfTen(-shift)
        val digits = digitsWithZeros.toLongOrNull() ?: return null
        if (divisor == null) {
            return if (rounding == DecimalRounding.CEILING && digits > 0L) 1L else 0L
        }
        val quotient = digits / divisor
        val remainder = digits % divisor
        return if (rounding == DecimalRounding.CEILING && remainder != 0L) {
            if (quotient == Long.MAX_VALUE) null else quotient + 1L
        } else {
            quotient
        }
    }

    private fun powerOfTen(exponent: Int): Long? {
        if (exponent < 0 || exponent > 18) return null
        var value = 1L
        repeat(exponent) {
            if (value > Long.MAX_VALUE / 10L) return null
            value *= 10L
        }
        return value
    }

    private fun multiplyExact(left: Long, right: Long): Long? {
        if (left < 0L || right < 0L || (left != 0L && right > Long.MAX_VALUE / left)) return null
        return left * right
    }

    private enum class DecimalRounding { FLOOR, CEILING }
}

/** Thin, stable facade intended for Swift/JSON bridges. */
object PlateCalculatorFacade {
    fun calculate(
        targetWeightKg: Double,
        barbellWeightKg: Double = SharedPlateCalculator.DEFAULT_BARBELL_KG,
        availablePlates: List<Double> = SharedPlateCalculator.DEFAULT_USER_PLATES
    ): PlateCalculationResult = SharedPlateCalculator.calculate(
        targetWeightKg,
        barbellWeightKg,
        availablePlates
    )

    fun calculateJson(
        targetWeightKg: Double,
        barbellWeightKg: Double = SharedPlateCalculator.DEFAULT_BARBELL_KG,
        availablePlates: List<Double> = SharedPlateCalculator.DEFAULT_USER_PLATES
    ): String = SharedPlateCalculator.calculateJson(targetWeightKg, barbellWeightKg, availablePlates)
}
