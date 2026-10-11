package com.thrym.app.di

import com.thrym.app.BuildConfig

import com.thrym.app.data.backup.BackupDocumentIo
import com.thrym.app.data.backup.ContentResolverBackupDocumentIo
import com.thrym.app.data.backup.FileRecoveryBackupStore
import com.thrym.app.data.backup.RecoveryBackupStore
import com.thrym.app.data.db.RoomTransactionRunner
import com.thrym.app.data.db.TransactionRunner
import com.thrym.app.data.local.ThrymDatabase
import com.thrym.app.data.preferences.AppPreferencesRepositoryImpl
import com.thrym.app.data.reminder.ReminderSchedulerImpl
import com.thrym.app.data.repository.BackupRepositoryImpl
import com.thrym.app.data.repository.DeloadRepositoryImpl
import com.thrym.app.data.repository.ExerciseRepositoryImpl
import com.thrym.app.data.repository.IncidentReportRepositoryImpl
import com.thrym.app.data.repository.MetaTrainingPlanRepositoryImpl
import com.thrym.app.data.repository.ProgressionRepositoryImpl
import com.thrym.app.data.repository.ReadinessProjectionSourceImpl
import com.thrym.app.data.repository.ReadinessRepositoryImpl
import com.thrym.app.data.repository.StatisticsRepositoryImpl
import com.thrym.app.data.repository.TrainingPlanRepositoryImpl
import com.thrym.app.data.repository.WorkoutRepositoryImpl
import com.thrym.app.domain.repository.AppPreferencesRepository
import com.thrym.app.domain.repository.BackupRepository
import com.thrym.app.domain.repository.DeloadRepository
import com.thrym.app.domain.repository.ExerciseRepository
import com.thrym.app.domain.repository.IncidentReportRepository
import com.thrym.app.domain.repository.MetaTrainingPlanRepository
import com.thrym.app.domain.repository.ProgressionRepository
import com.thrym.app.domain.repository.ReadinessProjectionSource
import com.thrym.app.domain.repository.ReadinessRepository
import com.thrym.app.domain.repository.ReminderScheduler
import com.thrym.app.domain.repository.StatisticsRepository
import com.thrym.app.domain.repository.TrainingPlanRepository
import com.thrym.app.domain.repository.WorkoutRepository
import com.thrym.app.domain.progression.ProgressionEngine
import com.thrym.app.domain.util.BuildInfo
import com.thrym.app.presentation.dashboard.DashboardViewModel
import com.thrym.app.presentation.exercises.ExerciseLibraryViewModel
import com.thrym.app.presentation.history.WorkoutDetailViewModel
import com.thrym.app.presentation.history.WorkoutHistoryViewModel
import com.thrym.app.presentation.plans.MetaPlanEditorViewModel
import com.thrym.app.presentation.plans.MetaPlanListViewModel
import com.thrym.app.presentation.plans.PlanEditorViewModel
import com.thrym.app.presentation.plans.TrainingPlanListViewModel
import com.thrym.app.presentation.progression.ProgressionReviewViewModel
import com.thrym.app.presentation.settings.SettingsViewModel
import com.thrym.app.presentation.statistics.ExerciseStatsViewModel
import com.thrym.app.presentation.workout.ActiveWorkoutViewModel
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single<com.thrym.app.presentation.common.UiStrings> {
        val context = androidContext().applicationContext
        com.thrym.app.presentation.common.UiStrings { resourceId, args ->
            context.getString(resourceId, *args)
        }
    }
    single { BuildInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE) }
    single { ThrymDatabase.create(androidContext()) }
    single { get<ThrymDatabase>().exerciseDao() }
    single { get<ThrymDatabase>().workoutSessionDao() }
    single { get<ThrymDatabase>().workoutSetDao() }
    single { get<ThrymDatabase>().personalRecordDao() }
    single { get<ThrymDatabase>().trainingPlanDao() }
    single { get<ThrymDatabase>().metaTrainingPlanDao() }
    single { get<ThrymDatabase>().progressionDao() }
    single { get<ThrymDatabase>().readinessDataDao() }
    single<TransactionRunner> { RoomTransactionRunner(get()) }
    single<BackupDocumentIo> {
        ContentResolverBackupDocumentIo(androidContext())
    }
    single<RecoveryBackupStore> { FileRecoveryBackupStore(androidContext()) }

    single<ExerciseRepository> { ExerciseRepositoryImpl(get()) }
    single<WorkoutRepository> {
        val appPreferencesRepository = get<AppPreferencesRepository>()
        WorkoutRepositoryImpl(
            get(), get(), get(), get(), get(), get(),
            // Set intentions are persisted through the readiness store inside the
            // same Room transaction as the set itself.
            get<ReadinessRepository>()
        ) {
            // Captured at session start so history keeps the deload context that was
            // in effect back then, instead of re-deriving it from the current switch.
            appPreferencesRepository.preferences.first().deloadMode
        }
    }
    single { ProgressionEngine() }
    single<ProgressionRepository> { ProgressionRepositoryImpl(get(), get(), get(), get(), get(), get()) }
    single<ReadinessRepository> { ReadinessRepositoryImpl(get(), get(), get()) }
    single<ReadinessProjectionSource> { ReadinessProjectionSourceImpl(get(), get()) }
    single<StatisticsRepository> { StatisticsRepositoryImpl(get(), get()) }
    single<DeloadRepository> { DeloadRepositoryImpl(get(), get(), get()) }
    single<TrainingPlanRepository> { TrainingPlanRepositoryImpl(get()) }
    single<MetaTrainingPlanRepository> { MetaTrainingPlanRepositoryImpl(get()) }
    single<AppPreferencesRepository> { AppPreferencesRepositoryImpl(androidContext(), get()) }
    single<ReminderScheduler> { ReminderSchedulerImpl(androidContext()) }
    single<IncidentReportRepository> { IncidentReportRepositoryImpl(androidContext(), get()) }
    single<BackupRepository> {
        BackupRepositoryImpl(
            transactionRunner = get(),
            documentIo = get(),
            recoveryStore = get(),
            exerciseDao = get(),
            workoutSessionDao = get(),
            workoutSetDao = get(),
            trainingPlanDao = get(),
            metaTrainingPlanDao = get(),
            personalRecordDao = get(),
            progressionDao = get(),
            readinessDataDao = get(),
            buildInfo = get()
        )
    }

    // ViewModels WITH SavedStateHandle — must use viewModelOf() so Koin
    // automatically provides SavedStateHandle from Navigation's CreationExtras
    viewModelOf(::ActiveWorkoutViewModel)
    viewModelOf(::WorkoutDetailViewModel)
    viewModelOf(::ExerciseStatsViewModel)
    viewModelOf(::PlanEditorViewModel)
    viewModelOf(::ProgressionReviewViewModel)

    // ViewModels WITHOUT SavedStateHandle — viewModelOf() for consistency
    viewModelOf(::ExerciseLibraryViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::WorkoutHistoryViewModel)
    viewModelOf(::TrainingPlanListViewModel)
    viewModelOf(::MetaPlanListViewModel)
    viewModelOf(::MetaPlanEditorViewModel)
    viewModelOf(::SettingsViewModel)
}
