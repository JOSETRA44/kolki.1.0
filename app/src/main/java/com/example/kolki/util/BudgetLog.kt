package com.example.kolki.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object BudgetLog {
    private const val PREFS = "kolki_prefs"
    private const val KEY = "budget_log"

    data class Entry(
        val timeMs: Long,
        val type: String,
        val message: String
    )

    fun addEvent(context: Context, type: String, message: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val arrStr = prefs.getString(KEY, "[]") ?: "[]"
            val arr = try { JSONArray(arrStr) } catch (_: Throwable) { JSONArray() }
            val obj = JSONObject().apply {
                put("timeMs", System.currentTimeMillis())
                put("type", type)
                put("message", message)
            }
            arr.put(obj)
            prefs.edit().putString(KEY, arr.toString()).apply()
        } catch (_: Exception) { }
    }

    fun getEvents(context: Context): List<Entry> {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val arrStr = prefs.getString(KEY, "[]") ?: "[]"
            val arr = try { JSONArray(arrStr) } catch (_: Throwable) { JSONArray() }
            val out = ArrayList<Entry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(Entry(
                    timeMs = o.optLong("timeMs"),
                    type = o.optString("type"),
                    message = o.optString("message")
                ))
            }
            out.sortedByDescending { it.timeMs }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
