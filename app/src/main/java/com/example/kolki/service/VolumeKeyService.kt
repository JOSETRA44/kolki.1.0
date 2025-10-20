package com.example.kolki.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.KeyEvent
import android.media.AudioManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.kolki.speech.SimpleSpeechRecognizer
import com.example.kolki.speech.ExpenseVoiceParser
import com.example.kolki.data.RoomStorageAdapter
import com.example.kolki.repository.ExpenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VolumeKeyService : Service() {
    
    private var lastVolumeKeyTime = 0L
    private val doubleTapThreshold = 500L // 500ms for double tap
    private lateinit var speechRecognizer: SimpleSpeechRecognizer
    private lateinit var voiceParser: ExpenseVoiceParser
    private lateinit var repository: ExpenseRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var isSessionActive: Boolean = false
    private var lastSessionEndTime: Long = 0L
    private val cooldownMs: Long = 800L
    
    override fun onCreate() {
        super.onCreate()
        speechRecognizer = SimpleSpeechRecognizer(this)
        voiceParser = ExpenseVoiceParser()
        
        val storage = RoomStorageAdapter(this)
        repository = ExpenseRepository(storage)
        
        // Speech recognizer is ready to use
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "VOLUME_KEY_PRESSED" -> {
                handleVolumeKeyPress()
            }
        }
        return START_STICKY
    }
    
    private fun handleVolumeKeyPress() {
        val currentTime = System.currentTimeMillis()
        
        // Enforce cooldown after a session ends to avoid rapid re-entry
        if (currentTime - lastSessionEndTime < cooldownMs) {
            return
        }

        if (currentTime - lastVolumeKeyTime < doubleTapThreshold) {
            // Double tap detected - start voice recognition
            startQuickVoiceRecognition()
        }
        
        lastVolumeKeyTime = currentTime
    }
    
    private fun startQuickVoiceRecognition() {
        if (isSessionActive) return
        isSessionActive = true
        speechRecognizer.startListening(object : SimpleSpeechRecognizer.SpeechCallback {
            override fun onResult(text: String) {
                handleVoiceResult(text)
            }
            
            override fun onError(error: String) {
                // Handle error silently or show notification
                try { speechRecognizer.cleanup() } catch (_: Exception) {}
                isSessionActive = false
                lastSessionEndTime = System.currentTimeMillis()
                stopSelf()
            }
            
            override fun onPartialResult(partialText: String) {
                // Handle partial results if needed
            }
        })
        
        // Auto-stop after 10 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            try { speechRecognizer.stopListening() } catch (_: Exception) {}
            try { speechRecognizer.cleanup() } catch (_: Exception) {}
            isSessionActive = false
            lastSessionEndTime = System.currentTimeMillis()
            stopSelf()
        }, 10000)
    }
    
    private fun handleVoiceResult(text: String) {
        val expense = voiceParser.parseExpenseFromVoice(text)
        
        if (expense != null) {
            serviceScope.launch {
                repository.insertExpense(expense)
                // Could show a notification here to confirm the expense was saved
            }
        }
        
        try { speechRecognizer.stopListening() } catch (_: Exception) {}
        try { speechRecognizer.cleanup() } catch (_: Exception) {}
        isSessionActive = false
        lastSessionEndTime = System.currentTimeMillis()
        stopSelf()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try { speechRecognizer.stopListening() } catch (_: Exception) {}
        try { speechRecognizer.cleanup() } catch (_: Exception) {}
        isSessionActive = false
        lastSessionEndTime = System.currentTimeMillis()
    }
}
