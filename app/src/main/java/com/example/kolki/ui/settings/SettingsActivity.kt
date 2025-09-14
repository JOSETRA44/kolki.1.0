package com.example.kolki.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.kolki.R

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Kolki) // ensure app theme
        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }
}
