package com.example.jarvis

import android.app.Activity
import android.app.AlertDialog
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
import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

class JarvisApiSettings : Activity() {

    private val prefsName = "jarvis_api_security"
    private val pinKey = "settings_pin_hash"

    private fun prefs(): SharedPreferences =
        getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (prefs().getString(pinKey, null) == null) {
            showPinSetup()
        } else {
            showApiScreen()
        }
    }

    private fun showPinSetup() {
        val pinInput = EditText(this).apply {
            hint = "4–6 अंकों का नया PIN"
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

        AlertDialog.Builder(this)
            .setTitle("JARVIS सुरक्षा PIN सेट करें")
            .setMessage("API Settings को सुरक्षित रखने के लिए PIN बनाएँ।")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("PIN सेव करें", null)
            .setNegativeButton("बंद करें") { _, _ -> finish() }
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener {
                            val pin = pinInput.text.toString()
                            val confirm = confirmInput.text.toString()

                            if (!pin.matches(Regex("\\d{4,6}"))) {
                                pinInput.error = "4 से 6 अंक डालें"
                            } else if (pin != confirm) {
                                confirmInput.error = "दोनों PIN एक जैसे होने चाहिए"
                            } else {
                                prefs().edit()
                                    .putString(pinKey, hashPin(pin))
                                    .apply()
                                dialog.dismiss()
                                showApiScreen()
                            }
                        }
                }
                dialog.show()
            }
    }

    private fun showApiScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 36, 32, 24)
        }

        val title = TextView(this).apply {
            text = "JARVIS • GEMINI API"
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        root.addView(title)

        val apiInput = EditText(this).apply {
            hint = "Gemini API Key यहाँ पेस्ट करें"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        root.addView(apiInput)

        val saveButton = Button(this).apply {
            text = "SAVE API KEY"
            setOnClickListener {
                val key = apiInput.text.toString().trim()
                if (JarvisOnlineBrain.saveApiKey(this@JarvisApiSettings, key)) {
                    apiInput.text.clear()
                    Toast.makeText(
                        this@JarvisApiSettings,
                        "API Key सुरक्षित रूप से सेव हुई",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@JarvisApiSettings,
                        "API Key सेव नहीं हुई। जाँचकर दोबारा प्रयास करें।",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        root.addView(saveButton)

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
            text = "DELETE SAVED API KEY"
            setOnClickListener {
                AlertDialog.Builder(this@JarvisApiSettings)
                    .setTitle("API Key हटाएँ?")
                    .setMessage("सेव की हुई Gemini API Key हट जाएगी।")
                    .setNegativeButton("रद्द करें", null)
                    .setPositiveButton("हटाएँ") { _, _ ->
                        JarvisOnlineBrain.clearApiKey(this@JarvisApiSettings)
                        status.text = "API Key अभी सेव नहीं है"
                        Toast.makeText(
                            this@JarvisApiSettings,
                            "API Key हटा दी गई",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .show()
            }
        })

        root.addView(Button(this).apply {
            text = "CHANGE SECURITY PIN"
            setOnClickListener {
                showChangePin()
            }
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

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 8)
            addView(oldInput)
            addView(newInput)
        }

        AlertDialog.Builder(this)
            .setTitle("सुरक्षा PIN बदलें")
            .setView(layout)
            .setNegativeButton("रद्द करें", null)
            .setPositiveButton("बदलें") { _, _ ->
                val oldPin = oldInput.text.toString()
                val newPin = newInput.text.toString()

                if (hashPin(oldPin) != prefs().getString(pinKey, null)) {
                    Toast.makeText(this, "पुराना PIN गलत है", Toast.LENGTH_SHORT).show()
                } else if (!newPin.matches(Regex("\\d{4,6}"))) {
                    Toast.makeText(this, "नया PIN 4–6 अंकों का रखें", Toast.LENGTH_SHORT).show()
                } else {
                    prefs().edit().putString(pinKey, hashPin(newPin)).apply()
                    Toast.makeText(this, "PIN बदल गया", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}
