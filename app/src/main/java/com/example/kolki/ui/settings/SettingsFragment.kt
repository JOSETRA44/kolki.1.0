package com.example.kolki.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.kolki.data.SimpleExpenseStorage
import com.example.kolki.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        loadVoiceAutoSaveSetting()
        loadVoiceAutoSaveDelay()
        loadVoiceMaxListen()
        loadGlobalVoiceSettings()
        setupClickListeners()
        // Hide duplicate Export in Settings (lives in Profile)
        try { binding.exportDataLayout.visibility = View.GONE } catch (_: Exception) {}
        loadCurrency()
        loadExportFormat()
    }
    
    private fun setupClickListeners() {
        // Voice Commands Configuration
        binding.voiceCommandsCard.setOnClickListener {
            // TODO: Implementar configuración de comandos de voz
        }
        
        // App Settings
        binding.appSettingsCard.setOnClickListener {
            // TODO: Implementar configuración general
        }
        
        // About
        binding.aboutCard.setOnClickListener {
            // TODO: Mostrar información de la app
        }

        // Export data
        binding.exportDataLayout.setOnClickListener {
            startExport()
        }

        // Currency selector
        binding.currencySelectorLayout.setOnClickListener {
            showCurrencyDialog()
        }

        // Export format selector
        binding.exportFormatLayout.setOnClickListener {
            showExportFormatDialog()
        }

        // Voice Auto-Save toggle
        binding.voiceAutoSaveSwitch.setOnCheckedChangeListener { _, isChecked ->
            val prefs = requireContext().getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("voice_auto_save", isChecked).apply()
            setVoiceAutoSaveDelayEnabled(isChecked)
        }

        // Auto-save delay picker
        binding.voiceAutoSaveDelayLayout.setOnClickListener {
            showAutoSaveDelayDialog()
        }

        // Max listen timeout picker
        binding.voiceMaxListenLayout.setOnClickListener {
            showMaxListenDialog()
        }

        // Global accessibility enable
        binding.voiceGlobalEnableSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            // Ignore programmatic changes; only react to user presses
            if (!buttonView.isPressed) return@setOnCheckedChangeListener
            val prefs = requireContext().getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("global_access_enabled", isChecked).apply()
            setGlobalVoiceControlsEnabled(isChecked)
            if (isChecked && !isKolkiServiceEnabled()) {
                // Prompt user to enable in system settings only if not enabled
                try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) {}
            }
        }

        // Global press count picker
        binding.voiceGlobalPressCountLayout.setOnClickListener {
            showGlobalPressCountDialog()
        }

        // Global window picker
        binding.voiceGlobalWindowLayout.setOnClickListener {
            showGlobalWindowDialog()
        }
    }

    private fun loadVoiceAutoSaveSetting() {
        val prefs = requireContext().getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        // Establecer por defecto auto-guardar=true si no existe la preferencia
        if (!prefs.contains("voice_auto_save")) {
            prefs.edit().putBoolean("voice_auto_save", true).apply()
        }
        val autoSave = prefs.getBoolean("voice_auto_save", true)
        binding.voiceAutoSaveSwitch.isChecked = autoSave
        setVoiceAutoSaveDelayEnabled(autoSave)
    }

    private fun loadVoiceAutoSaveDelay() {
        val prefs = requireContext().getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        val delayMs = prefs.getInt("voice_auto_save_delay_ms", 800)
        binding.voiceAutoSaveDelayValue.text = formatDelay(delayMs)
    }

    private fun loadVoiceMaxListen() {
        val prefs = requireContext().getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        val timeoutMs = prefs.getInt("voice_max_listen_ms", 8000)
        binding.voiceMaxListenValue.text = formatDelay(timeoutMs)
    }

    private fun showAutoSaveDelayDialog() {
        val options = arrayOf("0.5 s", "0.8 s", "1.2 s")
        val values = intArrayOf(500, 800, 1200)
        val prefs = requireContext().getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        val current = prefs.getInt("voice_auto_save_delay_ms", 800)
        val currentIndex = values.indexOf(current).coerceAtLeast(1)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Espera de auto-guardado")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val chosen = values[which]
                prefs.edit().putInt("voice_auto_save_delay_ms", chosen).apply()
                binding.voiceAutoSaveDelayValue.text = formatDelay(chosen)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showMaxListenDialog() {
        val options = arrayOf("5.0 s", "8.0 s", "12.0 s")
        val values = intArrayOf(5000, 8000, 12000)
        val prefs = requireContext().getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        val current = prefs.getInt("voice_max_listen_ms", 8000)
        val currentIndex = values.indexOf(current).let { if (it >= 0) it else 1 }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Tiempo máx. de escucha")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val chosen = values[which]
                prefs.edit().putInt("voice_max_listen_ms", chosen).apply()
                binding.voiceMaxListenValue.text = formatDelay(chosen)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ===== Export format selector =====
    private fun loadExportFormat() {
        val prefs = requireContext().getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        val format = prefs.getString("export_format", "CSV")
        binding.exportFormatValue.text = format
    }

    private fun showExportFormatDialog() {
        val options = arrayOf("CSV", "JSON")
        val prefs = requireContext().getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        val current = prefs.getString("export_format", "CSV")
        val idx = options.indexOf(current).let { if (it >= 0) it else 0 }
        AlertDialog.Builder(requireContext())
            .setTitle("Formato de exportación")
            .setSingleChoiceItems(options, idx) { dialog, which ->
                val chosen = options[which]
                prefs.edit().putString("export_format", chosen).apply()
                binding.exportFormatValue.text = chosen
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun formatDelay(ms: Int): String {
        return when (ms) {
            500 -> "0.5 s"
            800 -> "0.8 s"
            1200 -> "1.2 s"
            else -> String.format("%.1f s", ms / 1000f)
        }
    }

    private fun setVoiceAutoSaveDelayEnabled(enabled: Boolean) {
        binding.voiceAutoSaveDelayLayout.isEnabled = enabled
        binding.voiceAutoSaveDelayLayout.isClickable = enabled
        val alpha = if (enabled) 1f else 0.5f
        binding.voiceAutoSaveDelayLayout.alpha = alpha
        binding.voiceAutoSaveDelayValue.alpha = alpha
    }

    // ===== Global Voice settings =====
    private fun loadGlobalVoiceSettings() {
        val prefs = requireContext().getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("global_access_enabled", false)
        val pressCount = prefs.getInt("global_press_count", 3)
        val windowMs = prefs.getInt("global_press_window_ms", 500)

        binding.voiceGlobalEnableSwitch.isChecked = enabled
        binding.voiceGlobalPressCountValue.text = if (pressCount >= 3) "Triple" else "Doble"
        binding.voiceGlobalWindowValue.text = formatWindow(windowMs)
        setGlobalVoiceControlsEnabled(enabled)
    }

    private fun setGlobalVoiceControlsEnabled(enabled: Boolean) {
        val alpha = if (enabled) 1f else 0.5f
        binding.voiceGlobalPressCountLayout.isEnabled = enabled
        binding.voiceGlobalPressCountLayout.isClickable = enabled
        binding.voiceGlobalPressCountLayout.alpha = alpha
        binding.voiceGlobalWindowLayout.isEnabled = enabled
        binding.voiceGlobalWindowLayout.isClickable = enabled
        binding.voiceGlobalWindowLayout.alpha = alpha
        binding.voiceGlobalPressCountValue.alpha = alpha
        binding.voiceGlobalWindowValue.alpha = alpha
    }

    private fun showGlobalPressCountDialog() {
        val options = arrayOf("Doble", "Triple")
        val values = intArrayOf(2, 3)
        val prefs = requireContext().getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        val current = prefs.getInt("global_press_count", 3)
        val idx = values.indexOf(current).coerceAtLeast(1)
        AlertDialog.Builder(requireContext())
            .setTitle("Pulsaciones")
            .setSingleChoiceItems(options, idx) { dialog, which ->
                val chosen = values[which]
                prefs.edit().putInt("global_press_count", chosen).apply()
                binding.voiceGlobalPressCountValue.text = options[which]
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showGlobalWindowDialog() {
        val options = arrayOf("300 ms", "500 ms", "800 ms")
        val values = intArrayOf(300, 500, 800)
        val prefs = requireContext().getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        val current = prefs.getInt("global_press_window_ms", 500)
        val idx = values.indexOf(current).let { if (it >= 0) it else 1 }
        AlertDialog.Builder(requireContext())
            .setTitle("Ventana de detección")
            .setSingleChoiceItems(options, idx) { dialog, which ->
                val chosen = values[which]
                prefs.edit().putInt("global_press_window_ms", chosen).apply()
                binding.voiceGlobalWindowValue.text = options[which]
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun formatWindow(ms: Int): String = when (ms) {
        300 -> "300 ms"
        500 -> "500 ms"
        800 -> "800 ms"
        else -> "$ms ms"
    }

    private fun isKolkiServiceEnabled(): Boolean {
        return try {
            val enabledServices = android.provider.Settings.Secure.getString(
                requireContext().contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val expected = "${requireContext().packageName}/com.example.kolki.service.GlobalKeyAccessibilityService"
            enabledServices.split(":").any { it.equals(expected, ignoreCase = true) }
        } catch (_: Exception) {
            false
        }
    }

    // ===== Export data =====
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            exportToUri(uri)
        }
    }

    private fun startExport() {
        val filename = "kolki_expenses_${System.currentTimeMillis()}.json"
        createDocumentLauncher.launch(filename)
    }

    private fun exportToUri(uri: Uri) {
        try {
            val storage = SimpleExpenseStorage(requireContext())
            val json = storage.exportJson()
            requireContext().contentResolver.openOutputStream(uri)?.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            android.widget.Toast.makeText(requireContext(), "Exportación completada", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(requireContext(), "Error al exportar: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // ===== Currency selector =====
    private fun loadCurrency() {
        val prefs = requireContext().getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        val symbol = prefs.getString("currency_symbol", "S/")
        binding.currencyValue.text = symbol
    }

    private fun showCurrencyDialog() {
        val options = arrayOf("S/", "$", "€")
        val prefs = requireContext().getSharedPreferences("kolki_prefs", Context.MODE_PRIVATE)
        val current = prefs.getString("currency_symbol", "S/")
        val idx = options.indexOf(current).let { if (it >= 0) it else 0 }
        AlertDialog.Builder(requireContext())
            .setTitle("Moneda")
            .setSingleChoiceItems(options, idx) { dialog, which ->
                val chosen = options[which]
                prefs.edit().putString("currency_symbol", chosen).apply()
                binding.currencyValue.text = chosen
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
