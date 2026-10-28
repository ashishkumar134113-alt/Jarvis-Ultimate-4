package com.example.jarvis

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class JarvisVoiceStudio : Activity() {

    private lateinit var pitchSeek: SeekBar
    private lateinit var speedSeek: SeekBar

    private lateinit var pitchText: TextView
    private lateinit var speedText: TextView
    private lateinit var statusText: TextView

    private var pitch = 1.0f
    private var speed = 1.0f

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val prefs by lazy {
        getSharedPreferences(
            "jarvis_voice",
            MODE_PRIVATE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pitch = prefs.getFloat("pitch", 1.0f)
        speed = prefs.getFloat("speed", 1.0f)

        createInterface()
        initializeTts()
    }

    private fun createInterface() {

        val background = Color.rgb(5, 12, 22)
        val cyan = Color.rgb(0, 229, 255)
        val white = Color.WHITE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 36, 32, 36)
            setBackgroundColor(background)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }

        setContentView(scroll)

        fun heading(value: String, size: Float): TextView {
            return TextView(this).apply {
                text = value
                textSize = size
                setTextColor(cyan)
                gravity = Gravity.CENTER
                setPadding(0, 12, 0, 18)
            }
        }

        fun button(value: String, action: () -> Unit) {
            val item = Button(this).apply {
                text = value
                isAllCaps = false
                setTextColor(white)
                backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        Color.rgb(12, 65, 85)
                    )
                setOnClickListener { action() }
            }

            root.addView(item)
        }

        root.addView(
            heading("J.A.R.V.I.S", 32f)
        )

        root.addView(
            heading("VOICE STUDIO 4.0", 21f)
        )

        statusText = TextView(this).apply {
            text = "Voice Engine तैयार हो रहा है..."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(white)
            setPadding(0, 8, 0, 28)
        }

        root.addView(statusText)

        // PITCH CONTROL

        pitchText = heading("", 17f)
        root.addView(pitchText)

        pitchSeek = SeekBar(this).apply {
            max = 150
            progress = valueToProgress(pitch)
        }

        root.addView(pitchSeek)

        pitchSeek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    pitch = progressToValue(progress)
                    updateLabels()
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) {}

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) {}
            }
        )

        // SPEED CONTROL

        speedText = heading("", 17f)
        root.addView(speedText)

        speedSeek = SeekBar(this).apply {
            max = 150
            progress = valueToProgress(speed)
        }

        root.addView(speedSeek)

        speedSeek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    speed = progressToValue(progress)
                    updateLabels()
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) {}

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) {}
            }
        )

        updateLabels()

        root.addView(
            heading("VOICE PRESETS", 19f)
        )

        button("CUSTOM VOICE (DEFAULT)") {
            setPreset(1.0f, 1.0f)
        }

        button("DEEP VOICE") {
            setPreset(0.75f, 0.90f)
        }

        button("ROBOT VOICE") {
            setPreset(0.85f, 1.10f)
        }

        button("SOFT VOICE") {
            setPreset(1.15f, 0.90f)
        }

        button("FAST ASSISTANT") {
            setPreset(1.0f, 1.30f)
        }

        button("SLOW ASSISTANT") {
            setPreset(1.0f, 0.75f)
        }

        root.addView(
            heading("VOICE TEST", 19f)
        )

        button("TEST JARVIS VOICE") {
            testVoice()
        }

        button("SAVE MY VOICE") {
            saveSettings()
        }

        button("RESET DEFAULT VOICE") {
            setPreset(1.0f, 1.0f)
            saveSettings()
        }

        button("BACK TO JARVIS") {
            finish()
        }
    }

    private fun progressToValue(progress: Int): Float {
        return 0.5f + (progress / 100f)
    }

    private fun valueToProgress(value: Float): Int {
        return ((value - 0.5f) * 100f)
            .toInt()
            .coerceIn(0, 150)
    }

    private fun updateLabels() {

        pitchText.text = String.format(
            Locale.US,
            "VOICE PITCH: %.2f",
            pitch
        )

        speedText.text = String.format(
            Locale.US,
            "VOICE SPEED: %.2f",
            speed
        )
    }

    private fun setPreset(
        newPitch: Float,
        newSpeed: Float
    ) {

        pitchSeek.progress = valueToProgress(newPitch)
        speedSeek.progress = valueToProgress(newSpeed)

        pitch = newPitch
        speed = newSpeed

        updateLabels()
        testVoice()
    }

    private fun initializeTts() {

        tts = TextToSpeech(this) { result ->

            if (result == TextToSpeech.SUCCESS) {

                val languageResult = tts?.setLanguage(
                    Locale("hi", "IN")
                )

                ttsReady =
                    languageResult != TextToSpeech.LANG_MISSING_DATA &&
                    languageResult != TextToSpeech.LANG_NOT_SUPPORTED

                statusText.text = if (ttsReady) {
                    "JARVIS Voice Engine Ready"
                } else {
                    "Hindi Voice उपलब्ध नहीं है। TTS Settings जाँचें।"
                }

            } else {

                ttsReady = false
                statusText.text =
                    "Voice Engine शुरू नहीं हो सका।"
            }
        }
    }

    private fun testVoice() {

        if (!ttsReady) {

            Toast.makeText(
                this,
                "Voice Engine अभी तैयार नहीं है।",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        tts?.setPitch(pitch)
        tts?.setSpeechRate(speed)

        tts?.speak(
            "नमस्कार सर। मैं जार्विस हूँ। आपकी सेवा में तैयार हूँ।",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "jarvis_voice_test"
        )
    }

    private fun saveSettings() {

        prefs.edit()
            .putFloat("pitch", pitch)
            .putFloat("speed", speed)
            .apply()

        Toast.makeText(
            this,
            "JARVIS Voice Settings Saved",
            Toast.LENGTH_SHORT
        ).show()

        statusText.text =
            "Voice Settings सेव हो गई हैं।"
    }

    override fun onDestroy() {

        tts?.stop()
        tts?.shutdown()
        tts = null

        super.onDestroy()
    }
}
