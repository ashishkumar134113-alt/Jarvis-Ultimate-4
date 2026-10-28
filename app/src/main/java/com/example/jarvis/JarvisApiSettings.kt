package com.example.jarvis

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest

class JarvisApiSettings : Activity() {

    companion object {
        private const val PREFS_NAME = "jarvis_api_security"
        private const val PIN_KEY = "settings_pin_hash"
        private const val MAX_ATTEMPTS = 3
    }

    private var failedAttempts = 0
    private var screenOpened = false

    private fun prefs(): SharedPreferences =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun pinMatches(pin: String, savedHash: String?): Boolean {
        if (savedHash.isNullOrBlank()) return false

        val entered = hashPin(pin).toByteArray(Charsets.UTF_8)
        val saved = savedHash.toByteArray(Charsets.UTF_8)

        return MessageDigest.isEqual(entered, saved)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedHash = prefs().getString(PIN_KEY, null)

        if (savedHash.isNullOrBlank()) {
            showPinSetup()
        } else {
            showPinUnlock()
        }
    }

    private fun showPinSetup() {
        val pinInput = EditText(this).apply {
            hint = "नया 4–6 अंकों का PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        val confirmInput = EditText(this).apply {
            hint = "PIN दोबारा लिखें"
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
            addView(pinInput)
            addView(confirmInput)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("JARVIS सुरक्षा PIN सेट करें")
            .setMessage("API Settings की सुरक्षा के लिए नया PIN बनाएँ।")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("PIN सेव करें", null)
            .setNegativeButton("बंद करें") { _, _ -> finish() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val pin = pinInput.text.toString()
                    val confirm = confirmInput.text.toString()

                    if (!pin.matches(Regex("\\d{4,6}"))) {
                        pinInput.error = "PIN में 4 से 6 अंक होने चाहिए"
                    } else if (pin != confirm) {
                        confirmInput.error = "दोनों PIN एक जैसे होने चाहिए"
                    } else {
                        val saved = prefs().edit()
                            .putString(PIN_KEY, hashPin(pin))
                            .commit()

                        if (saved) {
                            dialog.dismiss()
                            openApiScreen()
                        } else {
                            showMessage("PIN सेव नहीं हो पाया।")
                        }
                    }
                }
        }

        dialog.show()
    }

    private fun showPinUnlock() {
        val pinInput = EditText(this).apply {
            hint = "अपना सुरक्षा PIN डालें"
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("JARVIS सुरक्षा जाँच")
            .setMessage("API Settings खोलने के लिए PIN डालें।")
            .setView(pinInput)
            .setCancelable(false)
            .setPositiveButton("अनलॉक", null)
            .setNegativeButton("बंद करें") { _, _ -> finish() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val pin = pinInput.text.toString()
                    val savedHash = prefs().getString(PIN_KEY, null)

                    if (pinMatches(pin, savedHash)) {
                        failedAttempts = 0
                        dialog.dismiss()
                        openApiScreen()
                    } else {
                        failedAttempts++

                        if (failedAttempts >= MAX_ATTEMPTS) {
                            dialog.dismiss()
                            showMessage(
                                "तीन बार गलत PIN। API Settings बंद की जा रही हैं।"
                            )
                            finish()
                        } else {
                            pinInput.text.clear()
                            pinInput.error =
                                "गलत PIN। बचे हुए प्रयास: ${MAX_ATTEMPTS - failedAttempts}"
                        }
                    }
                }
        }

        dialog.show()
    }

    private fun openApiScreen() {
        if (screenOpened || isFinishing) return
        screenOpened = true
        showApiScreen()
    }

    private fun showApiScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 36, 32, 24)
        }

        root.addView(TextView(this).apply {
            text = "JARVIS • GEMINI API"
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        })

        root.addView(TextView(this).apply {
            text = "अपनी Gemini API Key यहाँ डालें। इसे किसी के साथ साझा न करें।"
            textSize = 15f
        })

        val apiInput = EditText(this).apply {
            hint = "Gemini API Key"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        root.addView(apiInput)

        val status = TextView(this).apply {
            text = if (JarvisOnlineBrain.hasApiKey(this@JarvisApiSettings)) {
                "API Key सेव है"
            } else {
                "API Key अभी सेव नहीं है"
            }
            textSize = 16f
            setPadding(0, 18, 0, 18)
        }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "SAVE API KEY"

            setOnClickListener {
                val key = apiInput.text.toString().trim()

                if (key.isBlank()) {
                    apiInput.error = "पहले API Key डालें"
                    return@setOnClickListener
                }

                if (
                    JarvisOnlineBrain.saveApiKey(
                        this@JarvisApiSettings,
                        key
                    )
                ) {
                    apiInput.text.clear()
                    status.text = "API Key सेव है"
                    showMessage("API Key सेव हो गई।")
                } else {
                    showMessage("API Key सेव नहीं हुई। दोबारा जाँचें।")
                }
            }
        })

        root.addView(Button(this).apply {
            text = "DELETE SAVED API KEY"

            setOnClickListener {
                AlertDialog.Builder(this@JarvisApiSettings)
                    .setTitle("API Key हटाएँ?")
                    .setMessage("सेव की हुई Gemini API Key हट जाएगी।")
                    .setNegativeButton("रद्द करें", null)
                    .setPositiveButton("हटाएँ") { _, _ ->
                        JarvisOnlineBrain.clearApiKey(
                            this@JarvisApiSettings
                        )
                        status.text = "API Key अभी सेव नहीं है"
                        showMessage("सेव की हुई API Key हटा दी गई।")
                    }
                    .show()
            }
        })

        root.addView(Button(this).apply {
            text = "CHANGE SECURITY PIN"
            setOnClickListener { showChangePin() }
        })

        root.addView(Button(this).apply {
            text = "CLOSE"
            setOnClickListener { finish() }
        })

        setContentView(
            ScrollView(this).apply {
                addView(
                    root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        )
    }

    private fun showChangePin() {
        val oldInput = EditText(this).apply {
            hint = "पुराना PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        val newInput = EditText(this).apply {
            hint = "नया 4–6 अंकों का PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        val confirmInput = EditText(this).apply {
            hint = "नया PIN दोबारा लिखें"
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 8)
            addView(oldInput)
            addView(newInput)
            addView(confirmInput)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("सुरक्षा PIN बदलें")
            .setView(layout)
            .setNegativeButton("रद्द करें", null)
            .setPositiveButton("बदलें", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val oldPin = oldInput.text.toString()
                    val newPin = newInput.text.toString()
                    val confirm = confirmInput.text.toString()
                    val savedHash = prefs().getString(PIN_KEY, null)

                    when {
                        !pinMatches(oldPin, savedHash) -> {
                            oldInput.error = "पुराना PIN गलत है"
                        }

                        !newPin.matches(Regex("\\d{4,6}")) -> {
                            newInput.error = "नया PIN 4–6 अंकों का होना चाहिए"
                        }

                        newPin != confirm -> {
                            confirmInput.error = "दोनों नए PIN एक जैसे होने चाहिए"
                        }

                        else -> {
                            val saved = prefs().edit()
                                .putString(PIN_KEY, hashPin(newPin))
                                .commit()

                            if (saved) {
                                dialog.dismiss()
                                showMessage("सुरक्षा PIN बदल गया।")
                            } else {
                                showMessage("PIN सेव नहीं हो पाया।")
                            }
                        }
                    }
                }
        }

        dialog.show()
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
