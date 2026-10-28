package com.example.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var tts: TextToSpeech

    private lateinit var statusText: TextView

    private val permissionCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeVoice()
        createJarvisUI()
        requestJarvisPermissions()
    }

    private fun initializeVoice() {

        tts = TextToSpeech(this) { status ->

            if (status == TextToSpeech.SUCCESS) {

                tts.language = Locale("hi", "IN")

                val prefs = getSharedPreferences(
                    "jarvis_voice",
                    MODE_PRIVATE
                )

                tts.setPitch(
                    prefs.getFloat("pitch", 1.0f)
                )

                tts.setSpeechRate(
                    prefs.getFloat("speed", 1.0f)
                )
            }
        }
    }

    private fun createJarvisUI() {

        val scroll = ScrollView(this)

        scroll.setBackgroundColor(
            Color.rgb(3, 9, 22)
        )

        val layout = LinearLayout(this)

        layout.orientation = LinearLayout.VERTICAL

        layout.gravity = Gravity.CENTER_HORIZONTAL

        layout.setPadding(32, 55, 32, 40)

        scroll.addView(layout)

        val title = TextView(this)

        title.text = "J.A.R.V.I.S"
        title.textSize = 35f
        title.setTextColor(Color.CYAN)
        title.gravity = Gravity.CENTER

        title.setTypeface(
            null,
            Typeface.BOLD
        )

        layout.addView(title)

        val subtitle = TextView(this)

        subtitle.text = "ULTIMATE AI ASSISTANT 4.0"
        subtitle.textSize = 15f
        subtitle.setTextColor(Color.WHITE)
        subtitle.gravity = Gravity.CENTER
        subtitle.setPadding(0, 12, 0, 45)

        layout.addView(subtitle)

        statusText = TextView(this)

        statusText.text = "SYSTEM READY"
        statusText.textSize = 19f
        statusText.setTextColor(Color.GREEN)
        statusText.gravity = Gravity.CENTER
        statusText.setPadding(0, 15, 0, 35)

        layout.addView(statusText)

        addButton(
            layout,
            "START JARVIS"
        ) {
            startJarvis()
        }

        addButton(
            layout,
            "STOP JARVIS"
        ) {
            stopJarvis()
        }

        // NEW: Secure Backend Settings
        addButton(
            layout,
            "SECURE AI SETTINGS"
        ) {
            openApiSettings()
        }

        addButton(
            layout,
            "JARVIS VOICE STUDIO"
        ) {
            openVoiceStudio()
        }

        addButton(
            layout,
            "ENABLE MOBILE CONTROL"
        ) {
            startActivity(
                Intent(
                    Settings.ACTION_ACCESSIBILITY_SETTINGS
                )
            )
        }

        addButton(
            layout,
            "GRANT PERMISSIONS"
        ) {
            requestJarvisPermissions()
        }

        addButton(
            layout,
            "TEST JARVIS VOICE"
        ) {
            speak(
                "जी सर, जार्विस अल्टीमेट आपके लिए तैयार है।"
            )
        }

        setContentView(scroll)
    }

    private fun addButton(
        layout: LinearLayout,
        label: String,
        action: () -> Unit
    ) {

        val button = Button(this)

        button.text = label
        button.isAllCaps = false
        button.setTextColor(Color.WHITE)

        button.setBackgroundColor(
            Color.rgb(0, 95, 145)
        )

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.setMargins(0, 10, 0, 10)

        layout.addView(button, params)

        button.setOnClickListener {
            action()
        }
    }

    private fun startJarvis() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestJarvisPermissions()
            return
        }

        val intent = Intent(
            this,
            JarvisVoiceService::class.java
        )

        ContextCompat.startForegroundService(
            this,
            intent
        )

        statusText.text = "JARVIS ACTIVE"

        speak("जी सर, जार्विस चालू हो गया है।")
    }

    private fun stopJarvis() {

        val intent = Intent(
            this,
            JarvisVoiceService::class.java
        )

        stopService(intent)

        statusText.text = "JARVIS STOPPED"

        speak("जी सर, जार्विस बंद कर दिया गया है।")
    }

    // NEW: Open Secure AI Settings
    private fun openApiSettings() {

        val intent = Intent(
            this,
            JarvisApiSettings::class.java
        )

        startActivity(intent)
    }

    private fun openVoiceStudio() {

        val intent = Intent(
            this,
            JarvisVoiceStudio::class.java
        )

        startActivity(intent)
    }

    private fun speak(message: String) {

        if (::tts.isInitialized) {

            tts.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "jarvis_main"
            )
        }
    }

    private fun requestJarvisPermissions() {

        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.CALL_PHONE
        )

        if (Build.VERSION.SDK_INT >= 33) {

            permissions.add(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        val missing = permissions.filter {

            ContextCompat.checkSelfPermission(
                this,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {

            ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                permissionCode
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == permissionCode) {

            Toast.makeText(
                this,
                "JARVIS permissions updated",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroy() {

        if (::tts.isInitialized) {

            tts.stop()
            tts.shutdown()
        }

        super.onDestroy()
    }
}
