package com.example.kolki.ui.profile

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.kolki.data.SimpleExpenseStorage
import com.example.kolki.util.BudgetLog
import com.example.kolki.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private enum class ExportType { EXPENSES, INCOMES }
    private var pendingExportType: ExportType = ExportType.EXPENSES

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun showBudgetDialog() {
        val ctx = requireContext()
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 8)
        }
        val amountTil = com.google.android.material.textfield.TextInputLayout(ctx).apply {
            hint = "Seleccione su presupuesto para este mes"
        }
        val amountEt = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        amountTil.addView(amountEt)
        container.addView(amountTil)

        // Mode options
        val modes = arrayOf("Hoy", "Fin de mes", "Personalizado")
        val radio = android.widget.RadioGroup(ctx).apply {
            orientation = android.widget.RadioGroup.VERTICAL
        }
        modes.forEachIndexed { idx, label ->
            radio.addView(android.widget.RadioButton(ctx).apply {
                text = label
                id = 1000 + idx
                isChecked = idx == 1 // default: Fin de mes
            })
        }
        container.addView(radio)

        // Date pickers for custom
        val customContainer = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(0, 16, 0, 0)
        }
        val startBtn = com.google.android.material.button.MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = "Inicio" }
        val endBtn = com.google.android.material.button.MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = "Fin" }
        val startLabel = android.widget.TextView(ctx).apply { text = "--/--/----"; setPadding(16,0,16,0) }
        val endLabel = android.widget.TextView(ctx).apply { text = "--/--/----"; setPadding(16,0,0,0) }
        customContainer.addView(startBtn)
        customContainer.addView(startLabel)
        customContainer.addView(endBtn)
        customContainer.addView(endLabel)
        container.addView(customContainer)

        radio.setOnCheckedChangeListener { _, checkedId ->
            customContainer.visibility = if (checkedId == 1002) View.VISIBLE else View.GONE
        }

        val cal = java.util.Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        var startMillis: Long? = null
        var endMillis: Long? = null

        startBtn.setOnClickListener {
            android.app.DatePickerDialog(ctx, { _, y, m, d ->
                val c = java.util.Calendar.getInstance(); c.set(y, m, d, 0, 0, 0); c.set(java.util.Calendar.MILLISECOND, 0)
                startMillis = c.timeInMillis
                startLabel.text = dateFormat.format(c.time)
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        endBtn.setOnClickListener {
            android.app.DatePickerDialog(ctx, { _, y, m, d ->
                val c = java.util.Calendar.getInstance(); c.set(y, m, d, 23, 59, 59); c.set(java.util.Calendar.MILLISECOND, 999)
                endMillis = c.timeInMillis
                endLabel.text = dateFormat.format(c.time)
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("Establecer presupuesto mensual")
            .setView(container)
            .setPositiveButton("Guardar") { dialog, _ ->
                val amount = amountEt.text?.toString()?.toDoubleOrNull() ?: 0.0
                if (amount <= 0.0) {
                    android.widget.Toast.makeText(ctx, "Ingrese un monto válido", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val mode = when (radio.checkedRadioButtonId) {
                    1000 -> "today"
                    1001 -> "eom"
                    1002 -> "custom"
                    else -> "eom"
                }
                if (mode == "custom" && (startMillis == null || endMillis == null || endMillis!! < startMillis!!)) {
                    android.widget.Toast.makeText(ctx, "Seleccione un rango válido", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val prefs = ctx.getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit()
                    .putFloat("budget_amount", amount.toFloat())
                    .putString("budget_mode", mode)
                    .apply()
                if (mode == "custom") {
                    prefs.edit()
                        .putLong("budget_start", startMillis!!)
                        .putLong("budget_end", endMillis!!)
                        .apply()
                }
                // Log presupuesto definido
                val fmt = java.text.NumberFormat.getNumberInstance(java.util.Locale.getDefault())
                fmt.maximumFractionDigits = 0
                val amountStr = fmt.format(kotlin.math.floor(amount))
                val modeLabel = when (mode) { "today" -> "hoy"; "eom" -> "fin de mes"; else -> "personalizado" }
                BudgetLog.addEvent(ctx, "budget_set", "Se definió tu presupuesto a S/ $amountStr ($modeLabel)")
                android.widget.Toast.makeText(ctx, "Presupuesto guardado", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showBudgetNotices() {
        val ctx = requireContext()
        val events = BudgetLog.getEvents(ctx)
        if (events.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle("Avisos")
                .setMessage("No hay avisos todavía")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val dateFmt = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        val msg = buildString {
            events.forEach { e ->
                append("• ")
                append(dateFmt.format(java.util.Date(e.timeMs)))
                append(" — ")
                append(e.message)
                append('\n')
            }
        }
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("Avisos de presupuestos")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Export data button
        binding.exportDataLayout.setOnClickListener {
            pendingExportType = ExportType.EXPENSES
            startExport()
        }

        // Show totals: incomes and expenses
        val storage = SimpleExpenseStorage(requireContext())
        val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
        val symbol = prefs.getString("currency_symbol", "S/") ?: "S/"
        val incomeTotal = storage.getIncomeTotal()
        val expenseTotal = storage.getTotalAmount()
        binding.incomeTotalText.text = "$symbol ${String.format(java.util.Locale.getDefault(), "%.2f", incomeTotal)}"
        binding.expenseTotalText.text = "$symbol ${String.format(java.util.Locale.getDefault(), "%.2f", expenseTotal)}"

        // Tap totals to export (separate datasets)
        binding.incomeTotalText.setOnClickListener {
            pendingExportType = ExportType.INCOMES
            startExport()
        }
        binding.expenseTotalText.setOnClickListener {
            pendingExportType = ExportType.EXPENSES
            startExport()
        }

        // Set monthly budget
        binding.setMonthlyBudgetLayout.setOnClickListener {
            showBudgetDialog()
        }

        // Avisos: historial de presupuestos
        try {
            binding.avisosLayout.setOnClickListener { showBudgetNotices() }
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ==== Export via SAF ====
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri != null) exportToUri(uri)
    }

    private fun startExport() {
        val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
        val fmt = prefs.getString("export_format", "CSV") ?: "CSV"
        val ext = if (fmt == "CSV") ".csv" else ".json"
        val base = if (pendingExportType == ExportType.INCOMES) "kolki_incomes" else "kolki_expenses"
        val filename = "${base}_${System.currentTimeMillis()}$ext"
        createDocumentLauncher.launch(filename)
    }

    private fun exportToUri(uri: Uri) {
        try {
            val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
            val fmt = prefs.getString("export_format", "CSV") ?: "CSV"
            val storage = SimpleExpenseStorage(requireContext())
            val data = when (pendingExportType) {
                ExportType.EXPENSES -> if (fmt == "CSV") storage.exportExpensesCsv() else storage.exportExpensesJson()
                ExportType.INCOMES -> if (fmt == "CSV") storage.exportIncomesCsv() else storage.exportIncomesJson()
            }
            val resolver = requireContext().contentResolver
            val out = try { resolver.openOutputStream(uri, "w") } catch (_: Throwable) { resolver.openOutputStream(uri) }
            if (out == null) {
                android.widget.Toast.makeText(requireContext(), "No se pudo abrir el archivo para escribir", android.widget.Toast.LENGTH_LONG).show()
                return
            }
            out.use { os ->
                os.write(data.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            android.widget.Toast.makeText(requireContext(), "Exportación completada", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(requireContext(), "Error al exportar: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
