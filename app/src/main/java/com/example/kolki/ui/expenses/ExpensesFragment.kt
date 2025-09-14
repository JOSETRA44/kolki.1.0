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
    private var lastCount: Int = 0
    private var filterCategory: Boolean = true
    private var filterComment: Boolean = true
    private var filterDate: Boolean = true

    companion object {
        private var adviceShownThisSession = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpensesBinding.inflate(inflater, container, false)
        return binding.root
    }

    // ===== Advice helpers =====
    private fun buildAdviceMessage(): String {
        if (allExpenses.isEmpty()) return ""
        val today = java.util.Calendar.getInstance()
        val yesterday = (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_MONTH, -1) }
        fun sameDay(a: java.util.Date, cal: java.util.Calendar): Boolean {
            val c = java.util.Calendar.getInstance().apply { time = a }
            return c.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
                    c.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR)
        }
        val todaySum = allExpenses.filter { sameDay(it.date, today) }.sumOf { it.amount }
        val yestSum = allExpenses.filter { sameDay(it.date, yesterday) }.sumOf { it.amount }

        // Record day logic (max single-day spend this month)
        val monthCal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.DAY_OF_MONTH, 1) }
        val endMonthCal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.DAY_OF_MONTH, getActualMaximum(java.util.Calendar.DAY_OF_MONTH)) }
        fun inMonth(d: java.util.Date): Boolean {
            val c = java.util.Calendar.getInstance().apply { time = d }
            return c.get(java.util.Calendar.MONTH) == monthCal.get(java.util.Calendar.MONTH) &&
                    c.get(java.util.Calendar.YEAR) == monthCal.get(java.util.Calendar.YEAR)
        }
        val monthExpenses = allExpenses.filter { inMonth(it.date) }
        val byDay = monthExpenses.groupBy { val c = java.util.Calendar.getInstance().apply { time = it.date }; c.get(java.util.Calendar.DAY_OF_YEAR) }
        val maxDayTotal = byDay.maxOfOrNull { it.value.sumOf { e -> e.amount } } ?: 0.0
        val todayIsRecord = todaySum > 0 && todaySum >= maxDayTotal

        // Top category this week
        val weekStart = (today.clone() as java.util.Calendar).apply {
            firstDayOfWeek = java.util.Calendar.MONDAY
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        }
        fun inCurrentWeek(d: java.util.Date): Boolean {
            val c = java.util.Calendar.getInstance().apply { time = d }
            return !c.before(weekStart) && !c.after(today)
        }
        val weekExpenses = allExpenses.filter { inCurrentWeek(it.date) }
        val topCat = weekExpenses.groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }.maxByOrNull { it.value }?.key

        // Projection to month end: very rough
        val dayOfMonth = today.get(java.util.Calendar.DAY_OF_MONTH)
        val daysInMonth = endMonthCal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val spentMonth = monthExpenses.sumOf { it.amount }
        val avgPerDay = if (dayOfMonth > 0) spentMonth / dayOfMonth else 0.0
        val projected = avgPerDay * daysInMonth

        val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
        val symbol = prefs.getString("currency_symbol", "S/") ?: "S/"
        fun money(v: Double) = "$symbol ${String.format(java.util.Locale.getDefault(), "%.2f", v)}"

        val parts = mutableListOf<String>()
        if (todaySum > yestSum && yestSum > 0) parts += "Gastaste más que ayer (${money(todaySum)} vs ${money(yestSum)})."
        if (todayIsRecord) parts += "Hoy es tu récord de gasto del mes (${money(todaySum)})."
        if (!topCat.isNullOrBlank()) parts += "Esta semana gastaste más en ${topCat}."
        // If projected > spentMonth by a large margin, warn
        if (projected > spentMonth * 1.4 && projected > 0) parts += "A este paso podrías no llegar a fin de mes (proyección: ${money(projected)})."

        return parts.joinToString(" ")
    }

    private fun quickAdviceForLast(): String {
        val last = allExpenses.maxByOrNull { it.date.time } ?: return ""
        val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
        val symbol = prefs.getString("currency_symbol", "S/") ?: "S/"
        return "Nuevo gasto agregado: ${last.category} por $symbol ${String.format(java.util.Locale.getDefault(), "%.2f", last.amount)}."
    }

    private fun showAdvice(text: String) {
        if (!isAdded) return
        try {
            binding.adviceText.text = text
            binding.adviceCard.visibility = View.VISIBLE
            // Auto-hide based on text length (approx): base 3s + 40ms per char, capped 8s
            val duration = (3000 + text.length * 40).coerceAtMost(8000)
            binding.adviceCard.removeCallbacks(hideAdviceRunnable)
            binding.adviceCard.postDelayed(hideAdviceRunnable, duration.toLong())
        } catch (_: Exception) {}
    }

    private val hideAdviceRunnable = Runnable {
        try { binding.adviceCard.visibility = View.GONE } catch (_: Exception) {}
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

        // Show advice once per app open
        if (!adviceShownThisSession) {
            adviceShownThisSession = true
            val msg = buildAdviceMessage()
            if (msg.isNotBlank()) showAdvice(msg)
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

        // Bell/notifications button: show contextual advice on demand
        binding.headerNotificationsButton.setOnClickListener {
            val msg = buildAdviceMessage()
            if (msg.isBlank()) {
                android.widget.Toast.makeText(requireContext(), "Sin avisos por ahora", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                showAdvice(msg)
            }
        }
    }
    
    private fun observeViewModel() {
        viewModel.expenses.observe(viewLifecycleOwner) { expenses ->
            allExpenses = expenses
            applySearchAndFilters(binding.searchInput.text?.toString().orEmpty())
            // Detect newly added expense and show a quick advice
            if (allExpenses.size > lastCount) {
                val msg = quickAdviceForLast()
                if (msg.isNotBlank()) showAdvice(msg)
            }
            lastCount = allExpenses.size
            
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
