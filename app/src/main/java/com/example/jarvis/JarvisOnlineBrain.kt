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
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
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

    private const val MODEL = "gemini-2.5-flash"
    private const val API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    // Saving is local only: it never calls the Gemini server.
    fun saveApiKey(context: Context, apiKey: String): Boolean {
        val key = apiKey.trim()
        if (key.length < 10) return false
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val saved = cipher.iv + cipher.doFinal(key.toByteArray(Charsets.UTF_8))
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(API_KEY_NAME, Base64.encodeToString(saved, Base64.NO_WRAP))
                .commit()
        } catch (_: Exception) {
            false
        }
    }

    fun hasApiKey(context: Context): Boolean = !readApiKey(context).isNullOrBlank()

    fun clearApiKey(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(API_KEY_NAME).commit()
    }

    private fun readApiKey(context: Context): String? {
        return try {
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(API_KEY_NAME, null) ?: return null
            val bytes = Base64.decode(saved, Base64.NO_WRAP)
            if (bytes.size <= 12) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, bytes.copyOfRange(0, 12))
            )
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE)
        store.load(null)
        val existing = store.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun isInternetAvailable(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
            val network = manager.activeNetwork ?: return false
            manager.getNetworkCapabilities(network)?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) == true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun ask(context: Context, question: String): String = withContext(Dispatchers.IO) {
        val apiKey = readApiKey(context)
        if (apiKey.isNullOrBlank()) {
            return@withContext JarvisOfflineBrain.reply(question)
        }
        if (!isInternetAvailable(context)) {
            return@withContext "जी सर, इंटरनेट उपलब्ध नहीं है।"
        }

        try {
            val body = JSONObject()
                .put("contents", JSONArray().put(
                    JSONObject().put("role", "user").put("parts", JSONArray().put(
                        JSONObject().put("text", "तुम JARVIS हो। हिंदी में सरल, उपयोगी और सुरक्षित जवाब दो।\n\nप्रश्न: $question")
                    ))
                ))
                .put("generationConfig", JSONObject()
                    .put("temperature", 0.7)
                    .put("maxOutputTokens", 800))

            val request = Request.Builder()
                .url(API_URL)
                .header("x-goog-api-key", apiKey)
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val result = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext when (response.code) {
                        400 -> "जी सर, Gemini अनुरोध में समस्या है। API Key और अनुरोध जाँचें।"
                        401, 403 -> "जी सर, Gemini API Key या उसकी अनुमति सही नहीं है।"
                        404 -> "जी सर, चुना गया Gemini मॉडल उपलब्ध नहीं है। ऐप का मॉडल अपडेट करें।"
                        429 -> "जी सर, Gemini की इस्तेमाल सीमा पूरी हो गई है। थोड़ी देर बाद कोशिश करें।"
                        500, 502, 503, 504 -> "जी सर, Gemini सर्वर अभी उपलब्ध नहीं है। त्रुटि ${response.code}। थोड़ी देर बाद कोशिश करें। आपकी सेव API Key नहीं हटाई गई है।"
                        else -> "जी सर, Gemini ने त्रुटि ${response.code} लौटाई है।"
                    }
                }

                val parts = JSONObject(result).optJSONArray("candidates")
                    ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                val answer = buildString {
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val piece = parts.optJSONObject(i)?.optString("text").orEmpty()
                            if (piece.isNotBlank()) {
                                if (isNotEmpty()) append("\n")
                                append(piece)
                            }
                        }
                    }
                }
                answer.ifBlank { "जी सर, इस बार Gemini से जवाब नहीं मिला। फिर से पूछिए।" }
            }
        } catch (_: IOException) {
            "जी सर, इंटरनेट या Gemini सर्वर से संपर्क नहीं हो पाया। थोड़ी देर बाद कोशिश करें।"
        } catch (_: Exception) {
            "जी सर, Gemini का जवाब पढ़ने में समस्या हुई।"
        }
    }

    // Compatibility with older project code; not used for Gemini API requests.
    fun saveBackendUrl(context: Context, url: String): Boolean {
        val clean = url.trim()
        if (!clean.startsWith("https://")) return false
        return context.getSharedPreferences(BACKEND_PREFS, Context.MODE_PRIVATE)
            .edit().putString(BACKEND_KEY, clean).commit()
    }

    fun getBackendUrl(context: Context): String =
        context.getSharedPreferences(BACKEND_PREFS, Context.MODE_PRIVATE)
            .getString(BACKEND_KEY, "") ?: ""
}
