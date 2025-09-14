package com.example.kolki.ui.analysis

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.fragment.app.Fragment
import com.example.kolki.R
import com.example.kolki.data.SimpleExpenseStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DeepAnalysisFragment : Fragment() {

    private lateinit var storage: SimpleExpenseStorage
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private var lastIaCallMs: Long = 0L
    private var fullContextEnabled: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_deep_analysis, container, false)
    }

    private fun showManageKeysDialog() {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)

        val container = android.widget.LinearLayout(ctx).apply {
            this.orientation = android.widget.LinearLayout.VERTICAL
            this.setPadding(32, 24, 32, 0)
        }

        fun row(title: String, prefKey: String): View {
            val row = android.widget.LinearLayout(ctx).apply {
                this.orientation = android.widget.LinearLayout.HORIZONTAL
                this.setPadding(0, 12, 0, 12)
                this.gravity = Gravity.CENTER_VERTICAL
            }
            val status = android.widget.ImageView(ctx).apply {
                val has = !prefs.getString(prefKey, null).isNullOrBlank()
                setImageResource(if (has) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background)
            }
            val tv = TextView(ctx).apply {
                text = title
                this.setPadding(12, 0, 0, 0)
            }
            val editBtn = com.google.android.material.button.MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Editar"
                setOnClickListener {
                    showSingleKeyEditDialog(title, prefKey) { showManageKeysDialog() }
                }
            }
            val clearBtn = com.google.android.material.button.MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Borrar"
                setOnClickListener {
                    prefs.edit().remove(prefKey).apply()
                    android.widget.Toast.makeText(ctx, "${title}: borrada", android.widget.Toast.LENGTH_SHORT).show()
                    showManageKeysDialog()
                }
            }
            val spacer = View(ctx).apply { layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f) }
            row.addView(status)
            row.addView(tv)
            row.addView(spacer)
            row.addView(editBtn)
            row.addView(clearBtn)
            return row
        }

        container.addView(TextView(ctx).apply { text = "Gestionar mis llaves"; textSize = 18f; setPadding(0,0,0,8) })
        container.addView(row("Análisis (Planner)", "planner_api_key"))
        container.addView(row("ChatGPT (OpenAI)", "openai_api_key"))
        container.addView(row("Gemini", "gemini_api_key"))

        AlertDialog.Builder(ctx)
            .setView(container as View)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun showSingleKeyEditDialog(title: String, prefKey: String, onDone: () -> Unit) {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
        val til = com.google.android.material.textfield.TextInputLayout(ctx).apply {
            endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
            hint = "Pega la clave de $title"
        }
        val input = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText("")
        }
        til.addView(input)

        AlertDialog.Builder(ctx)
            .setTitle("Editar clave: $title")
            .setView(til)
            .setPositiveButton("Guardar") { d, _ ->
                val raw = input.text?.toString() ?: ""
                val key = raw.trim().replace("\\s+".toRegex(), "").replace("\"", "")
                prefs.edit().putString(prefKey, key).apply()
                android.widget.Toast.makeText(ctx, "$title: guardada", android.widget.Toast.LENGTH_SHORT).show()
                d.dismiss(); onDone()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ==== Gemini Data Planner helpers ====
    private fun buildDataPlannerPrompt(question: String): String {
        val tz = java.util.TimeZone.getDefault()
        val offsetMinutes = tz.rawOffset / 60000
        val sign = if (offsetMinutes >= 0) "+" else "-"
        val absMin = kotlin.math.abs(offsetMinutes)
        val tzStr = String.format("GMT%s%02d:%02d", sign, absMin / 60, absMin % 60)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return (
            "Actúa como 'Data Planner' en español. Responde SOLO con un JSON VÁLIDO indicando QUÉ datos locales necesitas. " +
            "No incluyas texto extra fuera del JSON.\n" +
            "Hoy es: " + today + " (zona horaria: " + tzStr + "). Interpreta 'hoy', 'ayer', 'esta semana', 'este mes' relativo a esa fecha.\n" +
            "Estructura exacta: {" +
            "\"dateRange\": {\"from\": \"YYYY-MM-DD\", \"to\": \"YYYY-MM-DD\"}, " +
            "\"categories\": [\"...\"], " +
            "\"includeNotes\": true, " +
            "\"types\": [\"gasto\", \"ingreso\"], " +
            "\"topN\": 100" +
            "}.\n" +
            "Pregunta del usuario: " + question
        )
    }

    private fun safelyParsePlannerJson(text: String): org.json.JSONObject? {
        return try {
            // Find first JSON object in text
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start >= 0 && end > start) {
                val jsonStr = text.substring(start, end + 1)
                org.json.JSONObject(jsonStr)
            } else null
        } catch (_: Exception) { null }
    }

    private fun resolvePlannerRelativeDates(req: org.json.JSONObject, userQuestion: String) {
        val lower = userQuestion.lowercase(Locale.getDefault())
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val todayStr = sdf.format(cal.time)
        val dateRange = if (req.has("dateRange")) req.getJSONObject("dateRange") else org.json.JSONObject().also { req.put("dateRange", it) }

        fun setRange(from: Calendar, to: Calendar) {
            dateRange.put("from", sdf.format(from.time))
            dateRange.put("to", sdf.format(to.time))
        }

        // If planner didn't specify dates, infer from common Spanish phrases
        val fromStr = dateRange.optString("from", "")
        val toStr = dateRange.optString("to", "")
        if (fromStr.isBlank() && toStr.isBlank()) {
            when {
                lower.contains("hoy") -> {
                    setRange(cal.clone() as Calendar, cal.clone() as Calendar)
                }
                lower.contains("ayer") -> {
                    val c1 = cal.clone() as Calendar; c1.add(Calendar.DAY_OF_MONTH, -1)
                    setRange(c1, c1)
                }
                lower.contains("esta semana") -> {
                    val start = cal.clone() as Calendar
                    start.firstDayOfWeek = Calendar.MONDAY
                    start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    val end = start.clone() as Calendar
                    end.add(Calendar.DAY_OF_MONTH, 6)
                    setRange(start, end)
                }
                lower.contains("este mes") -> {
                    val start = cal.clone() as Calendar
                    start.set(Calendar.DAY_OF_MONTH, 1)
                    val end = cal.clone() as Calendar
                    end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
                    setRange(start, end)
                }
                else -> {
                    // Default to today to avoid ambiguous answers
                    setRange(cal.clone() as Calendar, cal.clone() as Calendar)
                }
            }
        }
    }

    private fun renderPlannerSummary(req: org.json.JSONObject): String {
        val dr = req.optJSONObject("dateRange")
        val from = dr?.optString("from").orEmpty()
        val to = dr?.optString("to").orEmpty()
        val catsArr = req.optJSONArray("categories")
        val cats = if (catsArr != null) (0 until catsArr.length()).map { catsArr.optString(it) } else emptyList()
        val includeNotes = req.optBoolean("includeNotes", true)
        val typesArr = req.optJSONArray("types")
        val types = if (typesArr != null) (0 until typesArr.length()).map { typesArr.optString(it) } else emptyList()
        val topN = req.optInt("topN", 200)

        val catsDisplay = if (cats.isEmpty()) "todas" else cats.joinToString(", ")
        val typesDisplay = if (types.isEmpty()) "gasto e ingreso" else types.joinToString(", ")
        val rangeDisplay = when {
            from.isNotBlank() && to.isNotBlank() -> "$from a $to"
            from.isNotBlank() -> "desde $from"
            to.isNotBlank() -> "hasta $to"
            else -> "sin rango específico"
        }
        return buildString {
            append("Plan de datos detectado:\n")
            append("• Rango: $rangeDisplay\n")
            append("• Categorías: $catsDisplay\n")
            append("• Tipos: $typesDisplay\n")
            append("• topN: $topN\n")
            append("• Incluir notas: ${if (includeNotes) "sí" else "no"}")
        }
    }

    private fun prepareLocalDataForRequest(req: org.json.JSONObject): String {
        val data = storage.getSnapshot()
        if (data.isEmpty()) return "Sin datos locales"
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val from = req.optJSONObject("dateRange")?.optString("from")
        val to = req.optJSONObject("dateRange")?.optString("to")
        val cats = req.optJSONArray("categories")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } } ?: emptyList()
        val types = req.optJSONArray("types")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } } ?: listOf("gasto","ingreso")
        val topN = req.optInt("topN", 200)
        val includeNotes = req.optBoolean("includeNotes", true)

        fun parseDateSafe(s: String?): Date? = try { if (s.isNullOrBlank()) null else sdf.parse(s) } catch (_: Exception) { null }
        val fromDate = parseDateSafe(from)?.let { atStartOfDay(it) }
        val toDate = parseDateSafe(to)?.let { atEndOfDay(it) }

        val filtered = data.asSequence()
            .filter { e -> fromDate?.let { !e.date.before(it) } ?: true }
            .filter { e -> toDate?.let { !e.date.after(it) } ?: true }
            .filter { e -> if (cats.isNotEmpty()) cats.contains(e.category) else true }
            .filter { e ->
                // Infer type if available in model (assuming amount>0 is gasto unless marked)
                // If your model has an explicit type, replace this with that field
                val t = if (e.amount >= 0) "gasto" else "ingreso"
                types.contains(t)
            }
            .sortedBy { it.date }
            .take(topN)
            .toList()

        val sb = StringBuilder()
        sb.append("Registros filtrados: ${filtered.size}\n")
        filtered.forEachIndexed { idx, e ->
            val d = try { sdf.format(e.date) } catch (_: Exception) { "" }
            val cat = e.category ?: "(sin categoría)"
            val amt = String.format(Locale.getDefault(), "%.2f", kotlin.math.abs(e.amount))
            val ty = if (e.amount >= 0) "gasto" else "ingreso"
            sb.append("${idx + 1}. fecha=${d}; categoria=${cat}; tipo=${ty}; monto=${amt}")
            if (includeNotes) {
                val note = e.toString() // replace with e.note if exists in your model
                sb.append("; nota=${note}")
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun buildFinalAnswerPrompt(question: String, localPayload: String): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return "Eres un analista financiero. Responde en español de forma clara para el cliente.\n" +
                "Fecha actual: " + today + "\n" +
                "Datos locales relevantes (filtrados):\n" + localPayload + "\n" +
                "Tareas:\n" +
                "1) Calcula totales, conteos y, si aplica, desglose por categoría/fecha.\n" +
                "2) Responde a la pregunta con números concretos y breve explicación.\n" +
                "3) No inventes datos fuera de 'Datos locales'.\n" +
                "Pregunta del usuario: " + question
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        storage = SimpleExpenseStorage(requireContext())

        val quickGroup = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.quickQuestionsGroup)
        val inputEt = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.inputEditText)
        val sendBtn = view.findViewById<View>(R.id.sendButton)
        val chatRv = view.findViewById<RecyclerView>(R.id.chatRecycler)
        val cfgBtn = view.findViewById<View>(R.id.configureKeyButton)
        val keyStatus = view.findViewById<TextView>(R.id.keyStatusText)
        val keyDot = view.findViewById<android.widget.ImageView>(R.id.keyStatusDot)
        // Small top switch near the icons to include local context in AI
        val includeLocal = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.includeLocalTopSwitch)
        val clearCooldown = view.findViewById<View>(R.id.clearCooldownButton)
        val clearChat = view.findViewById<View>(R.id.clearChatButton)
        val providerGroup = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.providerGroup)
        val providerOpenAi = view.findViewById<com.google.android.material.chip.Chip>(R.id.providerOpenAi)
        val providerGemini = view.findViewById<com.google.android.material.chip.Chip>(R.id.providerGemini)
        val providerLocal = view.findViewById<com.google.android.material.chip.Chip>(R.id.providerLocal)
        val attachContextBtn = view.findViewById<View>(R.id.attachContextButton)

        // Setup chat list
        chatAdapter = ChatAdapter()
        chatRv?.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
            adapter = chatAdapter
        }
        restoreProviderSelection(providerGroup, providerOpenAi, providerGemini, providerLocal)

        // Wire quick questions to answer instantly
        quickGroup?.children?.forEach { v ->
            (v as? com.google.android.material.chip.Chip)?.setOnClickListener {
                val q = v.text?.toString().orEmpty()
                inputEt?.setText(q)
                inputEt?.setSelection(inputEt.text?.length ?: 0)
                val ans = answerQuick(q)
                if (ans != null) {
                    addMessage("user", q)
                    addMessage("assistant", ans)
                }
            }
        }

        // Load key status
        updateKeyIndicators(keyDot, keyStatus)

        cfgBtn?.setOnClickListener {
            showManageKeysDialog()
        }

        // Long-press on key icon to configure the Planner API key (separate key for analysis-only step)
        cfgBtn?.setOnLongClickListener {
            showPlannerKeyDialog(keyStatus)
            true
        }

        clearCooldown?.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .remove("openai_cooldown_until_ms")
                .remove("gemini_cooldown_until_ms")
                .apply()
            android.widget.Toast.makeText(requireContext(), "Cooldown limpiado", android.widget.Toast.LENGTH_SHORT).show()
        }

        clearChat?.setOnClickListener {
            messages.clear()
            chatAdapter.submitList(messages.toList())
        }

        attachContextBtn?.setOnClickListener {
            fullContextEnabled = !fullContextEnabled
            android.widget.Toast.makeText(
                requireContext(),
                if (fullContextEnabled) "Resumen extendido: activado" else "Resumen extendido: desactivado",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        providerGroup?.setOnCheckedChangeListener { _, _ ->
            val provider = when {
                providerLocal?.isChecked == true -> "local"
                providerGemini?.isChecked == true -> "gemini"
                else -> "openai"
            }
            requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putString("ai_provider", provider).apply()
            updateKeyIndicators(keyDot, keyStatus)
        }

        sendBtn?.setOnClickListener {
            val q = inputEt?.text?.toString()?.trim().orEmpty()
            if (q.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "Escribe una pregunta", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Simple rate-limit para evitar 429 por spam
            val now = System.currentTimeMillis()
            val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
            val providerForCooldown = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
                .getString("ai_provider", "gemini")
            val cooldownKey = if (providerForCooldown == "gemini") "gemini_cooldown_until_ms" else "openai_cooldown_until_ms"
            val cooldownUntil = prefs.getLong(cooldownKey, 0L)
            if (now < cooldownUntil) {
                val waitSec = ((cooldownUntil - now) / 1000).coerceAtLeast(1)
                addMessage("assistant", "Has alcanzado el límite. Intenta nuevamente en ~${waitSec}s.")
                // Disable send temporarily to mimic chat behavior
                sendBtn?.isEnabled = false
                android.widget.Toast.makeText(requireContext(), "Enfriando (${waitSec}s)", android.widget.Toast.LENGTH_SHORT).show()
                val reEnableMs = (cooldownUntil - now + 300).coerceAtLeast(500)
                sendBtn?.postDelayed({ sendBtn.isEnabled = true }, reEnableMs)
                return@setOnClickListener
            }
            if (now - lastIaCallMs < 10000) { // 1 llamada cada 10s para evitar 429
                addMessage("assistant", "Espera unos segundos antes de otra consulta.")
                return@setOnClickListener
            }

            val local = answerQuick(q)
            if (local != null) {
                addMessage("user", q)
                addMessage("assistant", local)
                try { inputEt?.setText("") } catch (_: Exception) {}
                chatRv?.post { chatRv.scrollToPosition((chatAdapter.itemCount - 1).coerceAtLeast(0)) }
                return@setOnClickListener
            }
            // Ready to call IA (with optional local context)
            val provider = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
                .getString("ai_provider", "local") ?: "local"
            if (provider == "local") {
                // Solo respuestas locales
                val localAns = answerQuick(q)
                addMessage("user", q)
                if (localAns != null) {
                    addMessage("assistant", localAns)
                } else {
                    addMessage("assistant", "Modo Local: usa las preguntas rápidas o activa un proveedor (OpenAI/Gemini).")
                }
                return@setOnClickListener
            } else if (provider == "openai") {
                val key = prefs.getString("openai_api_key", null)
                if (key.isNullOrBlank()) {
                    addMessage("assistant", "Configura tu API Key de OpenAI para preguntas abiertas.")
                    return@setOnClickListener
                }
                addMessage("user", q)
                try { inputEt?.setText("") } catch (_: Exception) {}
                chatRv?.post { chatRv.scrollToPosition((chatAdapter.itemCount - 1).coerceAtLeast(0)) }
                lastIaCallMs = now
                val prompt = if (includeLocal?.isChecked == true) {
                    val ctxStr = if (fullContextEnabled) buildFullContext() else buildLocalContext()
                    "Contexto:\n" + ctxStr + "\n\nPregunta: " + q
                } else q
                callChatApi(prompt.take(2000), key,
                    onResult = { text -> addMessage("assistant", text) },
                    onError = { err -> addMessage("assistant", "Error IA: $err") }
                )
            } else {
                val key = prefs.getString("gemini_api_key", null)
                if (key.isNullOrBlank()) {
                    addMessage("assistant", "Configura tu API Key de Gemini para preguntas abiertas.")
                    return@setOnClickListener
                }
                addMessage("user", q)
                lastIaCallMs = now
                if (includeLocal?.isChecked == true) {
                    // Two-step flow visible in chat:
                    // 1) Planner (mensaje 1: "Analizando..." + resumen del plan)
                    // 2) Respuesta final (mensaje 2)
                    val plannerPrompt = buildDataPlannerPrompt(q)
                    addMessage("assistant", "Analizando tu consulta… preparando y filtrando datos locales relevantes.")
                    // Use dedicated planner_api_key if available; otherwise fall back to Gemini key
                    val plannerKey = prefs.getString("planner_api_key", null) ?: key
                    callGeminiApi(plannerPrompt.take(2000), plannerKey,
                        onResult = { plannerResp ->
                            // Try to parse JSON request
                            val req = safelyParsePlannerJson(plannerResp)
                            if (req != null) {
                                // Resolver fechas relativas (hoy/ayer/semana/mes) si faltan (mensaje interno/no visible)
                                resolvePlannerRelativeDates(req, q)
                            }
                            val localPayload = if (req != null) prepareLocalDataForRequest(req) else buildLocalContext()
                            val finalPrompt = buildFinalAnswerPrompt(q, localPayload)
                            callGeminiApi(finalPrompt.take(2000), key,
                                onResult = { ans -> addMessage("assistant", ans) },
                                onError = { err2 -> addMessage("assistant", "Error IA (final): $err2") }
                            )
                        },
                        onError = { err ->
                            // Fallback: use short context
                            val ctxStr = buildLocalContext()
                            val fallbackPrompt = buildFinalAnswerPrompt(q, ctxStr)
                            callGeminiApi(fallbackPrompt.take(2000), key,
                                onResult = { ans -> addMessage("assistant", ans) },
                                onError = { err2 -> addMessage("assistant", "Error IA: $err2") }
                            )
                        }
                    )
                } else {
                    callGeminiApi(q.take(2000), key,
                        onResult = { text -> addMessage("assistant", text) },
                        onError = { err -> addMessage("assistant", "Error IA: $err") }
                    )
                }
            }
        }
    }

    private fun buildLocalContext(): String {
        val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
        val symbol = prefs.getString("currency_symbol", "S/") ?: "S/"
        val data = storage.getSnapshot()
        if (data.isEmpty()) return "Sin datos locales"
        val now = Calendar.getInstance()
        val monthCal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        val monthStart = atStartOfDay(monthCal.time)
        val monthEnd = atEndOfDay(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)) }.time)
        val inMonth = data.filter { !it.date.before(monthStart) && !it.date.after(monthEnd) }
        val monthSpent = inMonth.sumOf { it.amount }
        val byCat = inMonth.groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }
        val topCats = byCat.entries.sortedByDescending { it.value }.take(5)
        val lines = topCats.joinToString("\n") { e -> "• ${e.key}: $symbol ${String.format(Locale.getDefault(), "%.2f", e.value)}" }
        val budgetAmount = prefs.getFloat("budget_amount", 0f).toDouble()
        val mode = prefs.getString("budget_mode", "eom") ?: "eom"
        return buildString {
            append("Mes actual gastado: $symbol ${String.format(Locale.getDefault(), "%.2f", monthSpent)}\n")
            if (budgetAmount > 0) append("Presupuesto definido: $symbol ${String.format(Locale.getDefault(), "%.0f", kotlin.math.floor(budgetAmount))} (modo: $mode)\n")
            if (lines.isNotBlank()) {
                append("Top categorías este mes:\n")
                append(lines)
                append('\n')
            }
        }
    }

    private fun buildFullContext(): String {
        // Comprehensive flat listing of records (date, category, amount) to maximize AI context without raw DB
        val data = storage.getSnapshot()
        if (data.isEmpty()) return "Sin datos locales"
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val header = "Total de registros: ${data.size}"
        val lines = StringBuilder()
        // Limit output to avoid exceeding request size; include first 1500 records max
        val maxRecords = 1500
        data.sortedBy { it.date }.take(maxRecords).forEachIndexed { idx, e ->
            val d = try { sdf.format(e.date) } catch (_: Exception) { "" }
            val cat = e.category ?: "(sin categoría)"
            val amt = String.format(Locale.getDefault(), "%.2f", e.amount)
            lines.append("${idx + 1}. fecha=${d}; categoria=${cat}; monto=${amt}\n")
        }
        if (data.size > maxRecords) {
            lines.append("... (${data.size - maxRecords} registros más no incluidos en este resumen)\n")
        }
        return "$header\n$lines"
    }

    // Returns a string if the question matches a quick local query; otherwise null
    private fun answerQuick(q: String): String? {
        val text = q.lowercase(Locale.getDefault())
        return when {
            text.contains("día gast") || text.contains("dia gast") || text.contains("qué día gast") -> topSpendingDay()
            text.contains("horas gast") || text.contains("hora gast") -> topSpendingHours()
            text.contains("categoría más alta") || text.contains("categoria mas alta") -> topCategoryThisMonth()
            else -> null
        }
    }

    private fun topSpendingDay(): String {
        val data = storage.getSnapshot()
        if (data.isEmpty()) return "No hay datos de gastos aún."
        val fmtKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val fmtOut = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val totals = data.groupBy { fmtKey.format(it.date) }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        val max = totals.maxByOrNull { it.value } ?: return "No hay datos de gastos aún."
        val date = fmtKey.parse(max.key) ?: Date()
        val symbol = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
            .getString("currency_symbol", "S/") ?: "S/"
        return "El día con más gasto fue ${fmtOut.format(date)}: $symbol ${String.format(Locale.getDefault(), "%.2f", max.value)}"
    }

    private fun topSpendingHours(): String {
        val data = storage.getSnapshot()
        if (data.isEmpty()) return "No hay datos de gastos aún."
        val byHour = IntArray(24) { 0 }
        val sumByHour = DoubleArray(24) { 0.0 }
        val cal = Calendar.getInstance()
        data.forEach { e ->
            cal.time = e.date
            val h = cal.get(Calendar.HOUR_OF_DAY)
            byHour[h] += 1
            sumByHour[h] += e.amount
        }
        val pairs = (0 until 24).map { h -> h to sumByHour[h] }.sortedByDescending { it.second }.take(3)
        val symbol = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
            .getString("currency_symbol", "S/") ?: "S/"
        val lines = pairs.joinToString("\n") { (h, sum) ->
            val label = String.format(Locale.getDefault(), "%02d:00-%02d:00", h, (h + 1) % 24)
            "• $label — $symbol ${String.format(Locale.getDefault(), "%.2f", sum)}"
        }
        return "Horas con mayor gasto (top 3):\n$lines"
    }

    private fun topCategoryThisMonth(): String {
        val data = storage.getSnapshot()
        if (data.isEmpty()) return "No hay datos de gastos aún."
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = atStartOfDay(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val end = atEndOfDay(cal.time)
        val inMonth = data.filter { !it.date.before(start) && !it.date.after(end) }
        if (inMonth.isEmpty()) return "No hay gastos registrados este mes."
        val byCat = inMonth.groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }
        val top = byCat.maxByOrNull { it.value } ?: return "No hay gastos registrados este mes."
        val symbol = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
            .getString("currency_symbol", "S/") ?: "S/"
        return "Categoría más alta este mes: ${top.key} — $symbol ${String.format(Locale.getDefault(), "%.2f", top.value)}"
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

    // ==== API Key helpers ====
    private fun showKeyDialog(statusTv: TextView?) {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)

        // Detect provider selected now
        val provider = try {
            val geminiChip = requireView().findViewById<com.google.android.material.chip.Chip>(R.id.providerGemini)
            val localChip = requireView().findViewById<com.google.android.material.chip.Chip>(R.id.providerLocal)
            when {
                localChip?.isChecked == true -> "local"
                geminiChip?.isChecked == true -> "gemini"
                else -> "openai"
            }
        } catch (_: Exception) { "openai" }

        // Build input with toggle visibility and empty by default to avoid masked clutter
        val til = com.google.android.material.textfield.TextInputLayout(ctx).apply {
            endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
            hint = "Pega tu API key aquí"
        }
        val input = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText("") // start blank to avoid confusion with bullets
        }
        til.addView(input)

        AlertDialog.Builder(ctx)
            .setTitle("Configurar API Key (${provider.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }})")
            .setMessage("La clave se guarda localmente en este dispositivo. Puedes mostrar/ocultar con el ícono.")
            .setView(til)
            .setPositiveButton("Guardar") { d, _ ->
                val raw = input.text?.toString() ?: ""
                // Sanitize: trim, remove spaces/linebreaks/quotes
                val key = raw.trim().replace("\\s+".toRegex(), "").replace("\"", "")
                if (provider == "gemini") {
                    prefs.edit().putString("gemini_api_key", key).apply()
                } else if (provider == "openai") {
                    prefs.edit().putString("openai_api_key", key).apply()
                } else {
                    // local mode: do nothing
                }
                updateKeyStatus(statusTv)
                d.dismiss()
            }
            .setNeutralButton("Borrar clave") { d, _ ->
                when (provider) {
                    "gemini" -> prefs.edit().remove("gemini_api_key").apply()
                    "openai" -> prefs.edit().remove("openai_api_key").apply()
                    else -> {}
                }
                updateKeyStatus(statusTv)
                d.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showPlannerKeyDialog(statusTv: TextView?) {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)

        val til = com.google.android.material.textfield.TextInputLayout(ctx).apply {
            endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
            hint = "Pega la Planner API Key"
        }
        val input = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText("")
        }
        til.addView(input)

        AlertDialog.Builder(ctx)
            .setTitle("Configurar Planner API Key")
            .setMessage("Esta clave se usa solo para el paso de análisis (planner). Se guarda localmente.")
            .setView(til)
            .setPositiveButton("Guardar") { d, _ ->
                val raw = input.text?.toString() ?: ""
                val key = raw.trim().replace("\\s+".toRegex(), "").replace("\"", "")
                prefs.edit().putString("planner_api_key", key).apply()
                updateKeyStatus(statusTv)
                d.dismiss()
            }
            .setNeutralButton("Borrar clave") { d, _ ->
                prefs.edit().remove("planner_api_key").apply()
                updateKeyStatus(statusTv)
                d.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateKeyStatus(statusTv: TextView?) {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
        val key = prefs.getString("openai_api_key", null)
        val gkey = prefs.getString("gemini_api_key", null)
        val openAiStr = if (key.isNullOrBlank()) "OpenAI: no" else "OpenAI: sí"
        val geminiStr = if (gkey.isNullOrBlank()) "Gemini: no" else "Gemini: sí"
        statusTv?.text = "$openAiStr | $geminiStr"
    }

    private fun updateKeyIndicators(dot: android.widget.ImageView?, statusTv: TextView?) {
        // Keep status text updated (even if hidden)
        updateKeyStatus(statusTv)
        val prefs = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
        val provider = prefs.getString("ai_provider", "gemini")
        val hasKey = when (provider) {
            "gemini" -> !prefs.getString("gemini_api_key", null).isNullOrBlank()
            "openai" -> !prefs.getString("openai_api_key", null).isNullOrBlank()
            else -> true // local does not require key
        }
        val resId = if (hasKey) R.drawable.dot_key_ok else R.drawable.dot_key_missing
        try { dot?.setBackgroundResource(resId) } catch (_: Exception) {}
    }

    // ==== Minimal Chat API call (uses local API key; no third-party libs) ====
    private fun callChatApi(prompt: String, apiKey: String, onResult: (String) -> Unit, onError: (String) -> Unit) {
        Thread {
            var attempt = 0
            var backoffMs = 2000L
            while (attempt < 3) {
                attempt++
                try {
                    val url = java.net.URL("https://api.openai.com/v1/chat/completions")
                    val conn = (url.openConnection() as javax.net.ssl.HttpsURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Authorization", "Bearer $apiKey")
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                        connectTimeout = 15000
                        readTimeout = 30000
                    }

                    val sysMsg = "Eres un analista financiero. Responde corto y claro. Moneda por defecto S/."
                    val body = org.json.JSONObject().apply {
                        put("model", "gpt-3.5-turbo")
                        put("temperature", 0.2)
                        put("messages", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply { put("role", "system"); put("content", sysMsg) })
                            put(org.json.JSONObject().apply { put("role", "user"); put("content", prompt) })
                        })
                    }
                    conn.outputStream.writer(Charsets.UTF_8).use { it.write(body.toString()) }

                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val resp = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    conn.disconnect()

                    if (code == 429) {
                        // Set short cooldown to avoid hammering free tier
                        val retryAfter = conn.getHeaderField("Retry-After")?.toLongOrNull()?.times(1000) ?: backoffMs
                        val until = System.currentTimeMillis() + retryAfter
                        requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putLong("openai_cooldown_until_ms", until).apply()
                        if (attempt < 3) {
                            Thread.sleep(retryAfter)
                            backoffMs = (backoffMs * 2).coerceAtMost(8000L)
                            continue
                        }
                        runOnUiThread { onError("Has excedido el cupo temporal. Intenta en unos minutos.") }
                        return@Thread
                    }

                    if (code !in 200..299) {
                        runOnUiThread { onError("HTTP $code: $resp") }
                        return@Thread
                    }

                    val json = org.json.JSONObject(resp)
                    val content = json.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                    if (content.isNullOrBlank()) {
                        runOnUiThread { onError("Respuesta vacía") }
                    } else {
                        runOnUiThread { onResult(content.trim()) }
                    }
                    return@Thread
                } catch (e: Exception) {
                    if (attempt >= 3) {
                        runOnUiThread { onError(e.message ?: "excepción") }
                        return@Thread
                    }
                    Thread.sleep(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(8000L)
                }
            }
        }.start()
    }

    private fun runOnUiThread(block: () -> Unit) {
        if (!isAdded) return
        activity?.runOnUiThread { block() }
    }

    private fun addMessage(role: String, text: String) {
        messages.add(ChatMessage(role, text))
        chatAdapter.submitList(messages.toList())
        // Scroll to bottom
        view?.findViewById<RecyclerView>(R.id.chatRecycler)?.post {
            view?.findViewById<RecyclerView>(R.id.chatRecycler)?.scrollToPosition(messages.lastIndex)
        }
    }

    private fun restoreProviderSelection(
        group: com.google.android.material.chip.ChipGroup?,
        openAiChip: com.google.android.material.chip.Chip?,
        geminiChip: com.google.android.material.chip.Chip?,
        localChip: com.google.android.material.chip.Chip?
    ) {
        val provider = requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
            .getString("ai_provider", "local")
        when (provider) {
            "gemini" -> geminiChip?.isChecked = true
            "openai" -> openAiChip?.isChecked = true
            else -> localChip?.isChecked = true
        }
    }

    // ==== Gemini minimal call ====
    private fun callGeminiApi(prompt: String, apiKey: String, onResult: (String) -> Unit, onError: (String) -> Unit) {
        Thread {
            var attempt = 0
            var backoffMs = 2000L
            while (attempt < 3) {
                attempt++
                try {
                    val url = java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=" + java.net.URLEncoder.encode(apiKey, "UTF-8"))
                    val conn = (url.openConnection() as javax.net.ssl.HttpsURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                        connectTimeout = 15000
                        readTimeout = 30000
                    }
                    val body = org.json.JSONObject().apply {
                        put("contents", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply {
                                put("parts", org.json.JSONArray().apply {
                                    put(org.json.JSONObject().apply { put("text", prompt) })
                                })
                            })
                        })
                        put("generationConfig", org.json.JSONObject().apply { put("temperature", 0.2) })
                    }
                    conn.outputStream.writer(Charsets.UTF_8).use { it.write(body.toString()) }

                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val resp = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    conn.disconnect()

                    if (code == 429) {
                        val retryAfter = conn.getHeaderField("Retry-After")?.toLongOrNull()?.times(1000) ?: backoffMs
                        val until = System.currentTimeMillis() + retryAfter
                        requireContext().getSharedPreferences("kolki_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putLong("gemini_cooldown_until_ms", until).apply()
                        if (attempt < 3) {
                            Thread.sleep(retryAfter)
                            backoffMs = (backoffMs * 2).coerceAtMost(8000L)
                            continue
                        }
                        runOnUiThread { onError("Has excedido el cupo temporal (Gemini). Intenta en unos minutos.") }
                        return@Thread
                    }
                    if (code !in 200..299) {
                        runOnUiThread { onError("HTTP $code: $resp") }
                        return@Thread
                    }
                    val json = org.json.JSONObject(resp)
                    val text = json.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text")
                    if (text.isNullOrBlank()) {
                        runOnUiThread { onError("Respuesta vacía") }
                    } else {
                        runOnUiThread { onResult(text.trim()) }
                    }
                    return@Thread
                } catch (e: Exception) {
                    if (attempt >= 3) {
                        runOnUiThread { onError(e.message ?: "excepción") }
                        return@Thread
                    }
                    Thread.sleep(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(15000L)
                }
            }
        }.start()
    }
}
