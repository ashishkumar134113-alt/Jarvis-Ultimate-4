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
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    // Saving a key is local-only; it does not contact Gemini.
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
            .edit().remove(API_KEY_NAME).apply()
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

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEYSTORE
        )
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
            val manager = context.getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager
            val network = manager.activeNetwork ?: return false
            manager.getNetworkCapabilities(network)?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) == true
        } catch (_: Exception) {
            false
        }
    }

    // Ask Gemini which models this particular API key can use.
    private fun findModel(apiKey: String): String? {
        val request = Request.Builder()
            .url("$BASE_URL/models?pageSize=100")
            .header("x-goog-api-key", apiKey)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw GeminiHttpException(response.code)
            }
            val models = JSONObject(response.body?.string().orEmpty())
                .optJSONArray("models") ?: return null
            val usable = mutableListOf<String>()
            for (i in 0 until models.length()) {
                val model = models.optJSONObject(i) ?: continue
                val methods = model.optJSONArray("supportedGenerationMethods") ?: continue
                var supportsGenerate = false
                for (j in 0 until methods.length()) {
                    if (methods.optString(j) == "generateContent") {
                        supportsGenerate = true
                        break
                    }
                }
                val name = model.optString("name")
                if (supportsGenerate && name.startsWith("models/gemini-")) {
                    usable.add(name)
                }
            }
            val preferred = listOf(
                "models/gemini-2.5-flash",
                "models/gemini-2.5-flash-lite",
                "models/gemini-2.0-flash"
            )
            return (preferred.filter { usable.contains(it) } +
                usable.filter { it.contains("flash") } +
                usable).distinct().joinToString("|")
        }
    }

    private class GeminiHttpException(val status: Int) : Exception()

    suspend fun ask(context: Context, question: String): String =
        withContext(Dispatchers.IO) {
            val apiKey = readApiKey(context)
            if (apiKey.isNullOrBlank()) {
                return@withContext JarvisOfflineBrain.reply(question)
            }
            if (!isInternetAvailable(context)) {
                return@withContext "जी सर, इंटरनेट कनेक्शन जाँचिए।"
            }
            try {
                val modelNames = findModel(apiKey)
                    ?.split("|")?.filter { it.isNotBlank() }.orEmpty()
                if (modelNames.isEmpty()) {
                    return@withContext "जी सर, इस API Key के लिए कोई उपलब्ध Gemini टेक्स्ट मॉडल नहीं मिला।"
                }

                val body = JSONObject()
                    .put("contents", JSONArray().put(
                        JSONObject().put("role", "user").put(
                            "parts", JSONArray().put(
                                JSONObject().put(
                                    "text",
                                    "तुम JARVIS हो। हिंदी में सरल, उपयोगी और सुरक्षित जवाब दो।\n\nप्रश्न: $question"
                                )
                            )
                        )
                    ))
                    .put("generationConfig", JSONObject()
                        .put("temperature", 0.7)
                        .put("maxOutputTokens", 800))

                var lastStatus = 404
                for (model in modelNames) {
                    val request = Request.Builder()
                        .url("$BASE_URL/$model:generateContent")
                        .header("x-goog-api-key", apiKey)
                        .post(body.toString().toRequestBody(
                            "application/json; charset=utf-8".toMediaType()
                        ))
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.code == 404) {
                            lastStatus = 404
                            return@use
                        }
                        if (!response.isSuccessful) {
                            throw GeminiHttpException(response.code)
                        }
                        val json = JSONObject(response.body?.string().orEmpty())
                        val parts = json.optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
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
                        return@withContext answer.ifBlank {
                            "जी सर, इस बार जवाब नहीं मिला। फिर पूछिए।"
                        }
                    }
                }
                throw GeminiHttpException(lastStatus)
            } catch (error: GeminiHttpException) {
                when (error.status) {
                    400 -> "जी सर, Gemini अनुरोध सही नहीं है। त्रुटि 400।"
                    401, 403 -> "जी सर, Gemini API Key या उसकी अनुमति जाँचें। त्रुटि ${error.status}।"
                    404 -> "जी सर, Gemini मॉडल उपलब्ध नहीं मिला। त्रुटि 404।"
                    429 -> "जी सर, Gemini की इस्तेमाल सीमा पूरी हो गई है। त्रुटि 429।"
                    500, 502, 503, 504 ->
                        "जी सर, Gemini सर्वर अभी उपलब्ध नहीं है। थोड़ी देर बाद कोशिश करें। त्रुटि ${error.status}।"
                    else -> "जी सर, Gemini ने त्रुटि ${error.status} लौटाई।"
                }
            } catch (_: Exception) {
                "जी सर, Gemini कनेक्शन में समस्या है। इंटरनेट जाँचकर फिर कोशिश करें।"
            }
        }

    // Compatibility with older backend settings.
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
