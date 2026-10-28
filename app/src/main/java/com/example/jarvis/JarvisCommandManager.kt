package com.example.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.util.Locale

object JarvisCommandManager {

    fun execute(
        context: Context,
        input: String
    ): String {

        val command = input
            .lowercase(Locale.ROOT)
            .trim()

        if (command.isBlank()) {
            return "जी सर, अपना आदेश बताइए।"
        }

        val accessibility = JarvisAccessibility.active

        // HOME
        if (
            command.contains("होम") ||
            command.contains("home")
        ) {
            return if (accessibility?.goHome() == true) {
                "जी सर, होम स्क्रीन खोल दी है।"
            } else {
                "सर, मोबाइल कंट्रोल परमिशन चालू करें।"
            }
        }

        // BACK
        if (
            command.contains("वापस") ||
            command.contains("पीछे जाओ") ||
            command == "back"
        ) {
            return if (accessibility?.goBack() == true) {
                "जी सर, वापस आ गया।"
            } else {
                "सर, मोबाइल कंट्रोल उपलब्ध नहीं है।"
            }
        }

        // RECENT APPS
        if (
            command.contains("रीसेंट") ||
            command.contains("recent")
        ) {
            return if (
                accessibility?.openRecentApps() == true
            ) {
                "जी सर, रीसेंट ऐप्स खोल दिए।"
            } else {
                "सर, मोबाइल कंट्रोल परमिशन चालू करें।"
            }
        }

        // NOTIFICATIONS
        if (
            command.contains("नोटिफिकेशन") ||
            command.contains("notification")
        ) {
            return if (
                accessibility?.openNotifications() == true
            ) {
                "जी सर, नोटिफिकेशन खोल दिए।"
            } else {
                "सर, यह कमांड उपलब्ध नहीं है।"
            }
        }

        // QUICK SETTINGS
        if (
            command.contains("क्विक सेटिंग") ||
            command.contains("quick settings")
        ) {
            return if (
                accessibility?.openQuickSettings() == true
            ) {
                "जी सर, क्विक सेटिंग्स खोल दी हैं।"
            } else {
                "सर, क्विक सेटिंग्स नहीं खुल सकीं।"
            }
        }

        // SCROLL DOWN
        if (
            command.contains("नीचे स्क्रॉल") ||
            command.contains("scroll down")
        ) {
            return if (accessibility?.scroll(false) == true) {
                "जी सर, नीचे स्क्रॉल कर दिया।"
            } else {
                "सर, स्क्रीन स्क्रॉल नहीं हो सकी।"
            }
        }

        // SCROLL UP
        if (
            command.contains("ऊपर स्क्रॉल") ||
            command.contains("scroll up")
        ) {
            return if (accessibility?.scroll(true) == true) {
                "जी सर, ऊपर स्क्रॉल कर दिया।"
            } else {
                "सर, स्क्रीन स्क्रॉल नहीं हो सकी।"
            }
        }

        // WIFI SETTINGS
        if (
            command.contains("वाईफाई") ||
            command.contains("वाई फाई") ||
            command.contains("wifi") ||
            command.contains("wi-fi")
        ) {
            return openSettings(
                context,
                Settings.ACTION_WIFI_SETTINGS,
                "जी सर, वाईफाई सेटिंग्स खोल दी हैं।"
            )
        }

        // BLUETOOTH SETTINGS
        if (
            command.contains("ब्लूटूथ") ||
            command.contains("bluetooth")
        ) {
            return openSettings(
                context,
                Settings.ACTION_BLUETOOTH_SETTINGS,
                "जी सर, ब्लूटूथ सेटिंग्स खोल दी हैं।"
            )
        }

        // CAMERA
        if (
            command.contains("कैमरा") ||
            command.contains("camera")
        ) {
            return openApp(
                context,
                "com.android.camera",
                "camera"
            )
        }

        // YOUTUBE
        if (
            command.contains("यूट्यूब") ||
            command.contains("youtube")
        ) {
            return openApp(
                context,
                "com.google.android.youtube",
                "YouTube"
            )
        }

        // WHATSAPP
        if (
            command.contains("व्हाट्सएप") ||
            command.contains("व्हाट्सप्प") ||
            command.contains("whatsapp")
        ) {
            return openApp(
                context,
                "com.whatsapp",
                "WhatsApp"
            )
        }

        // CHROME
        if (
            command.contains("क्रोम") ||
            command.contains("chrome")
        ) {
            return openApp(
                context,
                "com.android.chrome",
                "Chrome"
            )
        }

        // PLAY STORE APP SEARCH
        if (
            command.contains("डाउनलोड") ||
            command.contains("download") ||
            command.contains("इंस्टॉल") ||
            command.contains("install")
        ) {

            val appName = command
                .replace("डाउनलोड", "")
                .replace("करो", "")
                .replace("करना", "")
                .replace("इंस्टॉल", "")
                .replace("download", "")
                .replace("install", "")
                .replace("app", "")
                .replace("ऐप", "")
                .trim()

            if (appName.isBlank()) {
                return "सर, किस ऐप को डाउनलोड करना है?"
            }

            return searchPlayStore(
                context,
                appName
            )
        }

        // TAP VISIBLE LABEL
        if (
            command.startsWith("टैप करो ") ||
            command.startsWith("tap ")
        ) {

            val label = command
                .removePrefix("टैप करो ")
                .removePrefix("tap ")
                .trim()

            if (label.isBlank()) {
                return "सर, किस बटन पर टैप करना है?"
            }

            return if (
                accessibility?.tapLabel(label) == true
            ) {
                "जी सर, बटन पर टैप कर दिया।"
            } else {
                "सर, यह बटन स्क्रीन पर नहीं मिला।"
            }
        }

        // OFFLINE AI BRAIN
        return JarvisOfflineBrain.reply(command)
    }

    private fun openApp(
        context: Context,
        packageName: String,
        appName: String
    ): String {

        return try {

            val intent = context.packageManager
                .getLaunchIntentForPackage(packageName)

            if (intent != null) {

                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )

                context.startActivity(intent)

                "जी सर, $appName खोल दिया है।"

            } else {

                searchPlayStore(context, appName)
            }

        } catch (e: Exception) {

            "सर, $appName नहीं खुल सका।"
        }
    }

    private fun openSettings(
        context: Context,
        action: String,
        response: String
    ): String {

        return try {

            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)

            response

        } catch (e: Exception) {

            "सर, यह सेटिंग नहीं खुल सकी।"
        }
    }

    private fun searchPlayStore(
        context: Context,
        appName: String
    ): String {

        return try {

            val uri = Uri.parse(
                "market://search?q=" +
                    Uri.encode(appName) +
                    "&c=apps"
            )

            val intent = Intent(
                Intent.ACTION_VIEW,
                uri
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {

                context.startActivity(intent)

            } catch (e: Exception) {

                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        "https://play.google.com/store/search?q=" +
                            Uri.encode(appName) +
                            "&c=apps"
                    )
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(webIntent)
            }

            "जी सर, $appName को डाउनलोड करने के लिए स्टोर खोल दिया है।"

        } catch (e: Exception) {

            "सर, अभी प्ले स्टोर नहीं खुल सका।"
        }
    }
}
