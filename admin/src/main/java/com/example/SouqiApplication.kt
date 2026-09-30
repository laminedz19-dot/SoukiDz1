package com.example

import android.app.Application
import android.util.Log
import com.parse.Parse

class SouqiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("SouqiCrashHandler", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        val applicationId = BuildConfig.BACK4APP_APPLICATION_ID.trim()
        val serverUrl = BuildConfig.BACK4APP_SERVER_URL.trim()
        if (applicationId.isBlank() || serverUrl.isBlank()) {
            Log.w("SouqiApplication", "Back4App is not configured; cloud features will remain unavailable")
            return
        }
        Parse.initialize(
            Parse.Configuration.Builder(this)
                .applicationId(applicationId)
                .clientKey(BuildConfig.BACK4APP_CLIENT_KEY.trim())
                .server(serverUrl)
                .build()
        )
    }
}
