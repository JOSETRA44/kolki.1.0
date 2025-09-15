package com.example.kolki.ui.budget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kolki.R
import com.example.kolki.data.SimpleExpenseStorage
import com.example.kolki.util.BudgetLog
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ManageBudgetsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_manage_budgets, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Wire edit button
        view.findViewById<View>(R.id.editBudgetButton)?.setOnClickListener {
            showBudgetDialog { refreshSummary(view) ; refreshHistory(view) }
        }

        refreshSummary(view)
        refreshHistory(view)
    }

    private fun refreshSummary(root: View) {
        val ctx = requireContext()
        val storage = SimpleExpenseStorage(ctx)
        val prefs = ctx.getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
        val symbol = prefs.getString("currency_symbol", "S/") ?: "S/"
        val amountMonthly = prefs.getFloat("budget_amount", 0f).toDouble()
        val mode = prefs.getString("budget_mode", "eom") ?: "eom"
        val customStart = prefs.getLong("budget_start", 0L)
        val customEnd = prefs.getLong("budget_end", 0L)

        // Determine period range based on mode
        val cal = Calendar.getInstance()
        val start: Date
        val end: Date
        when (mode) {
            "today" -> {
                start = atStartOfDay(cal.time)
                end = atEndOfDay(cal.time)
            }
            "custom" -> {
                val s = if (customStart > 0) Date(customStart) else cal.time
                val e = if (customEnd > 0) Date(customEnd) else cal.time
                start = atStartOfDay(s)
                end = atEndOfDay(e)
            }
            else -> { // eom
                val c1 = Calendar.getInstance(); c1.set(Calendar.DAY_OF_MONTH, 1)
                start = atStartOfDay(c1.time)
                val c2 = Calendar.getInstance(); c2.set(Calendar.DAY_OF_MONTH, c2.getActualMaximum(Calendar.DAY_OF_MONTH))
                end = atEndOfDay(c2.time)
            }
        }

        // Compute month/period spent
        val data = storage.getSnapshot()
        val inPeriod = data.filter { !it.date.before(start) && !it.date.after(end) }
        val spent = inPeriod.sumOf { it.amount }
        val remaining = (amountMonthly - spent).coerceAtLeast(0.0)

        // Today stats within the same period
        val todayStart = atStartOfDay(Calendar.getInstance().time)
        val todayEnd = atEndOfDay(Calendar.getInstance().time)
        val todaySpent = data.filter { !it.date.before(todayStart) && !it.date.after(todayEnd) }.sumOf { it.amount }
        val daysRemaining = ((end.time - todayStart.time) / (24*60*60*1000L) + 1).coerceAtLeast(0)
        val dailyAllowance = if (daysRemaining > 0) kotlin.math.floor((remaining / daysRemaining)).toLong() else 0L
        val remainingToday = kotlin.math.max(0.0, dailyAllowance.toDouble() - todaySpent)

        val nf = NumberFormat.getNumberInstance(Locale.getDefault())
        nf.maximumFractionDigits = 2
        val nf0 = NumberFormat.getNumberInstance(Locale.getDefault())
        nf0.maximumFractionDigits = 0

        root.findViewById<TextView>(R.id.summaryAmount)?.text = "$symbol ${nf0.format(kotlin.math.floor(amountMonthly))}"
        val modeLabel = when (mode) { "today" -> "hoy"; "eom" -> "fin de mes"; else -> "personalizado" }
        root.findViewById<TextView>(R.id.summaryMode)?.text = "Modo: $modeLabel"
        root.findViewById<TextView>(R.id.summaryMonthSpent)?.text = "Gastado este período: $symbol ${nf.format(spent)}"
        root.findViewById<TextView>(R.id.summaryMonthRemaining)?.text = "Restante del período: $symbol ${nf.format(remaining)}"
        root.findViewById<TextView>(R.id.summaryDaysRemaining)?.text = "Días restantes: $daysRemaining"
        root.findViewById<TextView>(R.id.summaryDailyAllowance)?.text = "Permitido diario: $symbol ${dailyAllowance}"
        root.findViewById<TextView>(R.id.summaryToday)?.text = "Hoy gastado: $symbol ${nf.format(todaySpent)} — Restante hoy: $symbol ${nf.format(remainingToday)}"
    }

    private fun refreshHistory(root: View) {
        val ctx = requireContext()
        val rv = root.findViewById<RecyclerView>(R.id.historyRecycler)
        rv.layoutManager = LinearLayoutManager(ctx)
        val events = BudgetLog.getEvents(ctx).filter { it.type.startsWith("budget_") }
        rv.adapter = object : RecyclerView.Adapter<SimpleVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleVH {
                val v = layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
                return SimpleVH(v)
            }
            override fun getItemCount(): Int = events.size
            override fun onBindViewHolder(holder: SimpleVH, position: Int) {
                val e = events[position]
                val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                holder.title.text = df.format(Date(e.timeMs))
                holder.subtitle.text = e.message
            }
        }
    }

    private class SimpleVH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(android.R.id.text1)
        val subtitle: TextView = v.findViewById(android.R.id.text2)
    }

    private fun showBudgetDialog(onSaved: () -> Unit) {
        val ctx = requireContext()
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 8)
        }
        val amountTil = com.google.android.material.textfield.TextInputLayout(ctx).apply { hint = "Seleccione su presupuesto para este período" }
        val amountEt = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        amountTil.addView(amountEt)
        container.addView(amountTil)

        // Mode options
        val modes = arrayOf("Hoy", "Fin de mes", "Personalizado")
        val radio = android.widget.RadioGroup(ctx).apply { orientation = android.widget.RadioGroup.VERTICAL }
        modes.forEachIndexed { idx, label ->
            radio.addView(android.widget.RadioButton(ctx).apply {
                text = label
                id = 2000 + idx
                isChecked = idx == 1
            })
        }
        container.addView(radio)

        // Custom range
        val customContainer = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(0, 16, 0, 0)
        }
        val startBtn = com.google.android.material.button.MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = "Inicio" }
        val endBtn = com.google.android.material.button.MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = "Fin" }
        val startLabel = android.widget.TextView(ctx).apply { text = "--/--/----"; setPadding(16,0,16,0) }
        val endLabel = android.widget.TextView(ctx).apply { text = "--/--/----"; setPadding(16,0,0,0) }
        customContainer.addView(startBtn); customContainer.addView(startLabel); customContainer.addView(endBtn); customContainer.addView(endLabel)
        container.addView(customContainer)

        radio.setOnCheckedChangeListener { _, checkedId ->
            customContainer.visibility = if (checkedId == 2002) View.VISIBLE else View.GONE
        }

        val cal = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        var startMillis: Long? = null
        var endMillis: Long? = null
        startBtn.setOnClickListener {
            android.app.DatePickerDialog(ctx, { _, y, m, d ->
                val c = Calendar.getInstance(); c.set(y, m, d, 0, 0, 0); c.set(Calendar.MILLISECOND, 0)
                startMillis = c.timeInMillis
                startLabel.text = dateFormat.format(c.time)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        endBtn.setOnClickListener {
            android.app.DatePickerDialog(ctx, { _, y, m, d ->
                val c = Calendar.getInstance(); c.set(y, m, d, 23, 59, 59); c.set(Calendar.MILLISECOND, 999)
                endMillis = c.timeInMillis
                endLabel.text = dateFormat.format(c.time)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("Establecer presupuesto")
            .setView(container)
            .setPositiveButton("Guardar") { dialog, _ ->
                val amount = amountEt.text?.toString()?.toDoubleOrNull() ?: 0.0
                if (amount <= 0.0) {
                    android.widget.Toast.makeText(ctx, "Ingrese un monto válido", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val mode = when (radio.checkedRadioButtonId) {
                    2000 -> "today"
                    2001 -> "eom"
                    2002 -> "custom"
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
                    prefs.edit().putLong("budget_start", startMillis!!).putLong("budget_end", endMillis!!).apply()
                }
                // Log
                val fmt = NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 0 }
                val amountStr = fmt.format(kotlin.math.floor(amount))
                val modeLabel = when (mode) { "today" -> "hoy"; "eom" -> "fin de mes"; else -> "personalizado" }
                BudgetLog.addEvent(ctx, "budget_set", "Se definió tu presupuesto a S/ $amountStr ($modeLabel)")
                android.widget.Toast.makeText(ctx, "Presupuesto guardado", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                onSaved()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun atStartOfDay(date: Date): Date {
        val cal = Calendar.getInstance(); cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }
    private fun atEndOfDay(date: Date): Date {
        val cal = Calendar.getInstance(); cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        return cal.time
    }
}
