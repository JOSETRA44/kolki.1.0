package com.example.kolki.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.example.kolki.data.SimpleExpenseStorage
import com.example.kolki.repository.ExpenseRepository
import com.example.kolki.speech.ExpenseVoiceParser
import java.util.concurrent.atomic.AtomicBoolean

class GlobalKeyAccessibilityService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private lateinit var repository: ExpenseRepository
    private lateinit var voiceParser: ExpenseVoiceParser
    private var lastKeyTimes = mutableListOf<Long>()
    private var listening = AtomicBoolean(false)
    private var lastLaunchAt: Long = 0L
    private lateinit var notifManager: NotificationManager
    private val channelId = "kolki_voice_channel"
    private val notifIdSaved = 2002

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        repository = ExpenseRepository(SimpleExpenseStorage(this))
        voiceParser = ExpenseVoiceParser()
        notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannelIfNeeded()

        // Ensure we receive key events
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val enabled = prefs.getBoolean("global_access_enabled", false)
        if (!enabled) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            val mode = prefs.getString("global_key_mode", "both") ?: "both"
            val isUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
            val isDown = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            val allowed = when (mode) {
                "up" -> isUp
                "down" -> isDown
                else -> (isUp || isDown)
            }
            if (!allowed) return false

            val windowMs = prefs.getInt("global_press_window_ms", 500)
            val requiredPresses = prefs.getInt("global_press_count", 3)
            val now = System.currentTimeMillis()

            // Track recent presses within window
            lastKeyTimes.add(now)
            lastKeyTimes = lastKeyTimes.filter { now - it <= windowMs }.toMutableList()

            // Debug notification (disabled by default). Enable by setting prefs "global_debug_notify"=true
            if (prefs.getBoolean("global_debug_notify", false)) {
                postSavedNotification("Vol key: ${'$'}{event.keyCode} count=${'$'}{lastKeyTimes.size}/${'$'}requiredPresses in ${'$'}windowMs ms")
            }

            if (lastKeyTimes.size >= requiredPresses && listening.compareAndSet(false, true)) {
                // Cooldown to avoid double-launch within a short window
                val cooldownMs = prefs.getInt("global_launch_cooldown_ms", 800)
                if (now - lastLaunchAt < cooldownMs) {
                    if (prefs.getBoolean("global_debug_notify", false)) {
                        postSavedNotification("Trigger ignorado (cooldown)")
                    }
                    listening.set(false)
                    lastKeyTimes.clear()
                    return true
                }
                lastLaunchAt = now
                // Debug: notify that trigger threshold was reached
                if (prefs.getBoolean("global_debug_notify", false)) {
                    postSavedNotification("Trigger global: iniciando voz")
                }
                // Launch overlay directly (previous stable behavior)
                try {
                    com.example.kolki.ui.quick.QuickVoiceActivity.launchFromService(this)
                } catch (e: Exception) {
                    postSavedNotification("No se pudo iniciar voz: ${e.message}")
                }
                listening.set(false)
                lastKeyTimes.clear()
                return true // consume
            }
        }
        return false
    }

    override fun onInterrupt() { }

    private fun createChannelIfNeeded() {
        // Min SDK is 26, so channel APIs are always available
        val channel = NotificationChannel(channelId, "Kolki Voz", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Escucha y guardado por voz"
        notifManager.createNotificationChannel(channel)
    }

    // (No se usa actualmente)

    private fun postSavedNotification(text: String) {
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_input_add)
            .setContentTitle("Kolki")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        notifManager.notify(notifIdSaved, notif)
    }
}
