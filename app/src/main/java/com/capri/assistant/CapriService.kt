package com.capri.assistant

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Fluxo: Porcupine escuta só "Capri" (offline, leve) -> bipe -> SpeechRecognizer ouve UM comando
 * -> IA executa -> fala -> volta a escutar "Capri". Sem Porcupine configurado, cai no modo contínuo.
 */
class CapriService : Service(), RecognitionListener {
    private var rec: SpeechRecognizer? = null
    private var porcupine: PorcupineManager? = null
    private lateinit var tts: TextToSpeech
    private lateinit var brain: Assistant
    private lateinit var avatar: Avatar
    private val main = Handler(Looper.getMainLooper())
    private var pending: (() -> String)? = null
    private val wakeRegex = Regex("cap(ri|ry|re|ré)", RegexOption.IGNORE_CASE)

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("capri", "Capri", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "capri").setContentTitle("Capri está ouvindo")
            .setContentText("Diga \"Capri\" + seu comando").setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)

        brain = if (java.io.File(filesDir, "model.litertlm").exists()) LocalBrain(this) else Brain(this)
        avatar = Avatar(this) { tapListen() }
        avatar.show()
        tts = TextToSpeech(this) { tts.language = Locale("pt", "BR") }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { next() }
            @Deprecated("") override fun onError(id: String?) { next() }
        })
        setupWakeWord()
        next()
    }

    private fun setupWakeWord() {
        val key = getSharedPreferences("capri", 0).getString("pv", "") ?: ""
        if (key.isBlank()) return
        porcupine = try {
            PorcupineManager.Builder()
                .setAccessKey(key)
                .setKeywordPath("capri.ppn")          // assets/capri.ppn
                .setModelPath("porcupine_params_pt.pv") // assets/porcupine_params_pt.pv
                .setSensitivity(0.7f)
                .build(applicationContext, PorcupineManagerCallback { _ -> onWake() })
        } catch (e: Exception) { null } // sem arquivos/chave: modo contínuo
    }

    /** Decide o próximo estado de escuta. */
    private fun next() {
        main.post {
            avatar.state("idle")
            if (pending != null || porcupine == null) listenCommand() else idle()
        }
    }

    private fun idle() {
        rec?.destroy(); rec = null
        try { porcupine?.start() } catch (e: Exception) { porcupine = null; listenCommand() }
    }

    private fun onWake() {
        main.post {
            try { porcupine?.stop() } catch (e: Exception) {}
            ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            main.postDelayed({ listenCommand() }, 200)
        }
    }

    private fun listenCommand() {
        avatar.state("listening")
        rec?.destroy()
        rec = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(this@CapriService)
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
        }
    }

    private fun tapListen() {
        try { porcupine?.stop() } catch (e: Exception) {}
        tts.stop()
        listenCommand()
    }

    private fun speak(t: String) {
        avatar.say(t); avatar.state("talking")
        if (tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "u") == TextToSpeech.ERROR) next()
    }

    override fun onResults(b: Bundle?) {
        val text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if (text.isNullOrBlank()) { pending = null; next(); return }
        handle(text)
    }

    override fun onError(e: Int) {
        pending = null
        main.postDelayed({ next() }, 600)
    }

    private fun handle(text: String) {
        val p = pending
        if (p != null) {
            pending = null
            if (Regex("\\b(sim|confirma|confirmo|pode|manda|isso)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                Thread { val r = p(); main.post { speak(r) } }.start()
            } else speak("Cancelado.")
            return
        }
        val m = wakeRegex.find(text)
        val cmd = when {
            porcupine != null -> if (m != null) text.substring(m.range.last + 1) else text // já acordou pela palavra
            m != null -> text.substring(m.range.last + 1)
            else -> { next(); return }
        }.trim(' ', ',', '.', '!')
        if (cmd.isEmpty()) { speak("Pois não?"); return }
        avatar.state("thinking")
        Thread {
            val r = try { brain.ask(cmd) } catch (e: Exception) { Result("Deu erro: ${e.message}") }
            main.post { pending = r.confirm; speak(r.say.ifBlank { "Pronto." }) }
        }.start()
    }

    override fun onReadyForSpeech(p: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(v: Float) {}
    override fun onBufferReceived(b: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(b: Bundle?) {}
    override fun onEvent(t: Int, b: Bundle?) {}

    override fun onDestroy() {
        avatar.hide(); rec?.destroy(); porcupine?.delete(); tts.shutdown(); super.onDestroy()
    }
}
