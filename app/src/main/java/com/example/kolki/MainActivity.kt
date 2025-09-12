package com.example.kolki

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.kolki.databinding.ActivityMainBinding
import com.example.kolki.service.VolumeKeyService
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.MaterialColors
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    companion object {
        private const val REQUEST_PERMISSIONS = 100
    }

    override fun onResume() {
        super.onResume()
        // Deferred global activation: if pending flag is set, navigate to Add and start voice
        try {
            val prefs = getSharedPreferences("kolki_prefs", MODE_PRIVATE)
            if (prefs.getBoolean("pending_voice_add", false)) {
                prefs.edit().putBoolean("pending_voice_add", false).apply()
                val navController = findNavController(R.id.nav_host_fragment_activity_main)
                val args = android.os.Bundle().apply { putBoolean("startVoice", true) }
                navController.navigate(R.id.navigation_add_expense, args)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error navigating for pending voice add: ${e.message}", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            val navView: BottomNavigationView = binding.navView

            val navController = findNavController(R.id.nav_host_fragment_activity_main)
            val appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.navigation_expenses, R.id.navigation_statistics, R.id.navigation_add_expense, 
                    R.id.navigation_profile, R.id.navigation_settings
                )
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            navView.setupWithNavController(navController)
            // Ensure status bar matches app primary color to avoid white strip
            val primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, android.graphics.Color.BLACK)
            window.statusBarColor = primary
            // Ensure icons/text are light over the colored status bar
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
            
            // Request necessary permissions
            requestPermissions()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in onCreate: ${e.message}", e)
            finish()
        }
    }
    
    private fun requestPermissions() {
        val basePermissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        // Android 13+ requires runtime POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            basePermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = basePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS
            )
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return try {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                // Start volume key service for double-tap detection
                val intent = Intent(this, VolumeKeyService::class.java)
                intent.action = "VOLUME_KEY_PRESSED"
                startService(intent)
                true // Consume the event
            } else {
                super.onKeyDown(keyCode, event)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in onKeyDown: ${e.message}", e)
            super.onKeyDown(keyCode, event)
        }
    }
}