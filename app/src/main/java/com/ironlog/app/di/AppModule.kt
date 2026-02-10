package com.ironlog.app.di

import com.ironlog.app.data.local.IronLogDatabase
import com.ironlog.app.data.repository.ExerciseRepositoryImpl
import com.ironlog.app.data.repository.StatisticsRepositoryImpl
import com.ironlog.app.data.repository.WorkoutRepositoryImpl
import com.ironlog.app.domain.repository.ExerciseRepository
import com.ironlog.app.domain.repository.StatisticsRepository
import com.ironlog.app.domain.repository.WorkoutRepository
import com.ironlog.app.presentation.dashboard.DashboardViewModel
import com.ironlog.app.presentation.exercises.ExerciseLibraryViewModel
import com.ironlog.app.presentation.history.WorkoutDetailViewModel
import com.ironlog.app.presentation.history.WorkoutHistoryViewModel
import com.ironlog.app.presentation.statistics.ExerciseStatsViewModel
import com.ironlog.app.presentation.workout.ActiveWorkoutViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Database
    single { IronLogDatabase.create(androidContext()) }
    single { get<IronLogDatabase>().exerciseDao() }
    single { get<IronLogDatabase>().workoutSessionDao() }
    single { get<IronLogDatabase>().workoutSetDao() }
    single { get<IronLogDatabase>().personalRecordDao() }

    // Repositories
    single<ExerciseRepository> { ExerciseRepositoryImpl(get()) }
    single<WorkoutRepository> { WorkoutRepositoryImpl(get(), get()) }
    single<StatisticsRepository> { StatisticsRepositoryImpl(get(), get()) }

    // ViewModels
    viewModel { ExerciseLibraryViewModel(get()) }
    viewModel { ActiveWorkoutViewModel(get(), get(), get(), get()) }
    viewModel { DashboardViewModel(get(), get(), get()) }
    viewModel { WorkoutHistoryViewModel(get()) }
    viewModel { WorkoutDetailViewModel(get(), get(), get(), get()) }
    viewModel { ExerciseStatsViewModel(get(), get(), get()) }
}
