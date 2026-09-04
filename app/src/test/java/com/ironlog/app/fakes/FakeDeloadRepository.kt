package com.ironlog.app.fakes

import com.ironlog.app.domain.model.DeloadAssessment
import com.ironlog.app.domain.repository.DeloadRepository
import java.time.LocalDate

class FakeDeloadRepository(
    var assessment: DeloadAssessment = DeloadAssessment(
        recommended = false,
        fatigueScore = 0,
        signals = emptyList(),
        windowStart = LocalDate.now().minusDays(21),
        windowEnd = LocalDate.now(),
        sessionCount = 0
    )
) : DeloadRepository {

    override suspend fun assess(): DeloadAssessment = assessment
}