package com.example.jarvis

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object JarvisOfflineBrain {

    fun reply(command: String): String {

        val text = command.lowercase(
            Locale.ROOT
        ).trim()

        if (text.isBlank()) {
            return "जी सर, बताइए।"
        }

        // JARVIS IDENTITY
        if (
            text.contains("तुम कौन हो") ||
            text.contains("आप कौन हो") ||
            text.contains("who are you")
        ) {
            return "जी सर, मैं जार्विस अल्टीमेट 4.0 हूँ। आपका पर्सनल एआई असिस्टेंट।"
        }

        // JARVIS NAME
        if (
            text.contains("तुम्हारा नाम") ||
            text.contains("आपका नाम") ||
            text.contains("what is your name") ||
            text.contains("your name")
        ) {
            return "सर, मेरा नाम जार्विस है।"
        }

        // GREETING
        if (
            text.contains("नमस्ते") ||
            text.contains("hello") ||
            text.contains("हेलो") ||
            text.contains("good morning")
        ) {
            return "नमस्ते सर, मैं आपकी क्या सहायता कर सकता हूँ?"
        }

        // HOW ARE YOU
        if (
            text.contains("कैसे हो") ||
            text.contains("how are you")
        ) {
            return "मैं ठीक हूँ सर। सभी सिस्टम तैयार हैं।"
        }

        // INDIA CAPITAL
        if (
            text.contains("भारत की राजधानी") ||
            text.contains("capital of india")
        ) {
            return "भारत की राजधानी नई दिल्ली है।"
        }

        // DATE
        if (
            text.contains("आज की तारीख") ||
            text.contains("तारीख बताओ") ||
            text.contains("today date") ||
            text.contains("what is the date")
        ) {

            val format = SimpleDateFormat(
                "dd MMMM yyyy",
                Locale("hi", "IN")
            )

            return "सर, आज की तारीख ${
                format.format(Date())
            } है।"
        }

        // TIME
        if (
            text.contains("कितने बजे") ||
            text.contains("समय बताओ") ||
            text.contains("what time") ||
            text == "time"
        ) {

            val format = SimpleDateFormat(
                "hh:mm a",
                Locale("hi", "IN")
            )

            return "सर, अभी ${
                format.format(Date())
            } बजे हैं।"
        }

        // THANK YOU
        if (
            text.contains("धन्यवाद") ||
            text.contains("शुक्रिया") ||
            text.contains("thank you") ||
            text.contains("thanks")
        ) {
            return "आपका स्वागत है सर।"
        }

        // HELP
        if (
            text.contains("क्या कर सकते हो") ||
            text.contains("help") ||
            text.contains("कमांड बताओ")
        ) {
            return "सर, मैं मोबाइल ऐप खोल सकता हूँ, होम जा सकता हूँ, स्क्रीन स्क्रॉल कर सकता हूँ, सेटिंग खोल सकता हूँ और सामान्य सवालों के जवाब दे सकता हूँ।"
        }

        // ONLINE AI FALLBACK
        return "सर, यह सवाल मेरे ऑफलाइन ज्ञान में नहीं है। ऑनलाइन एआई ब्रेन से जवाब लेने के लिए इंटरनेट कनेक्शन और ऑनलाइन एआई सेटअप की जरूरत होगी।"
    }

    fun getBatteryPercentage(
        context: Context
    ): Int {

        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(
                Intent.ACTION_BATTERY_CHANGED
            )
        )

        val level = batteryStatus?.getIntExtra(
            BatteryManager.EXTRA_LEVEL,
            -1
        ) ?: -1

        val scale = batteryStatus?.getIntExtra(
            BatteryManager.EXTRA_SCALE,
            -1
        ) ?: -1

        if (level < 0 || scale <= 0) {
            return -1
        }

        return (level * 100) / scale
    }
}
