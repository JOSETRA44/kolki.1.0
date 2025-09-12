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
import com.example.kolki.data.SimpleExpenseStorage
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
    
    override fun onCreate() {
        super.onCreate()
        speechRecognizer = SimpleSpeechRecognizer(this)
        voiceParser = ExpenseVoiceParser()
        
        val storage = SimpleExpenseStorage(this)
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
        
        if (currentTime - lastVolumeKeyTime < doubleTapThreshold) {
            // Double tap detected - start voice recognition
            startQuickVoiceRecognition()
        }
        
        lastVolumeKeyTime = currentTime
    }
    
    private fun startQuickVoiceRecognition() {
        speechRecognizer.startListening(object : SimpleSpeechRecognizer.SpeechCallback {
            override fun onResult(text: String) {
                handleVoiceResult(text)
            }
            
            override fun onError(error: String) {
                // Handle error silently or show notification
                stopSelf()
            }
            
            override fun onPartialResult(partialText: String) {
                // Handle partial results if needed
            }
        })
        
        // Auto-stop after 10 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            speechRecognizer.stopListening()
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
        
        speechRecognizer.stopListening()
        stopSelf()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.stopListening()
    }
}
