package com.example.jarvis

import android.content.Context
import org.json.JSONObject

/** Explicit, on-device learning. No network, surveillance or background collection. */
object JarvisLearningBrain {
    private const val PREFS = "jarvis_learning_brain_v1"
    private const val MEMORY = "learned_responses"
    private const val MAX_ENTRIES = 300

    private fun normalize(value: String): String = value.trim()
        .lowercase(java.util.Locale.ROOT)
        .replace(Regex("\\s+"), " ")

    /** Returns true only if an explicitly taught fact was stored. */
    @Synchronized
    fun teach(context: Context, phrase: String, response: String): Boolean {
        val key = normalize(phrase)
        val answer = response.trim()
        if (key.length !in 2..120 || answer.length !in 1..500) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return try {
            val data = JSONObject(prefs.getString(MEMORY, "{}") ?: "{}")
            if (!data.has(key) && data.length() >= MAX_ENTRIES) return false
            data.put(key, answer)
            prefs.edit().putString(MEMORY, data.toString()).commit()
        } catch (_: Exception) { false }
    }

    /** Exact matching avoids accidentally treating similar commands as the same. */
    fun recall(context: Context, phrase: String): String? {
        val key = normalize(phrase)
        if (key.isEmpty()) return null
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            JSONObject(prefs.getString(MEMORY, "{}") ?: "{}")
                .optString(key).takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    @Synchronized
    fun forget(context: Context, phrase: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return try {
            val data = JSONObject(prefs.getString(MEMORY, "{}") ?: "{}")
            data.remove(normalize(phrase))
            prefs.edit().putString(MEMORY, data.toString()).commit()
        } catch (_: Exception) { false }
    }

    @Synchronized
    fun clearAll(context: Context): Boolean = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().remove(MEMORY).commit()

    fun learnedCount(context: Context): Int = try {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        JSONObject(prefs.getString(MEMORY, "{}") ?: "{}").length()
    } catch (_: Exception) { 0 }
}
