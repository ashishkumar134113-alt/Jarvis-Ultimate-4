package com.example.jarvis

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object JarvisOnlineBrain {

    /*
     * JARVIS ULTIMATE 4.0
     *
     * Online AI Backend
     *
     * Never put an API key inside this APK.
     */

    private const val PREFS_NAME = "jarvis_online"

    private const val BACKEND_KEY = "backend_url"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // SAVE BACKEND URL

    fun saveBackendUrl(
        context: Context,
        url: String
    ): Boolean {

        val cleanUrl = url.trim().trimEnd('/')

        if (!isValidBackendUrl(cleanUrl)) {
            return false
        }

        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putString(BACKEND_KEY, cleanUrl)
            .apply()

        return true
    }

    // GET BACKEND URL

    fun getBackendUrl(context: Context): String {

        return context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(BACKEND_KEY, "") ?: ""
    }

    // VALIDATE URL

    private fun isValidBackendUrl(url: String): Boolean {

        val parsed = url.toHttpUrlOrNullSafe()
            ?: return false

        return parsed.scheme == "https" &&
            parsed.host.isNotBlank() &&
            parsed.username.isEmpty() &&
            parsed.password.isEmpty() &&
            parsed.query == null &&
            parsed.fragment == null
    }

    private fun String.toHttpUrlOrNullSafe():
        okhttp3.HttpUrl? {

        return try {
            okhttp3.HttpUrl.Companion.toHttpUrlOrNull(this)
        } catch (_: Exception) {
            null
        }
    }

    // CHECK INTERNET

    fun isInternetAvailable(context: Context): Boolean {

        val manager = context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as? ConnectivityManager ?: return false

        val network = manager.activeNetwork ?: return false

        val capabilities = manager.getNetworkCapabilities(
            network
        ) ?: return false

        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) &&
            capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
    }

    // ASK ONLINE AI

    suspend fun ask(
        context: Context,
        question: String
    ): String = withContext(Dispatchers.IO) {

        val cleanQuestion = question.trim()

        if (cleanQuestion.isBlank()) {
            return@withContext "सर, अपना सवाल बताइए।"
        }

        val backend = getBackendUrl(context)

        if (backend.isBlank()) {
            return@withContext offlineReply(
                cleanQuestion,
                "ऑनलाइन एआई सर्वर अभी सेट नहीं है।"
            )
        }

        if (!isInternetAvailable(context)) {
            return@withContext offlineReply(
                cleanQuestion,
                "इंटरनेट उपलब्ध नहीं है।"
            )
        }

        try {

            val json = JSONObject().apply {

                put("message", cleanQuestion)

                put("language", "hi")

                put("assistant", "JARVIS")
            }

            val body = json.toString().toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )

            val request = Request.Builder()
                .url("$backend/chat")
                .post(body)
                .header(
                    "Accept",
                    "application/json"
                )
                .build()

            client.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {

                    return@withContext offlineReply(
                        cleanQuestion,
                        "एआई सर्वर ने जवाब नहीं दिया।"
                    )
                }

                val responseText = response.body
                    ?.string()
                    .orEmpty()

                val result = JSONObject(responseText)

                val answer = result.optString(
                    "reply",
                    ""
                ).trim()

                if (answer.isNotBlank()) {
                    return@withContext answer
                }

                return@withContext offlineReply(
                    cleanQuestion,
                    "एआई सर्वर से खाली जवाब मिला।"
                )
            }

        } catch (_: Exception) {

            return@withContext offlineReply(
                cleanQuestion,
                "ऑनलाइन कनेक्शन में समस्या आई है।"
            )
        }
    }

    // OFFLINE FALLBACK

    private fun offlineReply(
        question: String,
        reason: String
    ): String {

        val offline = JarvisOfflineBrain.reply(question)

        return if (
            offline.contains("ऑफलाइन ज्ञान में नहीं")
        ) {
            "सर, $reason कृपया बाद में कोशिश करें।"
        } else {
            offline
        }
    }
}
