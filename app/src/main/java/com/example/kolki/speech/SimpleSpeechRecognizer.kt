package com.example.kolki.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*
import android.content.Context.MODE_PRIVATE
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

class SimpleSpeechRecognizer(private val context: Context) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var lastPartial: String? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var previousAudioMode: Int? = null
    private var retriedOnce: Boolean = false
    
    companion object {
        private const val TAG = "SimpleSpeechRecognizer"
    }
    
    interface SpeechCallback {
        fun onResult(text: String)
        fun onError(error: String)
        fun onPartialResult(partialText: String)
    }
    
    fun startListening(callback: SpeechCallback) {
        if (isListening) {
            Log.w(TAG, "Already listening")
            return
        }
        
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback.onError("Reconocimiento de voz no disponible")
            return
        }
        // Ensure we run on main looper
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            // Prepare Audio for voice capture (Android 10+ friendly)
            try {
                audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                previousAudioMode = audioManager?.mode
                // Switch to voice communication mode to improve mic routing and echo canceller
                audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val afr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener { /* no-op */ }
                    .setAudioAttributes(attrs)
                    .build()
                val res = audioManager?.requestAudioFocus(afr)
                if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    audioFocusRequest = afr
                } else {
                    Log.w(TAG, "AudioFocus not granted: $res")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Audio setup failed: ${e.message}")
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
                isListening = true
            }
            
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Beginning of speech")
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // Volume level changed
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {
                // Audio buffer received
            }
            
            override fun onEndOfSpeech() {
                Log.d(TAG, "End of speech")
                isListening = false
            }
            
            override fun onError(error: Int) {
                isListening = false
                // If we have a partial, surface it as a result instead of error
                if (error == SpeechRecognizer.ERROR_NO_MATCH && !lastPartial.isNullOrBlank()) {
                    val text = lastPartial!!
                    lastPartial = null
                    callback.onResult(text)
                    releaseAudio()
                    return
                }
                // One-time minimal intent retry for intermittent engine issues
                if (!retriedOnce && (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_NO_MATCH)) {
                    retriedOnce = true
                    try {
                        // Minimal intent: only essentials, avoid extras that some engines reject
                        val minimal = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        }
                        // Try to restart quickly
                        speechRecognizer?.startListening(minimal)
                        return
                    } catch (_: Exception) { /* fall through to error */ }
                }
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                    SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permisos insuficientes"
                    SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de red"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No se encontró coincidencia"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado"
                    SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout de voz"
                    else -> "Error desconocido: $error"
                }
                Log.e(TAG, "Speech recognition error: $errorMessage")
                callback.onError(errorMessage)
                releaseAudio()
            }
            
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    // Pick the longest (often most complete) hypothesis
                    val result = matches.maxByOrNull { it.length } ?: matches[0]
                    Log.d(TAG, "Speech result: $result")
                    callback.onResult(result)
                    releaseAudio()
                } else {
                    // Fall back to last partial if available
                    if (!lastPartial.isNullOrBlank()) {
                        val text = lastPartial!!
                        lastPartial = null
                        callback.onResult(text)
                        releaseAudio()
                    } else {
                        callback.onError("No se reconoció ningún texto")
                        releaseAudio()
                    }
                }
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    lastPartial = matches[0]
                    callback.onPartialResult(matches[0])
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {
                // Speech event
            }
            })
            
            // Language selection: from prefs or device default
            val prefs = context.getSharedPreferences("kolki_prefs", MODE_PRIVATE)
            val prefLangTag = prefs.getString("voice_language_tag", null)
            val deviceLocale = if (!prefLangTag.isNullOrBlank()) {
                try { Locale.forLanguageTag(prefLangTag) } catch (_: Exception) { Locale.getDefault() }
            } else {
                Locale.getDefault()
            }
            val localeTag = try { deviceLocale.toLanguageTag() } catch (_: Exception) { deviceLocale.toString() }
            val preferOffline = prefs.getBoolean("voice_prefer_offline", false)
            retriedOnce = false
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                // Some engines require calling package for better routing
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Limit to best hypothesis to speed up results
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Prefer offline results (configurable)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                // More tolerant timings for partials and natural pauses (ms)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2200)
                // Allow a bit more minimum speaking time
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500)
            }
            
            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition", e)
                callback.onError("Error al iniciar reconocimiento: ${e.message}")
                isListening = false
                releaseAudio()
            }
        }
    }
    
    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
        }
        isListening = false
        releaseAudio()
    }
    
    fun cleanup() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
    
    @Suppress("unused")
    fun isListening(): Boolean = isListening

    private fun releaseAudio() {
        try {
            audioFocusRequest?.let { afr ->
                audioManager?.abandonAudioFocusRequest(afr)
            }
            audioFocusRequest = null
            previousAudioMode?.let { mode ->
                audioManager?.mode = mode
            }
            previousAudioMode = null
        } catch (e: Exception) {
            Log.w(TAG, "Audio release failed: ${e.message}")
        }
    }
}
