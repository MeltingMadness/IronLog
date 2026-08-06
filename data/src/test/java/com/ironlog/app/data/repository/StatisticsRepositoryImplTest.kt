package com.ironlog.app.data.repository

import com.ironlog.app.data.local.dao.PersonalRecordDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.EpochConverter
import com.ironlog.app.data.local.entity.PersonalRecordEntity
import com.ironlog.app.domain.model.RecordType
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.TimeZone

class StatisticsRepositoryImplTest {

    private val personalRecordDao: PersonalRecordDao = mockk(relaxed = true)
    private val workoutSetDao: WorkoutSetDao = mockk()
    private val repository = StatisticsRepositoryImpl(personalRecordDao, workoutSetDao)

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        // A fixed, large offset from UTC (+14h, no DST) makes the old UTC-based bug obvious.
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `checkAndUpdateRecord stores achievedAt using system zone via EpochConverter`() = runTest {
        coEvery { personalRecordDao.getRecord(1L, RecordType.MAX_WEIGHT.name) } returns null
        val captured = slot<PersonalRecordEntity>()
        coEvery { personalRecordDao.insert(capture(captured)) } returns 1L

        val before = EpochConverter.toLong(LocalDateTime.now())
        repository.checkAndUpdateRecord(1L, RecordType.MAX_WEIGHT, 100.0)
        val after = EpochConverter.toLong(LocalDateTime.now())

        val achievedAt = captured.captured.achievedAt
        assertTrue(
            "achievedAt ($achievedAt) should be within the system-zone window [$before, $after]",
            achievedAt in before..after
        )

        // The previous implementation used `now.toEpochSecond(ZoneOffset.UTC) * 1000`, which in a
        // UTC+14 zone is off by ~14h from the wall-clock time everywhere else in the app uses.
        val buggyUtcValue = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) * 1000
        assertTrue(
            "achievedAt should not match the old UTC-based (bugged) calculation",
            kotlin.math.abs(achievedAt - buggyUtcValue) > 60_000L
        )
    }
}
