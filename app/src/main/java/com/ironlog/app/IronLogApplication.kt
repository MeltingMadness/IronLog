package com.ironlog.app

import android.app.Application
import com.ironlog.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class IronLogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@IronLogApplication)
            modules(appModule)
        }
    }
}
