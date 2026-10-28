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
import kotlinx.coroutines.withContext
import java.util.Locale

class JarvisVoiceService : Service(), RecognitionListener {

    companion object {
        const val ACTION_STOP = "com.example.jarvis.STOP"
        private const val CHANNEL_ID = "jarvis_voice"
        private const val NOTIFICATION_ID = 101
    }

    private val handler = Handler(Looper.getMainLooper())

    private val serviceJob = SupervisorJob()
    private val serviceScope =
        CoroutineScope(serviceJob + Dispatchers.Main)

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private var running = false
    private var listening = false
    private var speaking = false
    private var ttsReady = false
    private var processingOnline = false

    private var restartTask: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        initializeVoice()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )

        if (!running) {
            running = true
            initializeRecognizer()
            scheduleListening(500)
        }

        return START_NOT_STICKY
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "JARVIS Voice Assistant",
            NotificationManager.IMPORTANCE_LOW
        )

        getSystemService(
            NotificationManager::class.java
        ).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(
            this,
            JarvisVoiceService::class.java
        ).apply {
            action = ACTION_STOP
        }

        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)

        val openPending = PendingIntent.getActivity(
            this,
            2,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS ULTIMATE 4.0")
            .setContentText("Voice Assistant Active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openPending)
            .addAction(
                android.R.drawable.ic_media_pause,
                "STOP",
                stopPending
            )
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
        val prefs = getSharedPreferences(
            "jarvis_voice",
            MODE_PRIVATE
        )

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
        if (!running || processingOnline) return

        restartTask?.let { handler.removeCallbacks(it) }

        val task = Runnable { startListening() }
        restartTask = task
        handler.postDelayed(task, delay)
    }

    private fun startListening() {
        if (!running || listening || speaking || processingOnline) return

        val speechIntent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        try {
            listening = true
            recognizer?.startListening(speechIntent)
        } catch (_: Exception) {
            listening = false
            scheduleListening(1500)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        listening = true
    }

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

        val delay = when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 2000L
            SpeechRecognizer.ERROR_NO_MATCH -> 700L
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 700L
            else -> 1500L
        }

        scheduleListening(delay)
    }

    override fun onResults(results: Bundle?) {
        listening = false

        val matches = results?.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION
        )

        val spokenText = matches?.firstOrNull()?.trim().orEmpty()

        if (spokenText.isNotBlank()) {
            processCommand(spokenText)
        } else {
            scheduleListening(700)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun processCommand(text: String) {
        var command = text.lowercase(Locale.ROOT)

        val wakeWords = listOf(
            "hey jarvis",
            "हे जार्विस",
            "जार्विस",
            "jarvis"
        )

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

        if (
            command.contains("बंद हो जाओ") ||
            command.contains("stop jarvis")
        ) {
            speak("जी सर, जार्विस बंद कर रहा हूँ।")
            handler.postDelayed({ stopSelf() }, 1500)
            return
        }

        val response = JarvisCommandManager.execute(this, command)

        if (response.startsWith(JarvisCommandManager.ONLINE_PREFIX)) {
            val question = response.removePrefix(
                JarvisCommandManager.ONLINE_PREFIX
            ).trim()

            askOnlineAI(question)
            return
        }

        if (response.isNotBlank()) {
            speak(response)
        } else {
            scheduleListening(600)
        }
    }

    private fun askOnlineAI(question: String) {
        if (processingOnline) return

        processingOnline = true
        listening = false

        restartTask?.let { handler.removeCallbacks(it) }
        recognizer?.cancel()

        speak("जी सर, आपके सवाल का जवाब खोज रहा हूँ।")

        // Wait until the short acknowledgement has finished before
        // sending the request, so the microphone does not listen
        // while JARVIS is speaking.
        handler.postDelayed({
            if (!running) return@postDelayed

            serviceScope.launch {
                val answer = withContext(Dispatchers.IO) {
                    JarvisOnlineBrain.ask(
                        this@JarvisVoiceService,
                        question
                    )
                }

                if (!running) return@launch

                processingOnline = false
                speak(answer)
            }
        }, 1200)
    }

    private fun speak(message: String) {
        if (!ttsReady) {
            speaking = false
            if (running && !processingOnline) {
                scheduleListening(1000)
            }
            return
        }

        speaking = true
        listening = false

        restartTask?.let { handler.removeCallbacks(it) }
        recognizer?.cancel()

        applyVoiceSettings()

        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(id: String?) {}

                override fun onDone(id: String?) {
                    handler.post {
                        speaking = false
                        if (running && !processingOnline) {
                            scheduleListening(600)
                        }
                    }
                }

                override fun onError(id: String?) {
                    handler.post {
                        speaking = false
                        if (running && !processingOnline) {
                            scheduleListening(1000)
                        }
                    }
                }
            }
        )

        tts?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "jarvis_reply"
        )
    }

    override fun onDestroy() {
        running = false
        listening = false
        processingOnline = false

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
