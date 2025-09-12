package com.example.kolki.ui.expenses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.navigation.fragment.findNavController
import androidx.core.widget.doOnTextChanged
import androidx.appcompat.app.AlertDialog
import com.example.kolki.databinding.FragmentExpensesBinding
import com.example.kolki.data.SimpleIncome

class ExpensesFragment : Fragment() {

    private var _binding: FragmentExpensesBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: ExpensesViewModel
    private lateinit var expensesAdapter: ExpensesAdapter
    private var allExpenses: List<com.example.kolki.data.SimpleExpense> = emptyList()
    private var filterCategory: Boolean = true
    private var filterComment: Boolean = true
    private var filterDate: Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpensesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[ExpensesViewModel::class.java]
        
        setupRecyclerView()
        setupCategoryDropdown()
        setupClickListeners()
        observeViewModel()

        // Pull-to-refresh (root is SwipeRefreshLayout)
        val srl = (binding.root as androidx.swiperefreshlayout.widget.SwipeRefreshLayout)
        // Colors and background
        srl.setColorSchemeResources(com.google.android.material.R.color.design_default_color_primary)
        srl.setProgressBackgroundColorSchemeColor(android.graphics.Color.TRANSPARENT)
        // Try to position the spinner below the app bar (approx. 80dp)
        val offsetPx = (80 * resources.displayMetrics.density).toInt()
        srl.setProgressViewOffset(true, 0, offsetPx)

        srl.setOnRefreshListener {
            viewModel.reload()
            // Safety timeout to stop spinner after 2 seconds in case no new emission arrives
            srl.postDelayed({
                if (srl.isRefreshing) srl.isRefreshing = false
            }, 2000)
        }

        // Load filter toggles from preferences
        loadSearchFilterPrefs()

        // Real-time search
        binding.searchInput.doOnTextChanged { text, _, _, _ ->
            applySearchAndFilters(text?.toString().orEmpty())
        }

        // Wallet (budget) add income
        binding.headerBudgetButton.setOnClickListener {
            showAddIncomeDialog()
        }
    }
    
    private fun setupRecyclerView() {
        expensesAdapter = ExpensesAdapter(
            onItemClick = { _ ->
                // TODO: open detail/edit screen
            },
            onEdit = { expense ->
                // TODO: open edit UI (prefill AddExpenseFragment with data)
                android.widget.Toast.makeText(requireContext(), "Editar próximamente", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDelete = { expense ->
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Eliminar gasto")
                    .setMessage("¿Desea eliminar este gasto de ${expense.category} por ${expense.amount}?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        viewModel.deleteExpense(expense)
                        android.widget.Toast.makeText(requireContext(), "Gasto eliminado", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        )
        
        binding.expensesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = expensesAdapter
        }
    }
    
    private fun setupCategoryDropdown() {
        // No category dropdown needed in expenses list view
    }
    
    private fun setupClickListeners() {
        binding.headerStatsButton.setOnClickListener {
            try {
                findNavController().navigate(com.example.kolki.R.id.navigation_statistics)
            } catch (_: Exception) {}
        }

        binding.headerProfileButton.setOnClickListener {
            try {
                findNavController().navigate(com.example.kolki.R.id.navigation_profile)
            } catch (_: Exception) {}
        }

        // Ensure filter button uses accent tint on pre-M versions
        try {
            binding.filterButton.setTextColor(android.graphics.Color.WHITE)
        } catch (_: Exception) {}

        binding.filterButton.setOnClickListener {
            showFilterOptionsDialog()
        }
    }
    
    private fun observeViewModel() {
        viewModel.expenses.observe(viewLifecycleOwner) { expenses ->
            allExpenses = expenses
            applySearchAndFilters(binding.searchInput.text?.toString().orEmpty())
            
            // Show/hide empty state
            if (allExpenses.isEmpty()) {
                binding.emptyStateLayout.visibility = View.VISIBLE
                binding.expensesRecyclerView.visibility = View.GONE
            } else {
                binding.emptyStateLayout.visibility = View.GONE
                binding.expensesRecyclerView.visibility = View.VISIBLE
            }

            // Stop refreshing spinner if active
            (binding.root as androidx.swiperefreshlayout.widget.SwipeRefreshLayout).isRefreshing = false
        }
        // Remaining budget
        viewModel.remaining.observe(viewLifecycleOwner) { value ->
            val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
            val symbol = prefs.getString("currency_symbol", "S/") ?: "S/"
            val txt = "$symbol ${String.format(java.util.Locale.getDefault(), "%.2f", value ?: 0.0)}"
            binding.remainingText.text = txt
            binding.remainingText.setTextColor(if ((value ?: 0.0) >= 0) android.graphics.Color.parseColor("#2E7D32") else android.graphics.Color.RED)
        }

        // Categories not needed in expenses list view
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

        AlertDialog.Builder(requireContext())
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

    private fun loadSearchFilterPrefs() {
        val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
        filterCategory = prefs.getBoolean("search_filter_category", true)
        filterComment = prefs.getBoolean("search_filter_comment", true)
        filterDate = prefs.getBoolean("search_filter_date", true)
    }

    private fun saveSearchFilterPrefs() {
        val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("search_filter_category", filterCategory)
            .putBoolean("search_filter_comment", filterComment)
            .putBoolean("search_filter_date", filterDate)
            .apply()
    }

    private fun showFilterOptionsDialog() {
        val options = arrayOf("Categoría", "Comentario", "Fecha")
        val checked = booleanArrayOf(filterCategory, filterComment, filterDate)
        AlertDialog.Builder(requireContext())
            .setTitle("Filtrar por")
            .setMultiChoiceItems(options, checked) { _, which, isChecked ->
                when (which) {
                    0 -> filterCategory = isChecked
                    1 -> filterComment = isChecked
                    2 -> filterDate = isChecked
                }
            }
            .setPositiveButton("Aplicar") { dialog, _ ->
                saveSearchFilterPrefs()
                applySearchAndFilters(binding.searchInput.text?.toString().orEmpty())
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun applySearchAndFilters(queryRaw: String) {
        val query = queryRaw.trim()
        if (query.isEmpty()) {
            expensesAdapter.submitList(allExpenses)
            return
        }
        val qLower = query.lowercase()
        val dateFmt = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
        val symbol = prefs.getString("currency_symbol", "S/") ?: "S/"

        fun score(exp: com.example.kolki.data.SimpleExpense): Int {
            var s = 0
            // Category has highest weight if enabled
            if (filterCategory && exp.category.lowercase().contains(qLower)) s += 4
            // Amount: always searchable
            val amountStr = String.format(java.util.Locale.getDefault(), "%.2f", exp.amount)
            val amountWithSymbol = "$symbol $amountStr".lowercase()
            if (amountStr.contains(qLower) || amountWithSymbol.contains(qLower)) s += 3
            // Comment
            if (filterComment) {
                val c = exp.comment?.lowercase().orEmpty()
                if (c.contains(qLower)) s += 2
            }
            // Date (dd/MM/yyyy)
            if (filterDate) {
                val d = dateFmt.format(exp.date).lowercase()
                if (d.contains(qLower)) s += 1
            }
            return s
        }

        val filtered = allExpenses
            .map { it to score(it) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<com.example.kolki.data.SimpleExpense, Int>> { it.second }
                .thenByDescending { it.first.date.time })
            .map { it.first }

        expensesAdapter.submitList(filtered)
    }
    
    // Add expense functionality moved to AddExpenseFragment
    
    // Voice recognition functionality moved to AddExpenseFragment
    
    // Permission handling moved to AddExpenseFragment
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
