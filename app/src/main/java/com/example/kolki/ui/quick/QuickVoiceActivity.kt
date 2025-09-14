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
import kotlinx.coroutines.*
import java.util.*

class QuickVoiceActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private var speechRecognizer: SimpleSpeechRecognizer? = null
    private lateinit var parser: ExpenseVoiceParser
    private lateinit var repository: ExpenseRepository
    private val scope = CoroutineScope(Dispatchers.Main)
    private var timeoutJob: Job? = null
    private var lastPartialText: String? = null
    private var isHandlingResult: Boolean = false
    private var suppressError: Boolean = false

    private val notifChannelId = "kolki_voice_channel"
    private val notifIdSaved = 3001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_voice)

        statusText = findViewById(R.id.quickVoiceStatus)
        parser = ExpenseVoiceParser()
        repository = ExpenseRepository(SimpleExpenseStorage(this))

        ensureNotificationChannel()

        // Ask permission if needed
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 201)
        } else {
            startListening()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 201) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                Toast.makeText(this, "Permiso de micrófono denegado", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startListening() {
        statusText.text = getString(R.string.quick_listening)
        try {
            speechRecognizer = SimpleSpeechRecognizer(this)
        } catch (e: Exception) {
            Toast.makeText(this, "Error micrófono: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Programar tiempo máximo de escucha configurable
        val prefs = getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        val maxListenMs = prefs.getInt("voice_max_listen_ms", 8000)
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

        speechRecognizer?.startListening(object : SimpleSpeechRecognizer.SpeechCallback {
            override fun onResult(text: String) {
                timeoutJob?.cancel()
                isHandlingResult = true
                handleResult(text)
            }

            override fun onError(error: String) {
                if (suppressError || isHandlingResult) return
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
        if (expense == null) {
            Toast.makeText(this, "No se reconoció ningún texto", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val prefs = getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        // Asegurar por defecto auto-guardar=true si no existe
        if (!prefs.contains("voice_auto_save")) {
            prefs.edit().putBoolean("voice_auto_save", true).apply()
        }
        val auto = prefs.getBoolean("voice_auto_save", true)
        val delayMs = prefs.getInt("voice_auto_save_delay_ms", 800)

        scope.launch {
            delay(delayMs.toLong())
            if (auto) {
                val toSave = SimpleExpense(
                    category = expense.category,
                    amount = expense.amount,
                    comment = expense.comment,
                    date = Date()
                )
                try {
                    repository.insertExpense(toSave)
                    Toast.makeText(applicationContext, "Gasto guardado", Toast.LENGTH_SHORT).show()
                    postNotification("Gasto guardado: ${toSave.category} - S/ ${toSave.amount}")
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
        try { speechRecognizer?.stopListening() } catch (_: Exception) {}
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
