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
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
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
    private var visibleDayCount: Int = 3 // show Today + Yesterday + anteayer

    companion object {
        private var adviceShownThisSession = false
    }

    private fun showEditExpenseDialog(expense: com.example.kolki.data.SimpleExpense) {
        val ctx = requireContext()
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        // Categoría
        val catTil = com.google.android.material.textfield.TextInputLayout(ctx).apply { hint = "Categoría" }
        val catEt = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            setText(expense.category)
        }
        catTil.addView(catEt)
        container.addView(catTil)

        // Monto
        val amtTil = com.google.android.material.textfield.TextInputLayout(ctx).apply { hint = "Monto" }
        val amtEt = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(java.util.Locale.getDefault(), "%.2f", expense.amount))
        }
        amtTil.addView(amtEt)
        container.addView(amtTil)

        // Comentario
        val comTil = com.google.android.material.textfield.TextInputLayout(ctx).apply { hint = "Comentario (opcional)" }
        val comEt = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            setText(expense.comment ?: "")
        }
        comTil.addView(comEt)
        container.addView(comTil)

        AlertDialog.Builder(ctx)
            .setTitle("Editar gasto")
            .setView(container)
            .setPositiveButton("Guardar") { dialog, _ ->
                val newCat = catEt.text?.toString()?.trim().orEmpty()
                val newAmtText = amtEt.text?.toString()?.replace(',', '.')?.trim().orEmpty()
                val newAmt = newAmtText.toDoubleOrNull() ?: -1.0
                val newCom = comEt.text?.toString()?.trim().orEmpty()

                if (newCat.isBlank()) {
                    android.widget.Toast.makeText(ctx, "Ingrese la categoría", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newAmt <= 0) {
                    android.widget.Toast.makeText(ctx, "Ingrese un monto válido", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val updated = expense.copy(
                    category = newCat,
                    amount = newAmt,
                    comment = newCom.ifBlank { null }
                )
                viewModel.updateExpense(updated)
                android.widget.Toast.makeText(ctx, "Gasto actualizado", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
        val today = java.util.Calendar.getInstance()
        val yesterday = (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_MONTH, -1) }
        fun sameDay(a: java.util.Date, cal: java.util.Calendar): Boolean {
            val c = java.util.Calendar.getInstance().apply { time = a }
            return c.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
                    c.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR)
        }
        val hasExpenses = allExpenses.isNotEmpty()
        val todaySum = if (hasExpenses) allExpenses.filter { sameDay(it.date, today) }.sumOf { it.amount } else 0.0
        val yestSum = if (hasExpenses) allExpenses.filter { sameDay(it.date, yesterday) }.sumOf { it.amount } else 0.0

        // Record day logic (max single-day spend this month)
        val monthCal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.DAY_OF_MONTH, 1) }
        val endMonthCal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.DAY_OF_MONTH, getActualMaximum(java.util.Calendar.DAY_OF_MONTH)) }
        fun inMonth(d: java.util.Date): Boolean {
            val c = java.util.Calendar.getInstance().apply { time = d }
            return c.get(java.util.Calendar.MONTH) == monthCal.get(java.util.Calendar.MONTH) &&
                    c.get(java.util.Calendar.YEAR) == monthCal.get(java.util.Calendar.YEAR)
        }
        val monthExpenses = if (hasExpenses) allExpenses.filter { inMonth(it.date) } else emptyList()
        val byDay = if (hasExpenses) monthExpenses.groupBy { val c = java.util.Calendar.getInstance().apply { time = it.date }; c.get(java.util.Calendar.DAY_OF_YEAR) } else emptyMap()
        val maxDayTotal = byDay.maxOfOrNull { it.value.sumOf { e -> e.amount } } ?: 0.0
        val todayIsRecord = hasExpenses && todaySum > 0 && todaySum >= maxDayTotal

        // Top category this week
        val weekStart = (today.clone() as java.util.Calendar).apply {
            firstDayOfWeek = java.util.Calendar.MONDAY
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        }
        fun inCurrentWeek(d: java.util.Date): Boolean {
            val c = java.util.Calendar.getInstance().apply { time = d }
            return !c.before(weekStart) && !c.after(today)
        }
        val weekExpenses = if (hasExpenses) allExpenses.filter { inCurrentWeek(it.date) } else emptyList()
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

        // Contextual tips (prefs already loaded)
        val budgetAmount = prefs.getFloat("budget_amount", 0f).toDouble()
        val globalEnabled = prefs.getBoolean("global_access_enabled", false)
        val usedDeep = prefs.getBoolean("used_deep_analysis", false)
        // Strict hierarchical selection: pick first matching
        // 1) Missing critical setup
        if (budgetAmount <= 0.0) return "Establece tu presupuesto mensual tocando la billetera en Gastos."
        if (!globalEnabled) return "Activa la detección global en Ajustes > Accesibilidad para registrar por voz desde cualquier pantalla."

        // 2) Risk/warning if data exists
        if (hasExpenses && projected > spentMonth * 1.4 && projected > 0) {
            return "A este paso podrías no llegar a fin de mes (proyección: ${money(projected)})."
        }

        // 3) Highlights/comparisons
        if (hasExpenses && todayIsRecord) return "Hoy es tu récord de gasto del mes (${money(todaySum)})."
        if (hasExpenses && todaySum > yestSum && yestSum > 0) return "Gastaste más que ayer (${money(todaySum)} vs ${money(yestSum)})."
        if (hasExpenses && !topCat.isNullOrBlank()) return "Esta semana gastaste más en ${topCat}."

        // 4) Promote deep analysis if never used
        if (!usedDeep) return "Prueba Análisis Profundo con IA en Estadísticas (obtén insights personalizados)."

        // 5) Fallbacks with rotation (persist index to avoid repeating the same)
        val fallbacks = mutableListOf<String>().apply {
            // If budget exists, show remaining this month
            val remainingMonth = (budgetAmount - spentMonth).coerceAtLeast(0.0)
            add("Te quedan ${money(remainingMonth)} para este mes.")
            add("La mejor manera de crecer es ahorrando un poco cada día.")
            add("Pequeños gastos suman: registra todo para controlarlo.")
            add("Revisa Estadísticas para ver en qué categorías gastas más.")
            add("Activa avisos de presupuesto en Ajustes si quieres alertas diarias.")
        }
        val rotateKey = "advice_rotate_idx"
        val rotateDayKey = "advice_rotate_ymd"
        val now = java.util.Calendar.getInstance()
        val ymd = now.get(java.util.Calendar.YEAR) * 10000 + (now.get(java.util.Calendar.MONTH) + 1) * 100 + now.get(java.util.Calendar.DAY_OF_MONTH)
        val currentDay = prefs.getInt(rotateDayKey, 0)
        val currentIdx = prefs.getInt(rotateKey, 0)
        val newIdx = if (currentDay == ymd) (currentIdx + 1) % fallbacks.size else 0
        prefs.edit().putInt(rotateKey, newIdx).putInt(rotateDayKey, ymd).apply()
        return fallbacks[newIdx]
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

        // Greeting with user name in header (if configured)
        try {
            val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
            val name = prefs.getString("user_name", null)?.takeIf { it.isNotBlank() }
            val tv = binding.headerTitle
            if (name != null) {
                tv.visibility = View.VISIBLE
                tv.text = "Hola, $name"
                tv.textSize = 16f
                tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
            } else {
                // keep it subtle or hidden when no name
                tv.visibility = View.GONE
            }
        } catch (_: Exception) {}

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

        // Auto-load more when scrolled to bottom
        binding.contentScroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
            val child = v.getChildAt(0)
            if (child != null && scrollY + v.height >= child.height - 8) {
                maybeLoadMore()
            }
        })
    }
    
    private fun setupRecyclerView() {
        expensesAdapter = ExpensesAdapter(
            onItemClick = { _ ->
                // Futuro: detalle del gasto
            },
            onEdit = { expense ->
                showEditExpenseDialog(expense)
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

        binding.loadMoreButton.setOnClickListener {
            showLoadingFooter()
            visibleDayCount += 2
            applySearchAndFilters(binding.searchInput.text?.toString().orEmpty())
            hideLoadingFooterSoon()
        }
    }
    
    private fun observeViewModel() {
        viewModel.expenses.observe(viewLifecycleOwner) { expenses ->
            allExpenses = expenses
            applySearchAndFilters(binding.searchInput.text?.toString().orEmpty())
            // Detect newly added expense and show a quick advice
            if (allExpenses.size > lastCount) {
                try {
                    val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
                    val lastSeenTs = prefs.getLong("last_seen_expense_ts", 0L)
                    val latest = allExpenses.maxByOrNull { it.createdAt } ?: allExpenses.maxByOrNull { it.date.time }
                    val latestTs = (latest?.createdAt ?: latest?.date?.time ?: 0L)
                    val recentWindowMs = 2 * 60 * 1000L
                    val now = System.currentTimeMillis()
                    if (latestTs > lastSeenTs && (now - latestTs) <= recentWindowMs) {
                        val msg = quickAdviceForLast()
                        if (msg.isNotBlank()) showAdvice(msg)
                        prefs.edit().putLong("last_seen_expense_ts", latestTs).apply()
                    }
                } catch (_: Exception) {}
            }
            lastCount = allExpenses.size
            
            // Show/hide empty state
            if (allExpenses.isEmpty()) {
                binding.emptyStateLayout.visibility = View.VISIBLE
                binding.expensesRecyclerView.visibility = View.GONE
                binding.loadMoreButton.visibility = View.GONE
            } else {
                binding.emptyStateLayout.visibility = View.GONE
                binding.expensesRecyclerView.visibility = View.VISIBLE
                updateLoadMoreVisibility()
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
            val green = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.kolki.R.color.income_green)
            val red = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.kolki.R.color.expense_red)
            binding.remainingText.setTextColor(if ((value ?: 0.0) >= 0) green else red)
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
                    // Celebrate with money rain from wallet button
                    playMoneyRainFromWallet()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun playMoneyRainFromWallet() {
        if (!isAdded) return
        val wallet = try { binding.headerBudgetButton } catch (_: Exception) { null } ?: return
        val root = activity?.findViewById<android.view.ViewGroup>(android.R.id.content) ?: return

        val walletLoc = IntArray(2)
        wallet.getLocationOnScreen(walletLoc)
        val startX = walletLoc[0] + wallet.width / 2f
        val startY = walletLoc[1] + wallet.height / 2f

        val screenH = root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val screenW = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels

        fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

        // Subtle burst at wallet
        runCatching {
            val burst = android.widget.ImageView(requireContext()).apply {
                setImageResource(android.R.drawable.star_big_on)
                alpha = 0f
                layoutParams = android.view.ViewGroup.LayoutParams(dp(24), dp(24))
                x = startX - dp(12)
                y = startY - dp(12)
            }
            root.addView(burst)
            val a1 = android.animation.ObjectAnimator.ofFloat(burst, android.view.View.ALPHA, 0f, 1f, 0f).apply { duration = 450 }
            val a2 = android.animation.ObjectAnimator.ofFloat(burst, android.view.View.SCALE_X, 0.2f, 1.4f, 1f).apply { duration = 450 }
            val a3 = android.animation.ObjectAnimator.ofFloat(burst, android.view.View.SCALE_Y, 0.2f, 1.4f, 1f).apply { duration = 450 }
            android.animation.AnimatorSet().apply {
                playTogether(a1, a2, a3)
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { root.removeView(burst) }
                })
                start()
            }
        }

        val icons = intArrayOf(
            com.example.kolki.R.drawable.ic_coin,
            com.example.kolki.R.drawable.ic_bill
        )

        val count = 24
        repeat(count) { i ->
            root.postDelayed({
                try {
                    val iv = android.widget.ImageView(requireContext()).apply {
                        val which = kotlin.random.Random.nextInt(icons.size)
                        setImageResource(icons[which])
                        val size = dp(16 + kotlin.random.Random.nextInt(16))
                        layoutParams = android.view.ViewGroup.LayoutParams(size, size)
                        alpha = 0f
                    }
                    root.addView(iv)

                    // Start near wallet with small random offset
                    iv.x = (startX - iv.layoutParams.width / 2f) + kotlin.random.Random.nextInt(-dp(8), dp(8))
                    iv.y = (startY - iv.layoutParams.height / 2f) + kotlin.random.Random.nextInt(-dp(8), dp(8))

                    val endY = screenH + dp(40)
                    val driftX = kotlin.random.Random.nextInt(-screenW / 3, screenW / 3)
                    val rot = if (kotlin.random.Random.nextBoolean()) 360f else -360f
                    val duration = (1000L..1700L).random()

                    val fadeIn = android.animation.ObjectAnimator.ofFloat(iv, android.view.View.ALPHA, 0f, 1f).apply { this.duration = 120 }
                    val fallY = android.animation.ObjectAnimator.ofFloat(iv, android.view.View.TRANSLATION_Y, 0f, (endY - iv.y)).apply {
                        this.duration = duration
                        interpolator = android.view.animation.AccelerateInterpolator(1.6f)
                    }
                    val swayX = android.animation.ObjectAnimator.ofFloat(iv, android.view.View.TRANSLATION_X, 0f, driftX.toFloat()).apply {
                        this.duration = duration
                        interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    }
                    val rotate = android.animation.ObjectAnimator.ofFloat(iv, android.view.View.ROTATION, 0f, rot).apply { this.duration = duration }
                    val fadeOut = android.animation.ObjectAnimator.ofFloat(iv, android.view.View.ALPHA, 1f, 0f).apply { this.duration = 260; startDelay = (duration - 260).coerceAtLeast(0) }

                    android.animation.AnimatorSet().apply {
                        playTogether(fadeIn, fallY, swayX, rotate, fadeOut)
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) { root.removeView(iv) }
                        })
                        start()
                    }
                } catch (_: Exception) {}
            }, (i * 35L))
        }
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
        // If no query -> show limited window by recent days
        if (query.isEmpty()) {
            val base = limitByRecentDays(allExpenses, visibleDayCount)
            expensesAdapter.submitList(base)
            updateLoadMoreVisibility()
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

        // Search across ALL data; limit results to 15 for performance
        val filtered = allExpenses
            .map { it to score(it) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<com.example.kolki.data.SimpleExpense, Int>> { it.second }
                .thenByDescending { it.first.date.time })
            .map { it.first }
            .take(15)

        expensesAdapter.submitList(filtered)
        // When searching, hide 'Ver más' because search spans all data already
        binding.loadMoreButton.visibility = View.GONE
    }

    private fun limitByRecentDays(items: List<com.example.kolki.data.SimpleExpense>, days: Int): List<com.example.kolki.data.SimpleExpense> {
        if (items.isEmpty() || days <= 0) return emptyList()
        // Build unique day keys (YYYY*1000 + DOY) sorted desc
        fun dayKey(d: java.util.Date): Int {
            val c = java.util.Calendar.getInstance().apply { time = d }
            return c.get(java.util.Calendar.YEAR) * 1000 + c.get(java.util.Calendar.DAY_OF_YEAR)
        }
        val sortedKeys = items.map { dayKey(it.date) }.distinct().sortedDescending()
        val keep = sortedKeys.take(days).toSet()
        return items.filter { keep.contains(dayKey(it.date)) }
    }

    private fun updateLoadMoreVisibility() {
        // Show button if there are more days beyond the current window
        fun dayKey(d: java.util.Date): Int {
            val c = java.util.Calendar.getInstance().apply { time = d }
            return c.get(java.util.Calendar.YEAR) * 1000 + c.get(java.util.Calendar.DAY_OF_YEAR)
        }
        val totalDays = allExpenses.map { dayKey(it.date) }.distinct().size
        binding.loadMoreButton.visibility = if (totalDays > visibleDayCount) View.VISIBLE else View.GONE
    }

    private fun maybeLoadMore() {
        // If button is visible, we can load more
        if (binding.loadMoreButton.visibility == View.VISIBLE) {
            showLoadingFooter()
            visibleDayCount += 2
            applySearchAndFilters(binding.searchInput.text?.toString().orEmpty())
            hideLoadingFooterSoon()
        }
    }

    private fun showLoadingFooter() {
        try { binding.loadMoreFooter.visibility = View.VISIBLE } catch (_: Exception) {}
    }

    private fun hideLoadingFooterSoon() {
        // Small delay to ensure user perceives the load
        binding.loadMoreFooter.postDelayed({
            try { binding.loadMoreFooter.visibility = View.GONE } catch (_: Exception) {}
        }, 450)
    }
    
    // Add expense functionality moved to AddExpenseFragment
    
    // Voice recognition functionality moved to AddExpenseFragment
    
    // Permission handling moved to AddExpenseFragment
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
