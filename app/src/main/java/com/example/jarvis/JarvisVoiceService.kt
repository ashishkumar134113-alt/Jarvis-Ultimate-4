package com.example.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Locale

class JarvisVoiceService : Service(), RecognitionListener {
    companion object {
        const val ACTION_STOP = "com.example.jarvis.STOP"
        private const val CHANNEL_ID = "jarvis_voice"
        private const val NOTIFICATION_ID = 101
    }

    private val handler = Handler(Looper.getMainLooper())
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var running = false
    private var listening = false
    private var speaking = false
    private var ttsReady = false
    private var processingOnline = false
    private var awaitingApiPin = false
    private var pinAttempts = 0
    private var restartTask: Runnable? = null

    private val pinTimeoutTask = Runnable {
        if (awaitingApiPin) {
            cancelPinRequest()
            speak("जी सर, सुरक्षा समय समाप्त हो गया। जरूरत हो तो दोबारा बोलिए।")
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        initializeVoice()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, createNotification())
        if (!running) {
            running = true
            initializeRecognizer()
            scheduleListening(500)
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "JARVIS Voice Assistant", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, JarvisVoiceService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPending = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS ULTIMATE 4.0")
            .setContentText("Voice Assistant Active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_media_pause, "STOP", stopPending)
            .setOngoing(true)
            .build()
    }

    private fun initializeVoice() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale("hi", "IN")
                applyVoiceSettings()
            }
        }
    }

    private fun applyVoiceSettings() {
        val prefs = getSharedPreferences("jarvis_voice", MODE_PRIVATE)
        tts?.setPitch(prefs.getFloat("pitch", 1.0f))
        tts?.setSpeechRate(prefs.getFloat("speed", 1.0f))
    }

    private fun initializeRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("सर, वॉइस रिकग्निशन उपलब्ध नहीं है।")
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(this)
    }

    private fun scheduleListening(delay: Long) {
        if (!running || processingOnline || awaitingApiPin) return
        restartTask?.let { handler.removeCallbacks(it) }
        val task = Runnable { startListening() }
        restartTask = task
        handler.postDelayed(task, delay)
    }

    private fun startListening() {
        if (!running || listening || speaking || processingOnline || recognizer == null) return
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            listening = true
            recognizer?.startListening(speechIntent)
        } catch (_: Exception) {
            listening = false
            scheduleListening(1500)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) { listening = true }
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}

    override fun onError(error: Int) {
        listening = false
        if (!running || processingOnline) return
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            stopSelf()
            return
        }
        if (awaitingApiPin) {
            // PIN timeout remains active; resume listening without clearing the request.
            handler.postDelayed({ if (running && awaitingApiPin) startListening() }, 500)
            return
        }
        scheduleListening(
            when (error) {
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 2000L
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 700L
                else -> 1500L
            }
        )
    }

    override fun onResults(results: Bundle?) {
        listening = false
        val spokenText = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.trim().orEmpty()
        if (spokenText.isNotBlank()) processCommand(spokenText)
        else if (awaitingApiPin) {
            handler.postDelayed({ if (running && awaitingApiPin) startListening() }, 700)
        } else scheduleListening(700)
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun processCommand(text: String) {
        if (awaitingApiPin) {
            verifySpokenPin(text)
            return
        }
        var command = text.lowercase(Locale.ROOT)
        val wakeWords = listOf("hey jarvis", "हे जार्विस", "जार्विस", "jarvis")
        if (wakeWords.none { command.contains(it) }) {
            scheduleListening(400)
            return
        }
        wakeWords.forEach { command = command.replace(it, "") }
        command = command.trim()
        if (command.isBlank()) {
            speak("जी सर, बताइए।")
            return
        }
        if (command.contains("बंद हो जाओ") || command.contains("stop jarvis")) {
            speak("जी सर, जार्विस बंद कर रहा हूँ।")
            handler.postDelayed({ stopSelf() }, 1500)
            return
        }
        if (isApiSettingsCommand(command)) {
            requestApiPin()
            return
        }

        // Learning commands are handled locally before mobile commands or Gemini.
        val isLearningCommand = listOf(
            "याद रखो ", "सीखो ", "learn ", "भूल जाओ ", "forget "
        ).any { command.startsWith(it) }
        if (isLearningCommand) {
            speak(JarvisOfflineBrain.reply(this, command))
            return
        }

        // Exact matches in user-approved local memory take priority.
        val learned = JarvisLearningMemory.recall(this, command)
        if (learned != null) {
            speak(learned)
            return
        }

        val response = JarvisCommandManager.execute(this, command)
        if (response.startsWith(JarvisCommandManager.ONLINE_PREFIX)) {
            val question = response
                .removePrefix(JarvisCommandManager.ONLINE_PREFIX).trim()
            // For now prefer the offline knowledge brain, without a Gemini call.
            speak(JarvisOfflineBrain.reply(this, question))
            return
        }
        if (response.isNotBlank()) speak(response)
        else scheduleListening(600)
    }

    private fun isApiSettingsCommand(command: String): Boolean {
        val hasApi = command.contains("api") || command.contains("एपीआई")
        val hasSettings = command.contains("सेटिंग") ||
            command.contains("settings") || command.contains("सेटिंग्स")
        val hasOpenWord = command.contains("खोल") ||
            command.contains("open") || command.contains("दिखा")
        return hasApi && hasSettings && hasOpenWord
    }

    private fun requestApiPin() {
        val savedHash = getSharedPreferences(
            "jarvis_api_security", MODE_PRIVATE
        ).getString("settings_pin_hash", null)
        if (savedHash.isNullOrBlank()) {
            speak("जी सर, पहले ऐप की स्क्रीन पर अपना सुरक्षा PIN सेट करें।")
            return
        }
        awaitingApiPin = true
        pinAttempts = 0
        handler.removeCallbacks(pinTimeoutTask)
        handler.postDelayed(pinTimeoutTask, 20000)
        speak("जी सर, API सेटिंग खोलने के लिए अपना सुरक्षा PIN बोलिए।")
    }

    private fun verifySpokenPin(spokenText: String) {
        val pin = convertSpeechToPin(spokenText)
        if (!pin.matches(Regex("\\d{4,6}"))) {
            recordFailedPin()
            return
        }
        val savedHash = getSharedPreferences(
            "jarvis_api_security", MODE_PRIVATE
        ).getString("settings_pin_hash", null)
        if (savedHash != null && constantTimeEquals(savedHash, sha256(pin))) {
            cancelPinRequest()
            speak("जी सर, PIN सही है। API सेटिंग खोल रहा हूँ।")
            handler.postDelayed({
                if (!running) return@postDelayed
                try {
                    startActivity(
                        Intent(this, JarvisApiSettings::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                    speak("जी सर, API सेटिंग नहीं खुल सकी।")
                }
            }, 1200)
        } else recordFailedPin()
    }

    private fun recordFailedPin() {
        pinAttempts++
        if (pinAttempts >= 3) {
            cancelPinRequest()
            speak("जी सर, तीन बार गलत PIN बोला गया। API सेटिंग नहीं खुलेगी।")
        } else speak("PIN सही से पहचान नहीं पाया। कृपया चार से छह अंक साफ बोलिए।")
    }

    private fun cancelPinRequest() {
        awaitingApiPin = false
        pinAttempts = 0
        handler.removeCallbacks(pinTimeoutTask)
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    private fun convertSpeechToPin(text: String): String {
        val cleaned = text.lowercase(Locale.ROOT)
            .replace(Regex("[,;.!?]"), " ").trim()
        if (cleaned.matches(Regex("[0-9\\s-]+"))) return cleaned.filter { it.isDigit() }
        val digits = mapOf(
            "शून्य" to "0", "जीरो" to "0", "ज़ीरो" to "0", "zero" to "0", "oh" to "0",
            "एक" to "1", "इक" to "1", "one" to "1",
            "दो" to "2", "टू" to "2", "two" to "2",
            "तीन" to "3", "थ्री" to "3", "three" to "3",
            "चार" to "4", "फोर" to "4", "four" to "4",
            "पांच" to "5", "पाँच" to "5", "फाइव" to "5", "five" to "5",
            "छह" to "6", "छः" to "6", "सिक्स" to "6", "six" to "6",
            "सात" to "7", "सेवन" to "7", "seven" to "7",
            "आठ" to "8", "एट" to "8", "eight" to "8",
            "नौ" to "9", "नाइन" to "9", "nine" to "9"
        )
        val output = StringBuilder()
        for (token in cleaned.split(Regex("\\s+"))) {
            val digit = digits[token]
            if (digit != null) output.append(digit)
            else if (token.all { it.isDigit() }) output.append(token)
            else return ""
        }
        return output.toString()
    }

    private fun speak(message: String) {
        if (!ttsReady) {
            speaking = false
            if (running && !processingOnline) scheduleListening(1000)
            return
        }
        speaking = true
        listening = false
        restartTask?.let { handler.removeCallbacks(it) }
        recognizer?.cancel()
        applyVoiceSettings()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                handler.post {
                    speaking = false
                    if (running && !processingOnline) {
                        if (awaitingApiPin) startListening() else scheduleListening(600)
                    }
                }
            }
            override fun onError(id: String?) {
                handler.post {
                    speaking = false
                    if (running && !processingOnline) {
                        if (awaitingApiPin) startListening() else scheduleListening(1000)
                    }
                }
            }
        })
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
    }

    override fun onDestroy() {
        running = false
        listening = false
        processingOnline = false
        awaitingApiPin = false
        handler.removeCallbacks(pinTimeoutTask)
        restartTask?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
