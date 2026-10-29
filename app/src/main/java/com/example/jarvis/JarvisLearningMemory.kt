package com.example.jarvis

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/** Local, opt-in learning. Never reads other apps or sensitive phone data. */
object JarvisLearningMemory {
    private const val PREFS = "jarvis_learning_v1"
    private const val ENTRIES = "entries"
    private const val MAX_ENTRIES = 200

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .trim().replace(Regex("\\s+"), " ")

    @Synchronized
    fun teach(context: Context, question: String, answer: String): Boolean {
        val q = normalize(question)
        val a = answer.trim()
        if (q.length !in 3..160 || a.length !in 1..500) return false
        val forbidden = listOf("otp", "password", "पासवर्ड", "पिन", "pin", "cvv", "api key", "एपीआई की", "upi")
        if (forbidden.any { q.contains(it) || a.lowercase(Locale.ROOT).contains(it) }) return false
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val data = JSONObject(prefs.getString(ENTRIES, "{}") ?: "{}")
            if (!data.has(q) && data.length() >= MAX_ENTRIES) return false
            data.put(q, a)
            prefs.edit().putString(ENTRIES, data.toString()).commit()
        } catch (_: Exception) { false }
    }

    @Synchronized
    fun recall(context: Context, question: String): String? = try {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val data = JSONObject(prefs.getString(ENTRIES, "{}") ?: "{}")
        data.optString(normalize(question)).takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    @Synchronized
    fun forget(context: Context, question: String): Boolean = try {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val data = JSONObject(prefs.getString(ENTRIES, "{}") ?: "{}")
        val q = normalize(question)
        if (!data.has(q)) false else {
            data.remove(q)
            prefs.edit().putString(ENTRIES, data.toString()).commit()
        }
    } catch (_: Exception) { false }
}
