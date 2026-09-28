package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class SouqiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            Log.d("SouqiApplication", "FirebaseApp init skipped: ${e.message}")
        }
    }
}
