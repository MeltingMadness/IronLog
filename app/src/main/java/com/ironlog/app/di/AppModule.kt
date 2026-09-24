package com.ironlog.app.di

import com.ironlog.app.BuildConfig

import com.ironlog.app.data.backup.BackupDocumentIo
import com.ironlog.app.data.backup.ContentResolverBackupDocumentIo
import com.ironlog.app.data.backup.FileRecoveryBackupStore
import com.ironlog.app.data.backup.RecoveryBackupStore
import com.ironlog.app.data.db.RoomTransactionRunner
import com.ironlog.app.data.db.TransactionRunner
import com.ironlog.app.data.local.IronLogDatabase
import com.ironlog.app.data.preferences.AppPreferencesRepositoryImpl
import com.ironlog.app.data.reminder.ReminderSchedulerImpl
import com.ironlog.app.data.repository.BackupRepositoryImpl
import com.ironlog.app.data.repository.DeloadRepositoryImpl
import com.ironlog.app.data.repository.ExerciseRepositoryImpl
import com.ironlog.app.data.repository.IncidentReportRepositoryImpl
import com.ironlog.app.data.repository.MetaTrainingPlanRepositoryImpl
import com.ironlog.app.data.repository.ProgressionRepositoryImpl
import com.ironlog.app.data.repository.ReadinessProjectionSourceImpl
import com.ironlog.app.data.repository.ReadinessRepositoryImpl
import com.ironlog.app.data.repository.StatisticsRepositoryImpl
import com.ironlog.app.data.repository.TrainingPlanRepositoryImpl
import com.ironlog.app.data.repository.WorkoutRepositoryImpl
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.repository.BackupRepository
import com.ironlog.app.domain.repository.DeloadRepository
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.IncidentReportRepository
import com.ironlog.app.domain.repository.MetaTrainingPlanRepository
import com.ironlog.app.domain.repository.ProgressionRepository
import com.ironlog.app.domain.repository.ReadinessProjectionSource
import com.ironlog.app.domain.repository.ReadinessRepository
import com.ironlog.app.domain.repository.ReminderScheduler
import com.ironlog.app.domain.repository.StatisticsRepository
import com.ironlog.app.domain.repository.TrainingPlanRepository
import com.ironlog.app.domain.repository.WorkoutRepository
import com.ironlog.app.domain.progression.ProgressionEngine
import com.ironlog.app.domain.util.BuildInfo
import com.ironlog.app.presentation.dashboard.DashboardViewModel
import com.ironlog.app.presentation.exercises.ExerciseLibraryViewModel
import com.ironlog.app.presentation.history.WorkoutDetailViewModel
import com.ironlog.app.presentation.history.WorkoutHistoryViewModel
import com.ironlog.app.presentation.plans.MetaPlanEditorViewModel
import com.ironlog.app.presentation.plans.MetaPlanListViewModel
import com.ironlog.app.presentation.plans.PlanEditorViewModel
import com.ironlog.app.presentation.plans.TrainingPlanListViewModel
import com.ironlog.app.presentation.progression.ProgressionReviewViewModel
import com.ironlog.app.presentation.settings.SettingsViewModel
import com.ironlog.app.presentation.statistics.ExerciseStatsViewModel
import com.ironlog.app.presentation.workout.ActiveWorkoutViewModel
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { BuildInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE) }
    single { IronLogDatabase.create(androidContext()) }
    single { get<IronLogDatabase>().exerciseDao() }
    single { get<IronLogDatabase>().workoutSessionDao() }
    single { get<IronLogDatabase>().workoutSetDao() }
    single { get<IronLogDatabase>().personalRecordDao() }
    single { get<IronLogDatabase>().trainingPlanDao() }
    single { get<IronLogDatabase>().metaTrainingPlanDao() }
    single { get<IronLogDatabase>().progressionDao() }
    single { get<IronLogDatabase>().readinessDataDao() }
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
