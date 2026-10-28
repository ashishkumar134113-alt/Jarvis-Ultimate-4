package com.example.jarvis

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object JarvisOnlineBrain {

    private const val PREFS = "jarvis_gemini"
    private const val API_KEY_NAME = "gemini_api_key"
    private const val BACKEND_PREFS = "jarvis_online"
    private const val BACKEND_KEY = "backend_url"

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "JarvisGeminiKeyEncryption"

    private const val MODEL = "gemini-3.8-flash"
    private const val API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private val client = OkHttpClient()

    fun saveApiKey(context: Context, apiKey: String): Boolean {
        val key = apiKey.trim()
        if (key.length < 10) return false

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

            val encrypted = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
            val saved = cipher.iv + encrypted

            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(API_KEY_NAME, Base64.encodeToString(saved, Base64.NO_WRAP))
                .apply()

            true
        } catch (_: Exception) {
            false
        }
    }

    fun hasApiKey(context: Context): Boolean =
        !readApiKey(context).isNullOrBlank()

    fun clearApiKey(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(API_KEY_NAME)
            .apply()
    }

    private fun readApiKey(context: Context): String? {
        return try {
            val saved = context.getSharedPreferences(
                PREFS, Context.MODE_PRIVATE
            ).getString(API_KEY_NAME, null) ?: return null

            val bytes = Base64.decode(saved, Base64.NO_WRAP)
            if (bytes.size <= 12) return null

            val iv = bytes.copyOfRange(0, 12)
            val encrypted = bytes.copyOfRange(12, bytes.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, iv)
            )

            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE)
        store.load(null)

        val existing = store.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(true)
                .build()
        )

        return generator.generateKey()
    }

    fun isInternetAvailable(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager

            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network)

            capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) == true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun ask(context: Context, question: String): String =
        withContext(Dispatchers.IO) {
            val apiKey = readApiKey(context)

            if (apiKey.isNullOrBlank()) {
                return@withContext JarvisOfflineBrain.reply(question)
            }

            try {
                val body = JSONObject()
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject()
                                .put("role", "user")
                                .put(
                                    "parts",
                                    JSONArray().put(
                                        JSONObject().put(
                                            "text",
                                            "तुम JARVIS हो। हिंदी में सरल, उपयोगी और सुरक्षित जवाब दो।\n\nप्रश्न: $question"
                                        )
                                    )
                                )
                        )
                    )
                    .put(
                        "generationConfig",
                        JSONObject()
                            .put("temperature", 0.7)
                            .put("maxOutputTokens", 800)
                    )

                val request = Request.Builder()
                    .url(API_URL)
                    .addHeader("x-goog-api-key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(
                        body.toString().toRequestBody(
                            "application/json; charset=utf-8".toMediaType()
                        )
                    )
                    .build()

                client.newCall(request).execute().use { response ->
                    val result = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        return@withContext when (response.code) {
                            400, 403 -> "जी सर, Gemini API Key या उसकी अनुमति जाँचें।"
                            429 -> "जी सर, Gemini की इस्तेमाल सीमा पूरी हो गई है।"
                            else -> "जी सर, Gemini से कनेक्शन नहीं हुआ। त्रुटि कोड ${response.code}।"
                        }
                    }

                    val json = JSONObject(result)
                    val candidates = json.optJSONArray("candidates")
                    val parts = candidates
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")

                    val answer = buildString {
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val text = parts.optJSONObject(i)
                                    ?.optString("text")
                                    .orEmpty()

                                if (text.isNotBlank()) {
                                    if (isNotEmpty()) append("\n")
                                    append(text)
                                }
                            }
                        }
                    }

                    answer.ifBlank {
                        "जी सर, इस बार जवाब नहीं मिला। फिर से पूछिए।"
                    }
                }
            } catch (_: Exception) {
                "जी सर, इंटरनेट या Gemini कनेक्शन में समस्या है।"
            }
        }

    // पुराने प्रोजेक्ट के backend URL वाले कोड से compatibility.
    fun saveBackendUrl(context: Context, url: String): Boolean {
        val clean = url.trim()
        if (!clean.startsWith("https://")) return false

        context.getSharedPreferences(BACKEND_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(BACKEND_KEY, clean)
            .apply()

        return true
    }

    fun getBackendUrl(context: Context): String =
        context.getSharedPreferences(BACKEND_PREFS, Context.MODE_PRIVATE)
            .getString(BACKEND_KEY, "") ?: ""
}
