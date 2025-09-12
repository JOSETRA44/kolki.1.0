package com.example.kolki.ui.profile

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.kolki.data.SimpleExpenseStorage
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
