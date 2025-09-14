package com.example.kolki.ui.add

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import com.google.android.material.snackbar.Snackbar
import com.example.kolki.R
import com.example.kolki.data.SimpleExpense
import com.example.kolki.databinding.FragmentAddExpenseBinding
import com.example.kolki.speech.ExpenseVoiceParser
import com.example.kolki.speech.SimpleSpeechRecognizer
import com.example.kolki.ui.expenses.ExpensesViewModel
import com.example.kolki.data.SimpleIncome
import java.text.SimpleDateFormat
import java.util.*

class AddExpenseFragment : Fragment() {

    private var _binding: FragmentAddExpenseBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: ExpensesViewModel
    private var speechRecognizer: SimpleSpeechRecognizer? = null
    private lateinit var voiceParser: ExpenseVoiceParser
    private var lastAutoSavedExpense: SimpleExpense? = null
    private var autoSaveJob: Job? = null
    private var autoSavedThisSession: Boolean = false
    
    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity())[ExpensesViewModel::class.java]
        voiceParser = ExpenseVoiceParser()
        
        setupCategoryDropdown()
        setupClickListeners()
    }
    
    private fun setupCategoryDropdown() {
        // Lista reducida y canónica de categorías
        val categories = arrayOf(
            "Alimentación", "Transporte", "Entretenimiento", "Salud",
            "Compras", "Servicios", "Educación", "Vivienda", "Hogar", "Otros"
        )
        
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        binding.categoryInput.setAdapter(adapter)

        // Auto-start voice if requested via nav args or pending flag from global activation
        val shouldStart = arguments?.getBoolean("startVoice", false) == true ||
                requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
                    .getBoolean("pending_voice_add", false)
        if (shouldStart) {
            // Clear pending flag to avoid loops
            requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("pending_voice_add", false).apply()
            // Start listening
            binding.voiceButton.performClick()
        }
    }
    
    private fun setupClickListeners() {
        binding.saveButton.setOnClickListener {
            addExpense()
        }
        
        binding.voiceButton.setOnClickListener {
            if (checkAudioPermission()) {
                startVoiceRecognition()
            } else {
                requestAudioPermission()
            }
        }
        
        binding.stopVoiceButton.setOnClickListener {
            stopVoiceRecognition()
        }

        binding.addIncomeButton.setOnClickListener {
            showAddIncomeDialog()
        }
    }
    
    private fun addExpense() {
        val rawCategory = binding.categoryInput.text.toString().trim()
        val amountText = binding.amountInput.text.toString().trim()
        val comment = binding.commentInput.text.toString().trim()
        
        if (rawCategory.isEmpty()) {
            binding.categoryInputLayout.error = "Ingrese la categoría"
            return
        }
        
        if (amountText.isEmpty()) {
            binding.amountInputLayout.error = "Ingrese el monto"
            return
        }

        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            binding.amountInputLayout.error = "Monto inválido"
            return
        }

        // Normalizar categoría usando sinónimos y reducir al set canónico
        val normalized = voiceParser.normalizeCategory(rawCategory)
        val canonicalSet = setOf(
            "Alimentación", "Transporte", "Entretenimiento", "Salud",
            "Compras", "Servicios", "Educación", "Vivienda", "Hogar", "Otros"
        )
        val finalCategory = if (normalized in canonicalSet) normalized else "Otros"

        val expense = SimpleExpense(
            category = finalCategory,
            originalCategory = rawCategory.takeIf { it.isNotBlank() },
            amount = amount,
            comment = comment.takeIf { it.isNotBlank() },
            date = Date()
        )

        viewModel.insertExpense(expense)
        clearForm()
        Toast.makeText(context, "Gasto agregado: ${expense.category} - S/ ${expense.amount}", Toast.LENGTH_SHORT).show()
    }
    
    private fun clearForm() {
        binding.categoryInput.text?.clear()
        binding.amountInput.text?.clear()
        binding.commentInput.text?.clear()
        binding.categoryInputLayout.error = null
        binding.amountInputLayout.error = null
    }

    private fun showAddIncomeDialog() {
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val emitterInput = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
            hint = "Emisor"
        }.also { til ->
            val edit = com.google.android.material.textfield.TextInputEditText(til.context)
            til.addView(edit)
            container.addView(til)
        }
        val amountInput = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
            val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
            val symbol = prefs.getString("currency_symbol", "S/") ?: "S/"
            hint = "Ingreso ($symbol)"
        }.also { til ->
            val edit = com.google.android.material.textfield.TextInputEditText(til.context)
            edit.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            til.addView(edit)
            container.addView(til)
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Agregar ingreso")
            .setView(container)
            .setPositiveButton("Guardar") { dialog, _ ->
                val emitter = (emitterInput.editText?.text?.toString() ?: "").trim()
                val amountText = (amountInput.editText?.text?.toString() ?: "").trim()
                val amount = amountText.toDoubleOrNull() ?: 0.0
                if (emitter.isEmpty() || amount <= 0) {
                    Toast.makeText(requireContext(), "Complete emisor y monto válido", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.insertIncome(SimpleIncome(emitter = emitter, amount = amount))
                    Toast.makeText(requireContext(), "Ingreso agregado", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO_PERMISSION
        )
    }
    
    private fun startVoiceRecognition() {
        binding.voiceStatusCard.visibility = View.VISIBLE
        binding.voiceStatusText.text = "Escuchando..."
        binding.voiceResultText.text = "Diga: categoría, monto, comentario"
        autoSavedThisSession = false
        
        // Initialize recognizer lazily and safely
        if (speechRecognizer == null) {
            try {
                speechRecognizer = SimpleSpeechRecognizer(requireContext())
            } catch (e: Exception) {
                binding.voiceStatusText.text = "Error inicializando micrófono"
                Toast.makeText(context, "Error de voz: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.voiceStatusCard.visibility = View.GONE
                return
            }
        }

        speechRecognizer?.startListening(object : SimpleSpeechRecognizer.SpeechCallback {
            override fun onResult(text: String) {
                // Evitar crashes si el fragment ya no está adjunto
                if (!isAdded || _binding == null) return
                // Mostrar el texto final reconocido para depuración
                context?.let { Toast.makeText(it, "Reconocido: ${'$'}text", Toast.LENGTH_SHORT).show() }
                handleVoiceResult(text)
            }
            
            override fun onError(error: String) {
                if (!isAdded || _binding == null) return
                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    binding.voiceStatusText.text = "Error: $error"
                    stopVoiceRecognition()
                }
            }
            
            override fun onPartialResult(partialText: String) {
                if (!isAdded || _binding == null) return
                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    binding.voiceResultText.text = partialText
                    // Intentar llenar campos en vivo con el texto parcial (sin guardar)
                    val expense = voiceParser.parseExpenseFromVoice(partialText)
                    if (expense != null && expense.amount > 0) {
                        fillFormFromExpense(expense)
                        // Si autoguardado está activo, programar guardado con debounce
                        val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
                        val autoSave = prefs.getBoolean("voice_auto_save", false)
                        if (autoSave && !autoSavedThisSession) {
                            autoSaveJob?.cancel()
                            autoSaveJob = viewLifecycleOwner.lifecycleScope.launch {
                                val prefsDelay = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
                                val delayMs = prefsDelay.getInt("voice_auto_save_delay_ms", 800)
                                delay(delayMs.toLong())
                                if (!isAdded || _binding == null) return@launch
                                val cat = binding.categoryInput.text?.toString()?.trim().orEmpty()
                                val amt = binding.amountInput.text?.toString()?.toDoubleOrNull() ?: 0.0
                                if (cat.isNotBlank() && amt > 0) {
                                    val toSave = SimpleExpense(
                                        category = cat,
                                        amount = amt,
                                        comment = binding.commentInput.text?.toString()?.takeIf { it.isNotBlank() },
                                        date = java.util.Date()
                                    )
                                    viewModel.insertExpense(toSave)
                                    lastAutoSavedExpense = toSave
                                    autoSavedThisSession = true
                                    Snackbar.make(binding.root, "Gasto guardado por voz", Snackbar.LENGTH_LONG)
                                        .setAction("Deshacer") {
                                            lastAutoSavedExpense?.let { exp ->
                                                viewModel.deleteExpense(exp)
                                                Toast.makeText(requireContext(), "Se deshizo el guardado", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .show()
                                    stopVoiceRecognition()
                                    clearForm()
                                }
                            }
                        }
                    }
                }
            }
        })
    }
    
    private fun handleVoiceResult(text: String) {
        if (!isAdded || _binding == null) return
        activity?.runOnUiThread {
            if (!isAdded || _binding == null) return@runOnUiThread
            var expense = voiceParser.parseExpenseFromVoice(text)
            if (expense == null && text.isNotBlank()) {
                // Fallback: build a minimal expense from free text
                val amt = extractAmount(text)
                expense = SimpleExpense(
                    category = "Voz",
                    amount = amt,
                    comment = text,
                    date = java.util.Date()
                )
            }
            
            if (expense != null) {
                binding.voiceStatusText.text = "¡Gasto reconocido!"
                binding.voiceResultText.text = "${'$'}{expense.category}: S/ ${'$'}{expense.amount}"
                
                // Fill form with recognized data
                fillFormFromExpense(expense)
                
                // Decide whether to auto-save or just fill fields based on user setting
                val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
                val autoSave = prefs.getBoolean("voice_auto_save", false)

                if (autoSave) {
                    // Si ya hay autoguardado por parcial, no duplicar
                    if (!autoSavedThisSession) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            val prefsDelay = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
                            val delayMs = prefsDelay.getInt("voice_auto_save_delay_ms", 800)
                            delay(delayMs.toLong())
                            if (!isAdded || _binding == null) return@launch
                            val cat = binding.categoryInput.text?.toString()?.trim().orEmpty()
                            val amt = binding.amountInput.text?.toString()?.toDoubleOrNull() ?: 0.0
                            if (cat.isNotBlank() && amt > 0) {
                                val toSave = SimpleExpense(
                                    category = cat,
                                    amount = amt,
                                    comment = binding.commentInput.text?.toString()?.takeIf { it.isNotBlank() },
                                    date = java.util.Date()
                                )
                                viewModel.insertExpense(toSave)
                                lastAutoSavedExpense = toSave
                                autoSavedThisSession = true
                                Snackbar.make(binding.root, "Gasto guardado por voz", Snackbar.LENGTH_LONG)
                                    .setAction("Deshacer") {
                                        lastAutoSavedExpense?.let { exp ->
                                            viewModel.deleteExpense(exp)
                                            Toast.makeText(requireContext(), "Se deshizo el guardado", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .show()
                                stopVoiceRecognition()
                                clearForm()
                            }
                        }
                    }
                } else {
                    Toast.makeText(context, "Datos reconocidos. Verifique y toque Guardar.", Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.voiceStatusText.text = "No se pudo reconocer"
                binding.voiceResultText.text = "Intente de nuevo"
            }
        }
    }

    private fun extractAmount(text: String): Double {
        // Find first number like 12, 12.5, 12,50 possibly prefixed by currency
        val cleaned = text.lowercase().replace("s/", " ").replace("$", " ").replace("soles", " ")
        val regex = Regex("([0-9]+([.,][0-9]{1,2})?)")
        val match = regex.find(cleaned)
        return match?.value?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
    }

    private fun fillFormFromExpense(expense: SimpleExpense) {
        // For ExposedDropdown (AutoCompleteTextView) use setText(value, false) to avoid filtering
        binding.categoryInput.setText(expense.category, false)
        binding.categoryInputLayout.error = null

        binding.amountInput.setText(expense.amount.toString())
        binding.amountInputLayout.error = null

        if (!expense.comment.isNullOrBlank()) {
            binding.commentInput.setText(expense.comment)
        }
    }
    
    private fun stopVoiceRecognition() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
            // ignore
        }
        binding.voiceStatusCard.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            speechRecognizer?.cleanup()
            speechRecognizer = null
        } catch (_: Exception) {}
        _binding = null
    }
}
