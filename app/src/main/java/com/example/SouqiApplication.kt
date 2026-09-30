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
        try {
            Parse.initialize(
                Parse.Configuration.Builder(this)
                    .applicationId(getString(R.string.back4app_app_id))
                    .clientKey(getString(R.string.back4app_client_key))
                    .server(getString(R.string.back4app_server_url))
                    .build()
            )
            Log.i("SouqiApplication", "Back4App Parse initialized")
        } catch (e: Exception) {
            Log.e("SouqiApplication", "Back4App Parse initialization failed", e)
        }
    }
}
