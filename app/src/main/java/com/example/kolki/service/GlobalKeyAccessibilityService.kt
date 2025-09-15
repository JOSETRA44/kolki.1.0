package com.example.kolki.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.kolki.data.SimpleExpense
import com.example.kolki.data.SimpleExpenseStorage
import com.example.kolki.repository.ExpenseRepository
import com.example.kolki.speech.ExpenseVoiceParser
import com.example.kolki.speech.SimpleSpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class GlobalKeyAccessibilityService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private lateinit var repository: ExpenseRepository
    private lateinit var voiceParser: ExpenseVoiceParser
    private var speechRecognizer: SimpleSpeechRecognizer? = null

    private var lastKeyTimes = mutableListOf<Long>()
    private var listening = AtomicBoolean(false)
    private var autoSaveJob: Job? = null
    private lateinit var notifManager: NotificationManager
    private val channelId = "kolki_voice_channel"
    private val notifIdListening = 2001
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
                // Launch lightweight voice capture activity (overlay style)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Kolki Voz", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Escucha y guardado por voz"
            notifManager.createNotificationChannel(channel)
        }
    }

    private fun buildListeningNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Kolki")
            .setContentText("Escuchando...")
            .setOngoing(true)
            .build()
    }

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
