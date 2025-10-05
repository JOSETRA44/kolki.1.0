package com.example.kolki.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.kolki.speech.SimpleSpeechRecognizer

class RecognizerService : Service() {
    companion object {
        const val CHANNEL_ID = "kolki_voice_fg"
        const val NOTIF_ID = 4001
        const val ACTION_START = "com.example.kolki.action.START_LISTEN"
        const val ACTION_STOP = "com.example.kolki.action.STOP_LISTEN"
        const val ACTION_PARTIAL = "com.example.kolki.action.PARTIAL"
        const val ACTION_RESULT = "com.example.kolki.action.RESULT"
        const val ACTION_ERROR = "com.example.kolki.action.ERROR"
        const val EXTRA_TEXT = "text"
    }

    private var recognizer: SimpleSpeechRecognizer? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startListening()
            ACTION_STOP -> stopListening()
        }
        return START_STICKY
    }

    private fun startListening() {
        if (started) return
        started = true
        startForeground(NOTIF_ID, buildNotification("Escuchando…"))
        recognizer = SimpleSpeechRecognizer(this)
        recognizer?.startListening(object : SimpleSpeechRecognizer.SpeechCallback {
            override fun onResult(text: String) {
                sendBroadcast(Intent(ACTION_RESULT).putExtra(EXTRA_TEXT, text))
                stopListening()
            }
            override fun onError(error: String) {
                sendBroadcast(Intent(ACTION_ERROR).putExtra(EXTRA_TEXT, error))
                stopListening()
            }
            override fun onPartialResult(partialText: String) {
                sendBroadcast(Intent(ACTION_PARTIAL).putExtra(EXTRA_TEXT, partialText))
                // Update ongoing notification subtly
                updateNotification("Escuchando…")
            }
        })
    }

    private fun stopListening() {
        try { recognizer?.cleanup() } catch (_: Exception) {}
        recognizer = null
        started = false
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, "Kolki Voz", NotificationManager.IMPORTANCE_LOW)
            mgr.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Kolki")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, buildNotification(text))
    }
}
