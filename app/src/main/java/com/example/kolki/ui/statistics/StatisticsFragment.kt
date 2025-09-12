package com.example.kolki.ui.statistics

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kolki.databinding.FragmentStatisticsBinding
import com.example.kolki.ui.expenses.ExpensesAdapter
import com.example.kolki.data.SimpleExpense
import com.example.kolki.data.SimpleExpenseStorage
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
import java.text.SimpleDateFormat
import java.util.*

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: StatisticsViewModel
    private lateinit var categoriesAdapter: CategoryStatsAdapter
    private lateinit var recentExpensesAdapter: ExpensesAdapter
    
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("es", "PE"))
    private lateinit var storage: SimpleExpenseStorage
    private var pieStart: Date? = null
    private var pieEnd: Date? = null
    private var currentWeekMonday: Calendar = mondayOfWeek(Calendar.getInstance())
    private var lastPieTotals: Map<String, Double> = emptyMap()
    private var lastPieTotalSum: Double = 0.0
    private var lastWeekDayCategoryMap: List<Map<String, List<SimpleExpense>>> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[StatisticsViewModel::class.java]
        storage = SimpleExpenseStorage(requireContext())
        
        setupRecyclerViews()
        setupPeriodToggle()
        setupCharts()
        setupPieControls()
        setupWeekNavigation()
        observeViewModel()
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
        chart.setEntryLabelColor(android.graphics.Color.DKGRAY)
        chart.centerText = "Categorías"
        chart.setNoDataText("Sin datos para mostrar")
        chart.legend.isEnabled = true
    }

    private fun setupBarChart(chart: BarChart) {
        chart.description.isEnabled = false
        chart.setNoDataText("Sin datos para mostrar")
        chart.axisRight.isEnabled = false
        chart.axisLeft.granularity = 1f
        chart.axisLeft.axisMinimum = 0f
        val x = chart.xAxis
        x.position = XAxis.XAxisPosition.BOTTOM
        x.granularity = 1f
        x.setDrawGridLines(false)
        chart.legend.isEnabled = true
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
                binding.pieRangeLabel.text = "${fmt.format(pieStart)} - ${fmt.format(pieEnd)}"
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

        val byCat = inRange.filter { it.amount <= 100.0 }
            .groupBy { normalizeCategoryForCharts(it.category) }
            .mapValues { it.value.sumOf { e -> e.amount } }
        val total = byCat.values.sum()
        lastPieTotals = byCat
        lastPieTotalSum = total
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
            valueTextColor = android.graphics.Color.DKGRAY
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
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle(cat)
                    .setMessage("${String.format(Locale.getDefault(), "%.2f", percent)}%\n$symbol ${String.format(Locale.getDefault(), "%.2f", amount)}")
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
        val n = src.trim().lowercase(Locale.getDefault())
        return when (n) {
            // Servicios
            "alquiler", "telefono", "teléfono", "luz", "agua", "internet" -> "Servicios"
            // Transporte
            "taxi", "uber", "cabify", "bus", "micro", "colectivo", "transporte", "transportes" -> "Transporte"
            // Educación
            "copias", "fotocopias", "universidad", "uni", "matrícula", "matricula", "colegio", "educacion", "educación" -> "Educación"
            // Entretenimiento
            "cine", "peliculas", "películas", "netflix", "spotify", "entretenimiento" -> "Entretenimiento"
            // Medicina / Salud
            "farmacia", "medicina", "medicinas", "salud" -> "Medicina"
            else -> src
        }
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
                    binding.yearButton.id -> viewModel.setPeriod(StatisticsViewModel.Period.YEAR)
                }
            }
        }
        
        // Set default selection
        binding.monthButton.isChecked = true
        viewModel.setPeriod(StatisticsViewModel.Period.MONTH)
    }
    
    private fun observeViewModel() {
        viewModel.totalAmount.observe(viewLifecycleOwner) { total ->
            binding.totalAmountText.text = "S/ ${String.format("%.2f", total ?: 0.0)}"
        }
        
        viewModel.monthlyAmount.observe(viewLifecycleOwner) { amount ->
            binding.monthlyAmountText.text = "S/ ${String.format("%.2f", amount ?: 0.0)}"
        }
        
        viewModel.categoryTotals.observe(viewLifecycleOwner) { categories ->
            categoriesAdapter.submitList(categories)
        }
        
        viewModel.recentExpenses.observe(viewLifecycleOwner) { expenses ->
            recentExpensesAdapter.submitList(expenses.take(5)) // Show only 5 recent
        }

        // Remaining (Saldo Restante)
        viewModel.remaining.observe(viewLifecycleOwner) { value ->
            val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
            val symbol = prefs.getString("currency_symbol", "S/") ?: "S/"
            binding.remainingValueText.text = "$symbol ${String.format(Locale.getDefault(), "%.2f", value ?: 0.0)}"
            // Green if >= 0 else red
            val color = if ((value ?: 0.0) >= 0) android.graphics.Color.parseColor("#2E7D32") else android.graphics.Color.RED
            binding.remainingValueText.setTextColor(color)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
