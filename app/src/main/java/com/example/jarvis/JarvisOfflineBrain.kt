package com.example.jarvis

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object JarvisOfflineBrain {
    // Existing calls continue to compile. Learning requires the Context overload below.
    fun reply(command: String): String = basicReply(command)

    /** Use this overload in voice service to enable persistent learning. */
    fun reply(context: Context, command: String): String {
        val raw = command.trim()
        val text = raw.lowercase(Locale.ROOT)
        if (text.startsWith("याद रखो ") || text.startsWith("सीखो ") || text.startsWith("learn ")) {
            val payload = raw.substringAfter(' ').trim()
            val separator = when {
                payload.contains(" = ") -> " = "
                payload.contains(" मतलब ") -> " मतलब "
                else -> null
            }
            if (separator == null) return "सर, ऐसे सिखाएँ: याद रखो मेरा पसंदीदा ऐप = म्यूजिक ऐप"
            val question = payload.substringBefore(separator).trim()
            val answer = payload.substringAfter(separator).trim()
            return if (JarvisLearningMemory.teach(context, question, answer))
                "जी सर, आपकी बताई बात मैंने इस फोन में याद रख ली है।"
            else "सर, यह बात सेव नहीं हुई। निजी PIN, OTP, पासवर्ड या API Key याद नहीं रखता; सवाल छोटा रखें।"
        }
        if (text.startsWith("भूल जाओ ") || text.startsWith("forget ")) {
            val question = raw.substringAfter(' ').trim()
            return if (JarvisLearningMemory.forget(context, question))
                "जी सर, वह सीखी हुई बात हटा दी है।"
            else "सर, उस नाम से कोई सीखी हुई बात नहीं मिली।"
        }
        JarvisLearningMemory.recall(context, raw)?.let { return it }
        return basicReply(raw)
    }

    private fun basicReply(command: String): String {
        val text = command.lowercase(Locale.ROOT).trim()
        if (text.isBlank()) return "जी सर, बताइए।"
        if (listOf("तुम कौन हो", "आप कौन हो", "who are you").any(text::contains))
            return "जी सर, मैं जार्विस अल्टीमेट 4.0 हूँ। आपका पर्सनल असिस्टेंट।"
        if (listOf("तुम्हारा नाम", "आपका नाम", "what is your name", "your name").any(text::contains))
            return "सर, मेरा नाम जार्विस है।"
        if (listOf("नमस्ते", "hello", "हेलो", "good morning").any(text::contains))
            return "नमस्ते सर, मैं आपकी क्या सहायता कर सकता हूँ?"
        if (listOf("कैसे हो", "how are you").any(text::contains))
            return "मैं ठीक हूँ सर। सभी सिस्टम तैयार हैं।"
        if (listOf("भारत की राजधानी", "capital of india").any(text::contains))
            return "भारत की राजधानी नई दिल्ली है।"
        if (listOf("आज की तारीख", "तारीख बताओ", "today date", "what is the date").any(text::contains))
            return "सर, आज की तारीख ${SimpleDateFormat("dd MMMM yyyy", Locale("hi", "IN")).format(Date())} है।"
        if (listOf("कितने बजे", "समय बताओ", "what time").any(text::contains) || text == "time")
            return "सर, अभी ${SimpleDateFormat("hh:mm a", Locale("hi", "IN")).format(Date())} बजे हैं।"
        if (listOf("धन्यवाद", "शुक्रिया", "thank you", "thanks").any(text::contains))
            return "आपका स्वागत है सर।"
        if (listOf("क्या कर सकते हो", "help", "कमांड बताओ").any(text::contains))
            return "सर, मैं मोबाइल कमांड में सहायता कर सकता हूँ। मुझे सिखाने के लिए बोलें: याद रखो सवाल = जवाब।"
        return "सर, यह बात अभी मेरे ऑफलाइन ज्ञान में नहीं है। आप मुझे सिखा सकते हैं: याद रखो सवाल = जवाब।"
    }

    fun getBatteryPercentage(context: Context): Int {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level < 0 || scale <= 0) -1 else level * 100 / scale
    }
}
