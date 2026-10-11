package com.thrym.app

import com.thrym.app.data.reminder.TrainingReminderNotifier
import com.thrym.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class ThrymApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@ThrymApplication)
            modules(appModule)
        }

        TrainingReminderNotifier.ensureChannel(this)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Keeps the channel name in the system settings in the current app language.
        TrainingReminderNotifier.ensureChannel(this)
    }
}
