package com.ironlog.app.presentation.dashboard

import com.ironlog.app.domain.model.TrainingPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecommendedPlanTest {

    private val upper = TrainingPlan(id = 1L, name = "Oberkörper")
    private val lower = TrainingPlan(id = 2L, name = "Unterkörper")
    private val full = TrainingPlan(id = 3L, name = "Ganzkörper")

    @Test
    fun `ohne Plaene gibt es keine Empfehlung`() {
        assertNull(recommendedPlan(emptyList(), emptyList()))
    }

    @Test
    fun `nie trainierter Plan kommt zuerst`() {
        val result = recommendedPlan(
            emptyList(),
            listOf(DashboardPlanStatus(upper, 1L), DashboardPlanStatus(lower, null), DashboardPlanStatus(full, 9L))
        )
        assertEquals(lower, result?.plan)
        assertNull(result?.lastDoneDaysAgo)
    }

    @Test
    fun `sonst der am laengsten nicht trainierte Plan`() {
        val result = recommendedPlan(
            emptyList(),
            listOf(DashboardPlanStatus(upper, 4L), DashboardPlanStatus(lower, 6L), DashboardPlanStatus(full, 6L))
        )
        // Gleichstand: Reihenfolge der Liste entscheidet.
        assertEquals(lower, result?.plan)
        assertEquals(6L, result?.lastDoneDaysAgo)
    }

    @Test
    fun `naechster Teilplan eines Meta-Plans hat Vorrang`() {
        val meta = DashboardMetaPlanOption(
            metaPlanId = 7L,
            metaPlanName = "Ober/Unter",
            nextPlan = upper,
            rotationPlans = listOf(DashboardMetaSubPlanStatus(upper, 3L), DashboardMetaSubPlanStatus(lower, 1L)),
            canSkip = true
        )
        val result = recommendedPlan(listOf(meta), listOf(DashboardPlanStatus(lower, null)))
        assertEquals(upper, result?.plan)
        assertEquals(meta, result?.metaPlan)
        assertEquals(3L, result?.lastDoneDaysAgo)
    }

    @Test
    fun `Meta-Empfehlung nennt das letzte Training des Plans auch ausserhalb der Rotation`() {
        val meta = DashboardMetaPlanOption(
            metaPlanId = 7L,
            metaPlanName = "Ober/Unter",
            nextPlan = upper,
            rotationPlans = listOf(DashboardMetaSubPlanStatus(upper, null)),
            canSkip = true
        )
        val result = recommendedPlan(listOf(meta), listOf(DashboardPlanStatus(upper, 4L)))
        assertEquals(4L, result?.lastDoneDaysAgo)
    }
}
