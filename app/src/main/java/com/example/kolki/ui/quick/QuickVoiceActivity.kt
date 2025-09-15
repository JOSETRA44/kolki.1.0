package com.example.kolki.ui.quick

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.example.kolki.R
import com.example.kolki.data.SimpleExpense
import com.example.kolki.data.SimpleExpenseStorage
import com.example.kolki.repository.ExpenseRepository
import com.example.kolki.speech.ExpenseVoiceParser
import com.example.kolki.speech.SimpleSpeechRecognizer
import android.view.WindowManager
import kotlinx.coroutines.*
import java.util.*

class QuickVoiceActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    // Direct in-activity speech recognition (no service)
    private lateinit var parser: ExpenseVoiceParser
    private lateinit var repository: ExpenseRepository
    private val scope = CoroutineScope(Dispatchers.Main)
    private var timeoutJob: Job? = null
    private var lastPartialText: String? = null
    private var isHandlingResult: Boolean = false
    private var suppressError: Boolean = false
    private var retriedOnce: Boolean = false
    private var speechRecognizer: SimpleSpeechRecognizer? = null

    private val notifChannelId = "kolki_voice_channel"
    private val notifIdSaved = 3001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure overlay is visible on lockscreen and wakes screen briefly
        try {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        } catch (_: Exception) {}
        setContentView(R.layout.activity_quick_voice)

        statusText = findViewById(R.id.quickVoiceStatus)
        parser = ExpenseVoiceParser()
        repository = ExpenseRepository(SimpleExpenseStorage(this))

        try { Toast.makeText(this, "Kolki: overlay abierto", Toast.LENGTH_SHORT).show() } catch (_: Exception) {}

        ensureNotificationChannel()

        // Ask permission if needed
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 201)
        } else {
            // Small delay to avoid audio focus/race on cold start from background
            window.decorView.postDelayed({ startListening() }, 180)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 201) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                window.decorView.postDelayed({ startListening() }, 180)
            } else {
                Toast.makeText(this, "Permiso de micrófono denegado", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startListening() {
        statusText.text = getString(R.string.quick_listening)
        retriedOnce = false
        // Programar tiempo máximo de escucha configurable
        val prefs = getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        val maxListenMs = prefs.getInt("voice_max_listen_ms", 12000)
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(maxListenMs.toLong())
            // Si seguimos aquí, no hubo resultado a tiempo
            suppressError = true
            try { speechRecognizer?.stopListening() } catch (_: Exception) {}
            // Si tenemos un parcial, úsalo como resultado en lugar de abortar
            val fallback = lastPartialText?.takeIf { it.isNotBlank() }
            if (fallback != null) {
                isHandlingResult = true
                handleResult(fallback)
            } else {
                Toast.makeText(applicationContext, "Tiempo de escucha agotado", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        // Iniciar reconocimiento directo
        try {
            speechRecognizer = SimpleSpeechRecognizer(this)
        } catch (e: Exception) {
            Toast.makeText(this, "Error micrófono: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        speechRecognizer?.startListening(object : SimpleSpeechRecognizer.SpeechCallback {
            override fun onResult(text: String) {
                timeoutJob?.cancel()
                isHandlingResult = true
                handleResult(text.ifBlank { lastPartialText ?: text })
            }

            override fun onError(error: String) {
                if (suppressError || isHandlingResult) return
                val hadPartial = !lastPartialText.isNullOrBlank()
                if (!hadPartial && !retriedOnce) {
                    retriedOnce = true
                    timeoutJob?.cancel()
                    statusText.text = getString(R.string.quick_listening)
                    // retry once
                    speechRecognizer?.cleanup()
                    speechRecognizer = SimpleSpeechRecognizer(this@QuickVoiceActivity)
                    speechRecognizer?.startListening(this)
                    return
                }
                timeoutJob?.cancel()
                Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()
                finish()
            }

            override fun onPartialResult(partialText: String) {
                statusText.text = partialText
                lastPartialText = partialText
            }
        })
    }

    private fun handleResult(text: String) {
        val expense = parser.parseExpenseFromVoice(text)

        val prefs = getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        // Asegurar por defecto auto-guardar=true si no existe
        if (!prefs.contains("voice_auto_save")) {
            prefs.edit().putBoolean("voice_auto_save", true).apply()
        }
        // Permitir guardar crudo si el parser falla (por defecto: true)
        if (!prefs.contains("voice_fallback_save_raw")) {
            prefs.edit().putBoolean("voice_fallback_save_raw", true).apply()
        }
        val auto = prefs.getBoolean("voice_auto_save", true)
        val delayMs = prefs.getInt("voice_auto_save_delay_ms", 800)
        val fallbackSaveRaw = prefs.getBoolean("voice_fallback_save_raw", true)

        scope.launch {
            delay(delayMs.toLong())
            if (auto) {
                try {
                    if (expense != null) {
                        val spokenOriginal = parser.extractOriginalCategory(text)
                        val toSave = SimpleExpense(
                            category = expense.category,
                            originalCategory = spokenOriginal?.takeIf { it.isNotBlank() },
                            amount = expense.amount,
                            comment = expense.comment,
                            date = Date()
                        )
                        repository.insertExpense(toSave)
                        Toast.makeText(applicationContext, "Gasto guardado", Toast.LENGTH_SHORT).show()
                        postNotification("Gasto guardado: ${toSave.category} - S/ ${toSave.amount}")
                    } else if (fallbackSaveRaw) {
                        // Guardado de respaldo cuando el parser falla: monto 0 y comentario con el texto
                        val spokenOriginal = parser.extractOriginalCategory(text)
                        val toSave = SimpleExpense(
                            category = spokenOriginal?.takeIf { it.isNotBlank() } ?: "Voz",
                            originalCategory = spokenOriginal?.takeIf { it.isNotBlank() },
                            amount = 0.0,
                            comment = text,
                            date = Date()
                        )
                        repository.insertExpense(toSave)
                        Toast.makeText(applicationContext, "Guardado como nota de voz", Toast.LENGTH_SHORT).show()
                        postNotification("Guardado (nota de voz): ${toSave.comment?.take(32) ?: ""}")
                    } else {
                        Toast.makeText(applicationContext, "No se pudo interpretar. Activa 'Guardar crudo' en Ajustes.", Toast.LENGTH_LONG).show()
                        postNotification("Reconocido: $text")
                    }
                } catch (e: Exception) {
                    Toast.makeText(applicationContext, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                postNotification("Reconocido: $text")
            }
            finish()
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(notifChannelId, "Kolki Voz", NotificationManager.IMPORTANCE_LOW)
            mgr.createNotificationChannel(ch)
        }
    }

    private fun postNotification(text: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, notifChannelId)
            .setSmallIcon(android.R.drawable.ic_input_add)
            .setContentTitle("Kolki")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        mgr.notify(notifIdSaved, notif)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { speechRecognizer?.cleanup() } catch (_: Exception) {}
        timeoutJob?.cancel()
        scope.cancel()
    }

    companion object {
        fun launchFromService(context: Context) {
            val intent = Intent(context, QuickVoiceActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }
}
