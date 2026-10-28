package com.example.jarvis

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class JarvisApiSettings : Activity() {

    private lateinit var urlInput: EditText
    private lateinit var statusText: TextView
    private lateinit var testButton: Button
    private lateinit var saveButton: Button

    private val screenJob = Job()

    private val scope = CoroutineScope(
        Dispatchers.Main + screenJob
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var verifiedUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createInterface()
    }

    private fun createInterface() {

        val cyan = Color.rgb(0, 229, 255)
        val white = Color.WHITE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 36, 32, 36)
            setBackgroundColor(Color.rgb(5, 12, 22))
        }

        val scroll = ScrollView(this).apply {
            addView(root)
        }

        setContentView(scroll)

        fun heading(value: String, size: Float) {
            root.addView(TextView(this).apply {
                text = value
                textSize = size
                setTextColor(cyan)
                gravity = Gravity.CENTER
                setPadding(0, 12, 0, 20)
            })
        }

        fun button(value: String): Button {
            return Button(this).apply {
                text = value
                isAllCaps = false
                setTextColor(white)
                backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        Color.rgb(12, 65, 85)
                    )

                root.addView(
                    this,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }

        heading("J.A.R.V.I.S", 30f)
        heading("SECURE AI SETTINGS", 21f)

        root.addView(TextView(this).apply {
            text = "अपना HTTPS Backend URL नीचे डालें। API Key यहाँ नहीं डालनी है।"
            textSize = 15f
            setTextColor(white)
            setPadding(0, 12, 0, 18)
        })

        urlInput = EditText(this).apply {
            hint = "https://your-backend.example.com"
            setHintTextColor(Color.GRAY)
            setTextColor(white)
            setSingleLine(true)

            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_URI

            setText(
                JarvisOnlineBrain.getBackendUrl(
                    this@JarvisApiSettings
                )
            )
        }

        root.addView(
            urlInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        statusText = TextView(this).apply {
            text = "Backend अभी टेस्ट नहीं हुआ है।"
            textSize = 14f
            setTextColor(white)
            setPadding(0, 20, 0, 20)
        }

        root.addView(statusText)

        testButton = button("TEST CONNECTION")
        saveButton = button("SAVE & ACTIVATE")

        saveButton.isEnabled = false

        testButton.setOnClickListener {
            testConnection()
        }

        saveButton.setOnClickListener {

            val currentUrl = urlInput.text.toString()
                .trim()
                .trimEnd('/')

            if (currentUrl != verifiedUrl) {
                statusText.text =
                    "URL बदल गया है। पहले दोबारा Test करें।"
                saveButton.isEnabled = false
                return@setOnClickListener
            }

            val saved = JarvisOnlineBrain.saveBackendUrl(
                this,
                currentUrl
            )

            if (saved) {
                statusText.text =
                    "JARVIS Online AI Backend Activated"

                Toast.makeText(
                    this,
                    "Backend URL Saved",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                statusText.text =
                    "Backend URL सही नहीं है।"
            }
        }

        button("BACK TO JARVIS").setOnClickListener {
            finish()
        }
    }

    private fun testConnection() {

        val url = urlInput.text.toString()
            .trim()
            .trimEnd('/')

        verifiedUrl = ""
        saveButton.isEnabled = false

        if (!url.startsWith("https://")) {
            statusText.text =
                "केवल HTTPS Backend URL स्वीकार है।"
            return
        }

        testButton.isEnabled = false
        statusText.text =
            "Backend Connection टेस्ट हो रहा है..."

        scope.launch {

            val success = withContext(Dispatchers.IO) {

                try {
                    val request = Request.Builder()
                        .url("$url/health")
                        .get()
                        .build()

                    client.newCall(request).execute().use { response ->

                        if (!response.isSuccessful) {
                            return@withContext false
                        }

                        val body = response.body
                            ?.string()
                            .orEmpty()

                        val json = JSONObject(body)

                        json.optString("status")
                            .equals("online", ignoreCase = true)
                    }

                } catch (_: Exception) {
                    false
                }
            }

            testButton.isEnabled = true

            if (success) {
                verifiedUrl = url
                saveButton.isEnabled = true

                statusText.text =
                    "Backend Connected! अब Save & Activate दबाएँ।"
            } else {
                statusText.text =
                    "Connection Failed. Backend URL या /health जाँचें।"
            }
        }
    }

    override fun onDestroy() {
        screenJob.cancel()
        client.dispatcher.cancelAll()
        super.onDestroy()
    }
}
