package com.example.kolki.ui.statistics

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kolki.databinding.FragmentStatisticsBinding
import com.example.kolki.ui.expenses.ExpensesAdapter
import com.example.kolki.data.SimpleExpense
import com.example.kolki.data.RoomStorageAdapter
import com.example.kolki.data.ExpenseStoragePort
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.NumberFormat
import com.example.kolki.speech.ExpenseVoiceParser
import java.text.SimpleDateFormat
import androidx.navigation.fragment.findNavController
import com.example.kolki.util.BudgetLog
import java.util.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: StatisticsViewModel
    private lateinit var categoriesAdapter: CategoryStatsAdapter
    private lateinit var recentExpensesAdapter: ExpensesAdapter
    
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("es", "PE"))
    private lateinit var storage: ExpenseStoragePort
    private val parser by lazy { ExpenseVoiceParser() }
    private val canonicalSet = setOf(
        "Alimentación", "Transporte", "Entretenimiento", "Salud",
        "Compras", "Servicios", "Educación", "Vivienda", "Hogar", "Otros"
    )
    private var pieStart: Date? = null
    private var pieEnd: Date? = null
    private var currentWeekMonday: Calendar = mondayOfWeek(Calendar.getInstance())
    private var lastPieTotals: Map<String, Double> = emptyMap()
    private var lastPieTotalSum: Double = 0.0
    private var lastPieItemsByCat: Map<String, List<SimpleExpense>> = emptyMap()
    private var lastWeekDayCategoryMap: List<Map<String, List<SimpleExpense>>> = emptyList()
    private var showBudgetToday: Boolean = true
    private var showMonthOnSummary: Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // Refresh summary and budget bars in case data or settings changed
        updateSummaryCard()
        updateBudgetHint()
    }

    private fun setupVisualizationToggle() {
        // Default: show Circular (pie)
        binding.vizPieButton.isChecked = true
        updateVisualizationCards(Mode.PIE)

        binding.vizToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                binding.vizPieButton.id -> updateVisualizationCards(Mode.PIE)
                binding.vizBarButton.id -> updateVisualizationCards(Mode.BAR)
                binding.vizListButton.id -> updateVisualizationCards(Mode.LIST)
            }
        }
    }

    private enum class Mode { PIE, BAR, LIST }

    private fun updateVisualizationCards(mode: Mode) {
        val pieCard = view?.findViewById<View>(com.example.kolki.R.id.pieCard)
        val barCard = view?.findViewById<View>(com.example.kolki.R.id.barCard)
        val listCard = view?.findViewById<View>(com.example.kolki.R.id.categoriesCard)
        when (mode) {
            Mode.PIE -> {
                pieCard?.visibility = View.VISIBLE
                barCard?.visibility = View.GONE
                listCard?.visibility = View.GONE
            }
            Mode.BAR -> {
                pieCard?.visibility = View.GONE
                barCard?.visibility = View.VISIBLE
                listCard?.visibility = View.GONE
            }
            Mode.LIST -> {
                pieCard?.visibility = View.GONE
                barCard?.visibility = View.GONE
                listCard?.visibility = View.VISIBLE
            }
        }
    }

    private fun updateSummaryCard() {
        val titleTv = view?.findViewById<android.widget.TextView>(com.example.kolki.R.id.summaryTitleText) ?: return
        val valueTv = view?.findViewById<android.widget.TextView>(com.example.kolki.R.id.summaryValueText) ?: return
        val progContainer = view?.findViewById<android.view.View>(com.example.kolki.R.id.summaryProgressContainer)
        val progRed = view?.findViewById<android.view.View>(com.example.kolki.R.id.summaryProgressRed)
        val progGreen = view?.findViewById<android.view.View>(com.example.kolki.R.id.summaryProgressGreen)

        val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
        val symbol = prefs.getString("currency_symbol", "S/") ?: "S/"

        if (showMonthOnSummary) {
            // Este Mes (gasto): suma de gastos del mes actual
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            val start = atStartOfDay(cal.time)
            cal.set(java.util.Calendar.DAY_OF_MONTH, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
            val end = atEndOfDay(cal.time)
            val monthSpent = storage.getSnapshot().filter { !it.date.before(start) && !it.date.after(end) }.sumOf { it.amount }
            val amount = kotlin.math.floor(monthSpent).toLong() // enteros para mayor impacto visual
            titleTv.text = "Este Mes (gasto)"
            valueTv.text = "$symbol $amount"
            valueTv.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.kolki.R.color.expense_red))

            // Progress: For month view, relate to Budget (monthly/custom). Red = monthSpent, Green = remaining of budget
            val budgetAmount = prefs.getFloat("budget_amount", 0f).toDouble()
            val total = budgetAmount.coerceAtLeast(0.0)
            if (total > 0) {
                progContainer?.visibility = android.view.View.VISIBLE
                val redP = (monthSpent / total).coerceIn(0.0, 1.0).toFloat()
                val greenP = (1f - redP).coerceIn(0f, 1f)
                if (progRed != null) {
                    (progRed.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let { lp ->
                        lp.weight = if (redP <= 0f) 0f else redP
                        progRed.layoutParams = lp
                    }
                }
                if (progGreen != null) {
                    (progGreen.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let { lp ->
                        lp.weight = if (greenP <= 0f) 0f else greenP
                        progGreen.layoutParams = lp
                    }
                }
                progContainer?.requestLayout()
            } else {
                progContainer?.visibility = android.view.View.GONE
            }
        } else {
            // Saldo Restante (global): ingresos - gastos
            val remaining = storage.getRemaining()
            val amount = kotlin.math.floor(remaining).toLong()
            titleTv.text = "Saldo Restante"
            valueTv.text = "$symbol $amount"
            valueTv.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.kolki.R.color.income_green))

            // Progress: red = total gastado global, green = remaining
            val totalSpent = storage.getSnapshot().sumOf { it.amount }.coerceAtLeast(0.0)
            val greenVal = remaining.coerceAtLeast(0.0)
            val total = totalSpent + greenVal
            if (total > 0) {
                progContainer?.visibility = android.view.View.VISIBLE
                val redP = (totalSpent / total).coerceIn(0.0, 1.0).toFloat()
                val greenP = (1f - redP).coerceIn(0f, 1f)
                (progRed?.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let {
                    it.weight = if (redP <= 0f) 0f else redP
                    progRed.layoutParams = it
                }
                (progGreen?.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let {
                    it.weight = if (greenP <= 0f) 0f else greenP
                    progGreen.layoutParams = it
                }
                progContainer?.requestLayout()
            } else {
                progContainer?.visibility = android.view.View.GONE
            }
        }
    }

    private fun triggerBudgetAlert(baselinePerDay: Long, todaySpent: Double) {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
        val symbol = prefs.getString("currency_symbol", "S/") ?: "S/"
        val over = kotlin.math.floor(todaySpent - baselinePerDay).toLong().coerceAtLeast(0L)

        // Play configured sound
        try {
            val soundStr = prefs.getString("budget_alert_sound_uri", null)
            val uri = if (soundStr.isNullOrBlank()) android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            else android.net.Uri.parse(soundStr)
            val rt = android.media.RingtoneManager.getRingtone(ctx, uri)
            rt?.play()
        } catch (_: Exception) {}

        // Post notification
        val nm = androidx.core.app.NotificationManagerCompat.from(ctx)
        val channelId = "budget_alerts"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val ch = android.app.NotificationChannel(channelId, "Alertas de presupuesto", android.app.NotificationManager.IMPORTANCE_HIGH)
            val mgr = ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            mgr.createNotificationChannel(ch)
        }
        val title = if (todaySpent > baselinePerDay * 2) "Ha sobrepasado el doble del presupuesto diario" else "Ha sobrepasado el presupuesto diario"
        val text = "Gastó $symbol ${String.format(java.util.Locale.getDefault(), "%.0f", todaySpent)} (diario: $symbol $baselinePerDay). El restante mensual se ajustará."
        val notif = androidx.core.app.NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(com.example.kolki.R.drawable.ic_expenses_24)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(2001, notif)

        // Log event with latest expense name if available
        try {
            val now = java.util.Calendar.getInstance()
            val start = atStartOfDay(now.time)
            val end = atEndOfDay(now.time)
            val todayItems = storage.getSnapshot().filter { !it.date.before(start) && !it.date.after(end) }
            val latest = todayItems.maxByOrNull { it.createdAt }
            val name = latest?.originalCategory?.takeIf { it.isNotBlank() } ?: latest?.category ?: "gasto"
            val amt = latest?.amount ?: over.toDouble()
            val msg = "Tu presupuesto diario fue excedido por '$name' (-$symbol ${String.format(java.util.Locale.getDefault(), "%.0f", amt)}). El restante mensual se ajustó."
            BudgetLog.addEvent(ctx, "overspend", msg)
        } catch (_: Exception) {}
    }

    private fun updateBudgetHint() {
        val budgetCard = view?.findViewById<android.view.View>(com.example.kolki.R.id.budgetCard) ?: return
        val titleTv = view?.findViewById<android.widget.TextView>(com.example.kolki.R.id.budgetTitleText) ?: return
        val hintTv = view?.findViewById<android.widget.TextView>(com.example.kolki.R.id.budgetHintText) ?: return
        val barDaily = view?.findViewById<android.view.View>(com.example.kolki.R.id.budgetDailyBar)
        val barDailyRed = view?.findViewById<android.view.View>(com.example.kolki.R.id.budgetDailyRed)
        val barDailyGreen = view?.findViewById<android.view.View>(com.example.kolki.R.id.budgetDailyGreen)
        val barMonthly = view?.findViewById<android.view.View>(com.example.kolki.R.id.budgetMonthlyBar)
        val barMonthlyRed = view?.findViewById<android.view.View>(com.example.kolki.R.id.budgetMonthlyRed)
        val barMonthlyGreen = view?.findViewById<android.view.View>(com.example.kolki.R.id.budgetMonthlyGreen)

        val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
        val symbol = prefs.getString("currency_symbol", "S/") ?: "S/"
        val amount = prefs.getFloat("budget_amount", 0f).toDouble()
        val mode = prefs.getString("budget_mode", "eom") ?: "eom"
        val now = java.util.Calendar.getInstance()

        if (amount <= 0.0) {
            titleTv.text = "Presupuesto"
            hintTv.text = "No tiene presupuesto suficiente"
            barDaily?.visibility = android.view.View.GONE
            barMonthly?.visibility = android.view.View.GONE
            return
        }

        val periodStartEnd = when (mode) {
            "today" -> {
                val s = atStartOfDay(now.time)
                val e = atEndOfDay(now.time)
                s to e
            }
            "custom" -> {
                val sMs = prefs.getLong("budget_start", 0L)
                val eMs = prefs.getLong("budget_end", 0L)
                (java.util.Date(if (sMs > 0) sMs else now.timeInMillis) to java.util.Date(if (eMs > 0) eMs else now.timeInMillis))
            }
            else -> { // end of month
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                val s = atStartOfDay(cal.time)
                cal.set(java.util.Calendar.DAY_OF_MONTH, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
                val e = atEndOfDay(cal.time)
                s to e
            }
        }

        // Days remaining for allocation (inclusive of today)
        val daysRemaining: Int = when (mode) {
            "today" -> 1
            "custom" -> {
                val todayStart = atStartOfDay(now.time)
                val end = periodStartEnd.second
                val diff = ((end.time - todayStart.time) / (24*60*60*1000)) + 1
                diff.coerceAtLeast(0L).toInt()
            }
            else -> { // eom
                val todayStart = atStartOfDay(now.time)
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.DAY_OF_MONTH, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
                val e = atEndOfDay(cal.time)
                val diff = ((e.time - todayStart.time) / (24*60*60*1000)) + 1
                diff.coerceAtLeast(0L).toInt()
            }
        }

        // Sum expenses within period
        val expenses = storage.getSnapshot().filter { !it.date.before(periodStartEnd.first) && !it.date.after(periodStartEnd.second) }
        val spent = expenses.sumOf { it.amount }
        // Split today's spend to evaluate alerts
        val todayStart = atStartOfDay(now.time)
        val todayEnd = atEndOfDay(now.time)
        val todaySpent = expenses.filter { !it.date.before(todayStart) && !it.date.after(todayEnd) }.sumOf { it.amount }
        val spentExcludingToday = (spent - todaySpent).coerceAtLeast(0.0)

        val remaining = (amount - spent).coerceAtLeast(0.0)

        // Baseline per-day BEFORE today's spend
        val baselinePerDay = if (daysRemaining > 0) kotlin.math.floor(((amount - spentExcludingToday).coerceAtLeast(0.0)) / daysRemaining).toLong() else 0L
        // Today's remaining allowance (puede ser negativo para mostrar exceso)
        val todaysRemainingRaw = baselinePerDay - kotlin.math.floor(todaySpent).toLong()
        // Monthly remaining shown as entero (puede bajar por gasto de hoy)
        val perMonth = kotlin.math.floor(remaining).toLong()

        // Evaluate alert based on today's baseline allowance before spending today
        if (mode != "today") {
            val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
            val alertsEnabled = prefs.getBoolean("budget_alerts_enabled", true)
            if (alertsEnabled && daysRemaining > 0) {
                val baselinePerDayAlert = kotlin.math.floor(((amount - spentExcludingToday).coerceAtLeast(0.0)) / daysRemaining).toLong()
                // Fire alert if exceeded and not already alerted today
                if (todaySpent > baselinePerDayAlert.toDouble()) {
                    val y = now.get(java.util.Calendar.YEAR)
                    val m = now.get(java.util.Calendar.MONTH) + 1
                    val d = now.get(java.util.Calendar.DAY_OF_MONTH)
                    val ymd = y * 10000 + m * 100 + d
                    val lastYmd = prefs.getInt("budget_last_alert_ymd", 0)
                    if (ymd != lastYmd) {
                        triggerBudgetAlert(baselinePerDayAlert, todaySpent)
                        prefs.edit().putInt("budget_last_alert_ymd", ymd).apply()
                    }
                }
            }
        }

        if (remaining <= 0.0) {
            titleTv.text = "Presupuesto"
            hintTv.text = "No tiene presupuesto suficiente"
            return
        }

        if (showBudgetToday) {
            titleTv.text = "Presupuesto (Hoy)"
            // Mostrar cantidad; si es negativo, parpadeo rojo
            val isNegative = todaysRemainingRaw < 0
            val displayValue = kotlin.math.abs(todaysRemainingRaw)
            hintTv.text = (if (isNegative) "-$symbol " else "$symbol ") + displayValue.toString()
            try {
                val red = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.kolki.R.color.expense_red)
                val green = androidx.core.content.ContextCompat.getColor(requireContext(), com.example.kolki.R.color.income_green)
                hintTv.setTextColor(if (isNegative) red else green)
                val card = view?.findViewById<android.view.View>(com.example.kolki.R.id.budgetCard)
                if (isNegative) {
                    // Blink animation on text
                    val blink = android.view.animation.AlphaAnimation(0.4f, 1f).apply {
                        duration = 400
                        repeatMode = android.view.animation.Animation.REVERSE
                        repeatCount = android.view.animation.Animation.INFINITE
                    }
                    hintTv.startAnimation(blink)
                    // Subtle tilt on card
                    card?.animate()?.rotation(2f)?.setDuration(150)?.withEndAction {
                        card.animate()?.rotation(0f)?.setDuration(150)?.start()
                    }?.start()
                } else {
                    hintTv.clearAnimation()
                    view?.findViewById<android.view.View>(com.example.kolki.R.id.budgetCard)?.animate()?.rotation(0f)?.setDuration(0)?.start()
                }
            } catch (_: Exception) {}

            // Show daily bar, hide monthly
            barDaily?.visibility = android.view.View.VISIBLE
            barMonthly?.visibility = android.view.View.GONE
            // Percent spent today vs baseline per-day (avoid div by zero)
            val denom = if (baselinePerDay > 0) baselinePerDay.toDouble() else 1.0
            val pct = (todaySpent / denom).coerceIn(0.0, 1.0).toFloat()
            val left = (1f - pct).coerceIn(0f, 1f)
            if (barDailyRed != null) {
                (barDailyRed.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let { lp ->
                    lp.weight = if (pct <= 0f) 0f else pct
                    barDailyRed.layoutParams = lp
                }
            }
            if (barDailyGreen != null) {
                (barDailyGreen.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let { lp ->
                    lp.weight = if (left <= 0f) 0f else left
                    barDailyGreen.layoutParams = lp
                }
            }
            barDaily?.requestLayout()
        } else {
            titleTv.text = "Presupuesto (Mes)"
            // Solo cantidad en rojo (sin texto largo)
            hintTv.text = "$symbol $perMonth"
            try {
                hintTv.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.kolki.R.color.expense_red))
                hintTv.clearAnimation()
            } catch (_: Exception) {}

            // Show monthly bar, hide daily. Red=spent/amount, Green=remaining/amount
            barDaily?.visibility = android.view.View.GONE
            barMonthly?.visibility = android.view.View.VISIBLE
            val denom = if (amount > 0.0) amount else 1.0
            val pct = (spent / denom).coerceIn(0.0, 1.0).toFloat()
            val left = (1f - pct).coerceIn(0f, 1f)
            if (barMonthlyRed != null) {
                (barMonthlyRed.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let { lp ->
                    lp.weight = if (pct <= 0f) 0f else pct
                    barMonthlyRed.layoutParams = lp
                }
            }
            if (barMonthlyGreen != null) {
                (barMonthlyGreen.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let { lp ->
                    lp.weight = if (left <= 0f) 0f else left
                    barMonthlyGreen.layoutParams = lp
                }
            }
            barMonthly?.requestLayout()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[StatisticsViewModel::class.java]
        storage = RoomStorageAdapter(requireContext())
        
        setupRecyclerViews()
        setupPeriodToggle()
        setupCharts()
        setupPieControls()
        setupWeekNavigation()
        setupVisualizationToggle()
        observeViewModel()

        // Presupuesto: inicializar y toggle al tocar la tarjeta
        val budgetCard = view.findViewById<com.google.android.material.card.MaterialCardView>(com.example.kolki.R.id.budgetCard)
        budgetCard?.setOnClickListener {
            showBudgetToday = !showBudgetToday
            // toggle visual selection
            budgetCard.isChecked = !budgetCard.isChecked
            updateBudgetHint()
        }
        // initialize state & selection
        updateBudgetHint()
        budgetCard?.isChecked = showBudgetToday

        // Summary toggle card (Este Mes / Saldo restante)
        val summaryCard = view.findViewById<com.google.android.material.card.MaterialCardView>(com.example.kolki.R.id.summaryToggleCard)
        summaryCard?.setOnClickListener {
            showMonthOnSummary = !showMonthOnSummary
            // toggle visual selection
            summaryCard.isChecked = !summaryCard.isChecked
            updateSummaryCard()
        }
        // initialize state & selection
        updateSummaryCard()
        summaryCard?.isChecked = showMonthOnSummary

        // Deep Analysis: navigate to new screen
        view.findViewById<android.view.View>(com.example.kolki.R.id.deepAnalysisButton)?.setOnClickListener {
            try { findNavController().navigate(com.example.kolki.R.id.navigation_deep_analysis) } catch (_: Exception) {
                android.widget.Toast.makeText(requireContext(), "No se pudo abrir Análisis Profundo", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Re-render charts when Room emits first data (initial load) and on changes
        viewLifecycleOwner.lifecycleScope.launch {
            storage.expenses.collectLatest {
                try {
                    renderWeek(currentWeekMonday)
                } catch (_: Exception) {}
                try {
                    // Only renders if start/end are set
                    renderPie()
                } catch (_: Exception) {}
                // Also refresh budget/summary dependent on snapshots
                try { updateSummaryCard() } catch (_: Exception) {}
                try { updateBudgetHint() } catch (_: Exception) {}
            }
        }
    }
    
    private fun setupRecyclerViews() {
        categoriesAdapter = CategoryStatsAdapter()
        binding.categoriesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = categoriesAdapter
        }
        
        recentExpensesAdapter = ExpensesAdapter(
            onItemClick = { _ -> },
            onEdit = { _ -> android.widget.Toast.makeText(requireContext(), "Editar desde Estadísticas próximamente", android.widget.Toast.LENGTH_SHORT).show() },
            onDelete = { _ -> android.widget.Toast.makeText(requireContext(), "Eliminar disponible en la lista de Gastos", android.widget.Toast.LENGTH_SHORT).show() }
        )
        binding.recentExpensesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = recentExpensesAdapter
        }
    }
    
    // ===== Charts =====
    private fun setupCharts() {
        setupPieChart(binding.pieChart)
        setupBarChart(binding.barChart)
        // Initial renders
        selectPieThisMonth()
        renderWeek(currentWeekMonday)
    }

    private fun setupPieChart(chart: PieChart) {
        chart.description.isEnabled = false
        chart.isDrawHoleEnabled = true
        chart.setUsePercentValues(true)
        val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        chart.setEntryLabelColor(if (isNight) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY)
        chart.centerText = "Categorías"
        chart.setNoDataText("Sin datos para mostrar")
        chart.setNoDataTextColor(if (isNight) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY)
        chart.legend.isEnabled = true
        chart.legend.textColor = if (isNight) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
    }

    private fun setupBarChart(chart: BarChart) {
        chart.description.isEnabled = false
        chart.setNoDataText("Sin datos para mostrar")
        val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        chart.setNoDataTextColor(if (isNight) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY)
        chart.axisRight.isEnabled = false
        chart.axisLeft.granularity = 1f
        chart.axisLeft.axisMinimum = 0f
        chart.axisLeft.textColor = if (isNight) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
        val x = chart.xAxis
        x.position = XAxis.XAxisPosition.BOTTOM
        x.granularity = 1f
        x.setDrawGridLines(false)
        x.textColor = if (isNight) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
        chart.legend.isEnabled = true
        chart.legend.textColor = if (isNight) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
    }

    private fun setupPieControls() {
        // Default: Mes actual
        binding.pieMonthButton.isChecked = true
        binding.pieToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                binding.pieWeekButton.id -> selectPieThisWeek()
                binding.pieMonthButton.id -> selectPieThisMonth()
                binding.pieRangeButton.id -> selectPieRange()
            }
        }
        binding.piePickRangeButton.setOnClickListener { pickPieDateRange() }
    }

    private fun setupWeekNavigation() {
        updateWeekLabel()
        binding.weekPrevButton.setOnClickListener {
            currentWeekMonday.add(Calendar.DAY_OF_YEAR, -7)
            updateWeekLabel()
            renderWeek(currentWeekMonday)
        }
        binding.weekNextButton.setOnClickListener {
            currentWeekMonday.add(Calendar.DAY_OF_YEAR, 7)
            updateWeekLabel()
            renderWeek(currentWeekMonday)
        }
    }

    private fun updateWeekLabel() {
        val start = currentWeekMonday.time
        val endCal = currentWeekMonday.clone() as Calendar
        endCal.add(Calendar.DAY_OF_YEAR, 6)
        val end = endCal.time
        val fmt = SimpleDateFormat("dd/MM", Locale.getDefault())
        binding.weekRangeLabel.text = "${fmt.format(start)} - ${fmt.format(end)}"
    }

    private fun selectPieThisWeek() {
        val cal = mondayOfWeek(Calendar.getInstance())
        pieStart = atStartOfDay(cal.time)
        val endCal = cal.clone() as Calendar
        endCal.add(Calendar.DAY_OF_YEAR, 6)
        pieEnd = atEndOfDay(endCal.time)
        binding.pieRangeContainer.visibility = View.GONE
        renderPie()
    }

    private fun selectPieThisMonth() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        pieStart = atStartOfDay(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        pieEnd = atEndOfDay(cal.time)
        binding.pieRangeContainer.visibility = View.GONE
        renderPie()
    }

    private fun selectPieRange() {
        // Show range controls; wait user to pick
        binding.pieRangeContainer.visibility = View.VISIBLE
    }

    private fun selectPieThisYear() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_YEAR, 1)
        pieStart = atStartOfDay(cal.time)
        cal.set(Calendar.MONTH, Calendar.DECEMBER)
        cal.set(Calendar.DAY_OF_MONTH, 31)
        pieEnd = atEndOfDay(cal.time)
        binding.pieRangeContainer.visibility = View.GONE
        renderPie()
    }

    private fun pickPieDateRange() {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        // First picker (start)
        DatePickerDialog(requireContext(), { _, y, m, d ->
            val s = Calendar.getInstance()
            s.set(y, m, d, 0, 0, 0)
            s.set(Calendar.MILLISECOND, 0)
            pieStart = s.time
            // Second picker (end)
            DatePickerDialog(requireContext(), { _, y2, m2, d2 ->
                val e = Calendar.getInstance()
                e.set(y2, m2, d2, 23, 59, 59)
                e.set(Calendar.MILLISECOND, 999)
                pieEnd = e.time
                val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val sDate = pieStart
                val eDate = pieEnd
                if (sDate != null && eDate != null) {
                    binding.pieRangeLabel.text = "${fmt.format(sDate)} - ${fmt.format(eDate)}"
                }
                renderPie()
            }, year, month, day).show()
        }, year, month, day).show()
    }

    private fun pickPeriodRange() {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(requireContext(), { _, y, m, d ->
            val s = Calendar.getInstance()
            s.set(y, m, d, 0, 0, 0)
            s.set(Calendar.MILLISECOND, 0)
            val start = s.time
            DatePickerDialog(requireContext(), { _, y2, m2, d2 ->
                val e = Calendar.getInstance()
                e.set(y2, m2, d2, 23, 59, 59)
                e.set(Calendar.MILLISECOND, 999)
                val end = e.time
                viewModel.setCustomRange(start, end)
                // Also sync pie to same range
                binding.pieRangeButton.isChecked = true
                pieStart = start
                pieEnd = end
                val fmt = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                binding.pieRangeLabel.text = "${fmt.format(start)} - ${fmt.format(end)}"
                renderPie()
            }, year, month, day).show()
        }, year, month, day).show()
    }

    private fun renderPie() {
        val start = pieStart ?: return
        val end = pieEnd ?: return
        val expenses = storage.getSnapshot()
        val inRange = expenses.filter { !it.date.before(start) && !it.date.after(end) }

        // Over-limit handling (exclude >100 from pie and show message)
        val overLimit = inRange.filter { it.amount > 100.0 }
        if (overLimit.isNotEmpty()) {
            val top = overLimit.maxByOrNull { it.amount }!!
            val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
            val symbol = prefs.getString("currency_symbol", "S/") ?: "S/"
            val catName = normalizeCategoryForCharts(top.category)
            binding.overLimitInfoText.visibility = View.VISIBLE
            binding.overLimitInfoText.text = "${catName.lowercase()} gastó $symbol ${String.format(Locale.getDefault(), "%.2f", top.amount)}"
        } else {
            binding.overLimitInfoText.visibility = View.GONE
        }

        val filtered = inRange.filter { it.amount <= 100.0 }
        // Save items per canonical category for detailed drilldown
        val itemsByCat = filtered.groupBy { normalizeCategoryForCharts(it.category) }
        val byCat = itemsByCat
            .mapValues { it.value.sumOf { e -> e.amount } }
        val total = byCat.values.sum()
        lastPieTotals = byCat
        lastPieTotalSum = total
        lastPieItemsByCat = itemsByCat
        if (total <= 0) {
            binding.pieChart.clear()
            binding.pieChart.invalidate()
            return
        }
        val entries = byCat.entries.map { (cat, amt) ->
            val percent = (amt / total * 100.0).toFloat()
            PieEntry(percent, cat)
        }
        val dataSet = PieDataSet(entries, "Porcentaje").apply {
            colors = categoryColors(byCat.keys)
            val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            valueTextColor = if (isNight) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
            valueTextSize = 12f
            sliceSpace = 2f
        }
        // Consistent colors per category
        dataSet.colors = entries.map { colorForCategory(it.label) }
        val data = PieData(dataSet)
        data.setValueFormatter(com.github.mikephil.charting.formatter.PercentFormatter(binding.pieChart))
        binding.pieChart.data = data
        // Legend improvements for many categories
        binding.pieChart.legend.isWordWrapEnabled = true
        binding.pieChart.legend.verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
        binding.pieChart.legend.orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
        binding.pieChart.legend.textSize = 10f
        val isNightLegend = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        binding.pieChart.legend.textColor = if (isNightLegend) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
        binding.pieChart.setExtraOffsets(8f, 8f, 8f, 16f)
        binding.pieChart.invalidate()

        // Selection: show percent and amount
        binding.pieChart.setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
            override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                val pe = e as? com.github.mikephil.charting.data.PieEntry ?: return
                val cat = pe.label
                val percent = pe.value
                val amount = lastPieTotals[cat] ?: (lastPieTotalSum * (percent / 100.0))
                val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
                val symbol = prefs.getString("currency_symbol", "S/") ?: "S/"
                // Drilldown: breakdown by original label (synonyms) and list expenses with comments
                val items = lastPieItemsByCat[cat].orEmpty()
                // Group by original label (fallback to canonical when null)
                val byOriginal = items.groupBy { (it.originalCategory?.trim()?.ifBlank { null }) ?: it.category }
                val breakdown = byOriginal.entries
                    .map { (label, list) -> label to list.sumOf { it.amount } }
                    .sortedByDescending { it.second }
                val sb = StringBuilder()
                sb.append("${String.format(Locale.getDefault(), "%.2f", percent)}%\n")
                sb.append("$symbol ${String.format(Locale.getDefault(), "%.2f", amount)}\n\n")
                if (breakdown.isNotEmpty()) {
                    sb.append("Desglose por sinónimo:\n")
                    breakdown.forEach { (label, tot) ->
                        sb.append("• ")
                        sb.append(label)
                        sb.append(": ")
                        sb.append(symbol)
                        sb.append(" ")
                        sb.append(String.format(Locale.getDefault(), "%.2f", tot))
                        sb.append("\n")
                    }
                    sb.append("\n")
                }
                if (items.isNotEmpty()) {
                    sb.append("Gastos:\n")
                    items.sortedByDescending { it.amount }.forEach { itx ->
                        sb.append("• ")
                        val label = (itx.originalCategory?.takeIf { s -> s.isNotBlank() }) ?: itx.category
                        sb.append(label)
                        sb.append(" — ")
                        sb.append(symbol)
                        sb.append(" ")
                        sb.append(String.format(Locale.getDefault(), "%.2f", itx.amount))
                        val c = itx.comment?.takeIf { s -> s.isNotBlank() }
                        if (c != null) {
                            sb.append(" — ")
                            sb.append(c)
                        }
                        sb.append("\n")
                    }
                }
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle(cat)
                    .setMessage(sb.toString())
                    .setPositiveButton("OK", null)
                    .show()
            }
            override fun onNothingSelected() {}
        })
    }

    private fun renderWeek(monday: Calendar) {
        val start = atStartOfDay(monday.time)
        val endCal = monday.clone() as Calendar
        endCal.add(Calendar.DAY_OF_YEAR, 6)
        val end = atEndOfDay(endCal.time)
        val expenses = storage.getSnapshot().filter { !it.date.before(start) && !it.date.after(end) }

        // Determine category order and colors
        val categories = expenses.map { normalizeCategoryForCharts(it.category) }.distinct().sorted()
        if (categories.isEmpty()) {
            binding.barChart.clear()
            binding.barChart.invalidate()
            return
        }
        val colors = ArrayList<Int>(categories.size)
        categories.forEach { colors.add(colorForCategory(it)) }

        // Build 7 stacked entries (Mon..Sun), values per category order
        val dayEntries = ArrayList<BarEntry>()
        val dayLabels = listOf("L","M","X","J","V","S","D")
        // Prepare list of over-limit per day
        val overByDay: MutableList<List<SimpleExpense>> = MutableList(7) { emptyList() }
        val dayCategoryMap: MutableList<Map<String, List<SimpleExpense>>> = MutableList(7) { emptyMap() }
        for (i in 0 until 7) {
            val dayStartCal = monday.clone() as Calendar
            dayStartCal.add(Calendar.DAY_OF_YEAR, i)
            val ds = atStartOfDay(dayStartCal.time)
            val de = atEndOfDay(dayStartCal.time)
            val dayTotals = DoubleArray(categories.size) { 0.0 }
            val dayItems = expenses.filter { !it.date.before(ds) && !it.date.after(de) }
            val dayNormal = dayItems.filter { it.amount <= 100.0 }
            overByDay[i] = dayItems.filter { it.amount > 100.0 }
            val groupedNormal = dayNormal
                .groupBy { normalizeCategoryForCharts(it.category) }
            dayCategoryMap[i] = groupedNormal
            groupedNormal.forEach { (cat, list) ->
                    val idx = categories.indexOf(cat)
                    if (idx >= 0) dayTotals[idx] = list.sumOf { it.amount }
                }
            dayEntries.add(BarEntry(i.toFloat(), dayTotals.map { it.toFloat() }.toFloatArray()))
        }

        if (dayEntries.isEmpty()) {
            binding.barChart.clear()
            binding.barChart.invalidate()
            return
        }

        // Add red indicator segment for days with over-limit
        val maxNormal = dayEntries.maxOfOrNull { it.yVals?.sum() ?: 0f } ?: 0f
        val indicatorSize = if (maxNormal > 0f) (maxNormal * 0.15f).coerceAtLeast(5f) else 10f
        val extendedColors = colors + arrayListOf(0xFFE53935.toInt()) // red indicator
        val extendedLabels = categories + listOf("Sobre límite")

        // Append indicator to each entry where applicable
        for (i in dayEntries.indices) {
            val entry = dayEntries[i]
            val yvals = entry.yVals?.toMutableList() ?: mutableListOf(entry.y)
            if (overByDay[i].isNotEmpty()) {
                yvals.add(indicatorSize)
            } else {
                yvals.add(0f)
            }
            dayEntries[i] = BarEntry(entry.x, yvals.toFloatArray())
        }

        val set = BarDataSet(dayEntries, "Gastos diarios").apply {
            setColors(extendedColors.toIntArray(), 255)
            setDrawValues(false)
            stackLabels = extendedLabels.toTypedArray()
        }
        val barData = BarData(set)
        barData.barWidth = 0.6f
        binding.barChart.data = barData
        binding.barChart.xAxis.valueFormatter = IndexAxisValueFormatter(dayLabels)
        binding.barChart.xAxis.labelCount = 7
        // Legend improvements
        binding.barChart.legend.isWordWrapEnabled = true
        binding.barChart.legend.verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
        binding.barChart.legend.orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
        binding.barChart.legend.textSize = 10f
        val isNightLegend = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        binding.barChart.legend.textColor = if (isNightLegend) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
        binding.barChart.setExtraOffsets(8f, 8f, 8f, 16f)
        binding.barChart.invalidate()

        // Show detail on selecting segments
        lastWeekDayCategoryMap = dayCategoryMap
        binding.barChart.setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
            override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                val be = e as? BarEntry ?: return
                val stackIndex = h?.stackIndex ?: -1
                if (stackIndex == extendedLabels.lastIndex) {
                    val dayIndex = be.x.toInt()
                    val items = overByDay.getOrNull(dayIndex).orEmpty()
                    if (items.isNotEmpty()) {
                        val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
                        val symbol = prefs.getString("currency_symbol", "S/") ?: "S/"
                        val msg = buildString {
                            append("Límites superados\n")
                            items.forEach { itx ->
                                append("• ")
                                append(normalizeCategoryForCharts(itx.category))
                                append(": ")
                                append(symbol)
                                append(" ")
                                append(String.format(Locale.getDefault(), "%.2f", itx.amount))
                                append("\n")
                            }
                        }
                        android.app.AlertDialog.Builder(requireContext())
                            .setTitle("Sobre límite")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                } else if (stackIndex in 0 until extendedLabels.lastIndex) {
                    val dayIndex = be.x.toInt()
                    val cat = extendedLabels.getOrNull(stackIndex) ?: return
                    val items = lastWeekDayCategoryMap.getOrNull(dayIndex)?.get(cat).orEmpty()
                    if (items.isNotEmpty()) {
                        val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
                        val symbol = prefs.getString("currency_symbol", "S/") ?: "S/"
                        val total = items.sumOf { it.amount }
                        val msg = buildString {
                            append("$cat - Total: ")
                            append(symbol)
                            append(" ")
                            append(String.format(Locale.getDefault(), "%.2f", total))
                            append("\n\n")
                            items.forEach { itx ->
                                append("• ")
                                append(itx.comment ?: "Sin comentario")
                                append(" — ")
                                append(symbol)
                                append(" ")
                                append(String.format(Locale.getDefault(), "%.2f", itx.amount))
                                append("\n")
                            }
                        }
                        android.app.AlertDialog.Builder(requireContext())
                            .setTitle("Gastos del día")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
            override fun onNothingSelected() {}
        })
    }

    private fun normalizeCategoryForCharts(src: String): String {
        // Usa el mismo mapeo de sinónimos que el parser de voz
        val normalized = parser.normalizeCategory(src)
        // Asegura pertenencia al conjunto canónico; si no, agrupa en "Otros"
        return if (normalized in canonicalSet) normalized else "Otros"
    }

    // Deterministic color per category (stable mapping based on name hash)
    private fun colorForCategory(category: String): Int {
        val palette = intArrayOf(
            0xFF6B46C1.toInt(), // purple
            0xFF805AD5.toInt(),
            0xFF2B6CB0.toInt(), // blue
            0xFF2F855A.toInt(), // green
            0xFFD69E2E.toInt(), // yellow
            0xFFB83280.toInt(), // pink
            0xFFDD6B20.toInt(), // orange
            0xFF319795.toInt(), // teal
            0xFF718096.toInt(), // gray
            0xFF1A202C.toInt(), // dark slate (reserve red exclusively for indicator)
        )
        val key = normalizeCategoryForCharts(category)
        val idx = (key.hashCode() and 0x7fffffff) % palette.size
        return palette[idx]
    }

    private fun categoryColors(categories: Collection<String>): ArrayList<Int> {
        val palette = arrayListOf(
            0xFF6B46C1.toInt(), // purple
            0xFF805AD5.toInt(),
            0xFF2B6CB0.toInt(), // blue
            0xFF2F855A.toInt(), // green
            0xFFD69E2E.toInt(), // yellow
            0xFFB83280.toInt(), // pink
            0xFFDD6B20.toInt(), // orange
            0xFF319795.toInt(), // teal
            0xFFE53E3E.toInt()  // red
        )
        val out = ArrayList<Int>()
        var idx = 0
        categories.forEach { _ ->
            out.add(palette[idx % palette.size])
            idx++
        }
        return out
    }

    private fun mondayOfWeek(base: Calendar): Calendar {
        val cal = base.clone() as Calendar
        cal.firstDayOfWeek = Calendar.MONDAY
        val diff = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // convert Sun=1.. to Mon=0..
        cal.add(Calendar.DAY_OF_YEAR, -diff)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    private fun atStartOfDay(date: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun atEndOfDay(date: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.time
    }
    
    private fun setupPeriodToggle() {
        binding.periodToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    binding.weekButton.id -> viewModel.setPeriod(StatisticsViewModel.Period.WEEK)
                    binding.monthButton.id -> viewModel.setPeriod(StatisticsViewModel.Period.MONTH)
                    binding.periodRangeButton.id -> {
                        // Select RANGE and prompt for dates
                        viewModel.setPeriod(StatisticsViewModel.Period.RANGE)
                        pickPeriodRange()
                    }
                }
            }
        }
        
        // Set default selection
        binding.monthButton.isChecked = true
        viewModel.setPeriod(StatisticsViewModel.Period.MONTH)

        // Sync pie (gastos por categoría) with main period selection
        viewModel.selectedPeriod.observe(viewLifecycleOwner) { p: StatisticsViewModel.Period ->
            when (p) {
                StatisticsViewModel.Period.WEEK -> {
                    binding.pieWeekButton.isChecked = true
                    selectPieThisWeek()
                }
                StatisticsViewModel.Period.MONTH -> {
                    binding.pieMonthButton.isChecked = true
                    selectPieThisMonth()
                }
                StatisticsViewModel.Period.RANGE -> {
                    selectPieRange()
                    // If range already chosen, render
                    viewModel.customRange.value?.let { (s, e) ->
                        pieStart = s
                        pieEnd = e
                        val fmt = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                        binding.pieRangeLabel.text = "${fmt.format(s)} - ${fmt.format(e)}"
                        renderPie()
                    }
                }
            }
        }

        // Also react to changes in custom range to update pie when RANGE is active
        viewModel.customRange.observe(viewLifecycleOwner) { range ->
            if (viewModel.selectedPeriod.value == StatisticsViewModel.Period.RANGE && range != null) {
                pieStart = range.first
                pieEnd = range.second
                val fmt = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                binding.pieRangeLabel.text = "${fmt.format(range.first)} - ${fmt.format(range.second)}"
                renderPie()
            }
        }
    }
    
    private fun observeViewModel() {
        // When totals change, refresh the new summary card instead of old text views
        viewModel.totalAmount.observe(viewLifecycleOwner) { _ ->
            updateSummaryCard()
        }
        
        viewModel.monthlyAmount.observe(viewLifecycleOwner) { _ ->
            updateSummaryCard()
        }
        
        viewModel.categoryTotals.observe(viewLifecycleOwner) { categories ->
            categoriesAdapter.submitList(categories)
        }
        
        viewModel.recentExpenses.observe(viewLifecycleOwner) { expenses ->
            recentExpensesAdapter.submitList(expenses.take(5)) // Show only 5 recent
        }

        // Remaining (Saldo Restante) -> also drives summary card
        viewModel.remaining.observe(viewLifecycleOwner) { _ ->
            updateSummaryCard()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
