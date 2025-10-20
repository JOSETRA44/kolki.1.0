package com.example.kolki

import android.app.Application
import com.example.kolki.data.LegacyMigrationRunner

class KolkiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            android.util.Log.d("LegacyMigration", "Application onCreate: runIfNeeded start")
            LegacyMigrationRunner.runIfNeeded(applicationContext)
            android.util.Log.d("LegacyMigration", "Application onCreate: runIfNeeded end")
        } catch (e: Exception) {
            android.util.Log.e("LegacyMigration", "Application onCreate error: ${e.message}", e)
        }
    }
}
