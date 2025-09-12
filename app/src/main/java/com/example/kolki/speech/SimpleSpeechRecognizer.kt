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

class SimpleSpeechRecognizer(private val context: Context) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var lastPartial: String? = null
    
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
                    return
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
            }
            
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    // Pick the longest (often most complete) hypothesis
                    val result = matches.maxByOrNull { it.length } ?: matches[0]
                    Log.d(TAG, "Speech result: $result")
                    callback.onResult(result)
                } else {
                    // Fall back to last partial if available
                    if (!lastPartial.isNullOrBlank()) {
                        val text = lastPartial!!
                        lastPartial = null
                        callback.onResult(text)
                    } else {
                        callback.onError("No se reconoció ningún texto")
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
            val langTag = prefs.getString("voice_language_tag", null)
            val deviceLocale = if (!langTag.isNullOrBlank()) {
                try { Locale.forLanguageTag(langTag) } catch (_: Exception) { Locale.getDefault() }
            } else {
                // Default preferred Spanish (Peru) if device language is Spanish, else device default
                val def = Locale.getDefault()
                if (def.language.equals("es", ignoreCase = true)) Locale("es", "PE") else def
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, deviceLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, deviceLocale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                // Prefer online results for better accuracy
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                // Give user a bit more silence before auto-finishing
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000)
            }
            
            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition", e)
                callback.onError("Error al iniciar reconocimiento: ${e.message}")
                isListening = false
            }
        }
    }
    
    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
        }
        isListening = false
    }
    
    fun cleanup() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
    
    fun isListening(): Boolean = isListening
}
