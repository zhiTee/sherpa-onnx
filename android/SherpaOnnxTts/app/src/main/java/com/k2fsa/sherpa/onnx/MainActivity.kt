package com.k2fsa.sherpa.onnx

import android.app.Activity
import android.content.res.AssetManager
import android.media.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread

private const val TAG = "sherpa-onnx-xml"

class MainActivity : AppCompatActivity() {

    private lateinit var tts: OfflineTts
    private lateinit var track: AudioTrack
    private var mediaPlayer: MediaPlayer? = null

    @Volatile private var stopped = false

    private lateinit var textBox: EditText
    private lateinit var sidBox: EditText
    private lateinit var speedBox: EditText
    private lateinit var generateBtn: Button
    private lateinit var playBtn: Button
    private lateinit var stopBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setListeners()

        Log.i(TAG, "Initializing TTS…")
        initTts()
        Log.i(TAG, "Done.")

        Log.i(TAG, "Initializing AudioTrack…")
        initAudioTrack()
        Log.i(TAG, "Done.")
    }

    // -----------------------------
    // Bind XML views
    // -----------------------------
    private fun bindViews() {
        textBox = findViewById(R.id.text)
        sidBox = findViewById(R.id.sid)
        speedBox = findViewById(R.id.speed)

        generateBtn = findViewById(R.id.generate)
        playBtn = findViewById(R.id.play)
        stopBtn = findViewById(R.id.stop)
    }

    private fun setListeners() {
        generateBtn.setOnClickListener {
            hideKeyboard()
            validateAndGenerate()
        }

        playBtn.setOnClickListener {
            hideKeyboard()
            startPlay()
        }

        stopBtn.setOnClickListener {
            hideKeyboard()
            startStop()
        }
    }

    // ---------------------------------------
    // Hide keyboard when touching outside EditText
    // ---------------------------------------
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        currentFocus?.let {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        currentFocus?.clearFocus()
    }

    private fun toast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------
    // Validate & generate
    // ---------------------------------------
    private fun validateAndGenerate() {
        val text = textBox.text.toString()
        val sid = sidBox.text.toString().toIntOrNull()
        val speed = speedBox.text.toString().toFloatOrNull()

        when {
            text.isBlank() -> {
                toast("Please enter text")
                return
            }

            sid == null || sid < 0 -> {
                toast("Speaker ID must be a non-negative integer")
                return
            }

            speed == null || speed <= 0f -> {
                toast("Speed must be a positive number")
                return
            }
        }

        generateBtn.isEnabled = false
        startGenerate(text, sid, speed)

    }

    // ---------------------------------------
    // AudioTrack + TTS
    // ---------------------------------------
    private fun initAudioTrack() {
        val sr = tts.sampleRate()

        val buf = AudioTrack.getMinBufferSize(
            sr,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sr)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        track = AudioTrack(attrs, format, buf, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
        track.play()
    }

    private fun callback(samples: FloatArray): Int {
        return if (!stopped) {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            1
        } else {
            track.stop()
            0
        }
    }

    private fun startGenerate(text: String, sid: Int?, speed: Float?) {
        stopped = false

        track.pause()
        track.flush()
        track.play()

        if (sid == null) {
            toast("Please enter speaker ID")
            return
        }
        if (speed == null ){
            toast("Please enter speed")
            return
        }

        thread {
            val result = tts.generateWithCallback(
                text = text,
                sid = sid,
                speed = speed,
                callback = this::callback
            )

            val filename = "${filesDir.absolutePath}/generated.wav"
            val ok = result.samples.isNotEmpty() && result.save(filename)

            runOnUiThread {
                generateBtn.isEnabled = true

                if (ok) {
                    toast("Generated: $filename")
                } else {
                    toast("Failed to generate audio")
                }

                try { track.stop() } catch (_: Exception) {}
            }
        }
    }

    private fun startPlay() {
        val f = File("${filesDir.absolutePath}/generated.wav")
        if (!f.exists()) {
            toast("Generate audio first")
            return
        }

        mediaPlayer?.stop()
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@MainActivity, Uri.fromFile(f))
            prepare()
            start()
        }
    }

    private fun startStop() {
        stopped = true

        try {
            track.pause()
            track.flush()
        } catch (_: Exception) {}

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}

        mediaPlayer = null
    }

    // -----------------------------
    // TTS initialization (same logic as your original)
    // -----------------------------
    private fun initTts() {
        var modelDir: String?
        var modelName: String?
        var acousticModelName: String?
        var vocoder: String?
        var voices: String?
        var ruleFsts: String?
        var ruleFars: String?
        var lexicon: String?
        var dataDir: String?
        var assets: AssetManager? = application.assets
        var isKitten = false

        // The purpose of such a design is to make the CI test easier
        // Please see
        // https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/generate-tts-apk-script.py

        // VITS -- begin
        modelName = null
        // VITS -- end

        // Matcha -- begin
        acousticModelName = null
        vocoder = null
        // Matcha -- end

        // For Kokoro -- begin
        voices = null
        // For Kokoro -- end


        modelDir = null
        ruleFsts = null
        ruleFars = null
        lexicon = null
        dataDir = null

        // Example 1:
        // modelDir = "vits-vctk"
        // modelName = "vits-vctk.onnx"
        // lexicon = "lexicon.txt"

        // Example 2:
        // https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models
        // https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-low.tar.bz2
        // modelDir = "vits-piper-en_US-amy-low"
        // modelName = "en_US-amy-low.onnx"
        // dataDir = "vits-piper-en_US-amy-low/espeak-ng-data"

        // Example 3:
        // https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-icefall-zh-aishell3.tar.bz2
        // modelDir = "vits-icefall-zh-aishell3"
        // modelName = "model.onnx"
        // ruleFars = "vits-icefall-zh-aishell3/rule.far"
        // lexicon = "lexicon.txt"

        // Example 4:
        // https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html#csukuangfj-vits-zh-hf-fanchen-c-chinese-187-speakers
        // modelDir = "vits-zh-hf-fanchen-C"
        // modelName = "vits-zh-hf-fanchen-C.onnx"
        // lexicon = "lexicon.txt"

        // Example 5:
        // https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-coqui-de-css10.tar.bz2
        // modelDir = "vits-coqui-de-css10"
        // modelName = "model.onnx"

        // Example 6
        // vits-melo-tts-zh_en
        // https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html#vits-melo-tts-zh-en-chinese-english-1-speaker
        // modelDir = "vits-melo-tts-zh_en"
        // modelName = "model.onnx"
        // lexicon = "lexicon.txt"

        // Example 7
        // matcha-icefall-zh-baker
        // https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/matcha.html#matcha-icefall-zh-baker-chinese-1-female-speaker
        // modelDir = "matcha-icefall-zh-baker"
        // acousticModelName = "model-steps-3.onnx"
        // vocoder = "vocos-22khz-univ.onnx"    // Vocoder should be downloaded separately; place in the **root directory of your resources folder**, not under modelDir.
        // lexicon = "lexicon.txt"

        // Example 8
        // matcha-icefall-en_US-ljspeech
        // https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/matcha.html#matcha-icefall-en-us-ljspeech-american-english-1-female-speaker
        // modelDir = "matcha-icefall-en_US-ljspeech"
        // acousticModelName = "model-steps-3.onnx"
        // vocoder = "vocos-22khz-univ.onnx"
        // dataDir = "matcha-icefall-en_US-ljspeech/espeak-ng-data"

        // Example 9
        // kokoro-en-v0_19
        // modelDir = "kokoro-en-v0_19"
        // modelName = "model.onnx"
        // voices = "voices.bin"
        // dataDir = "kokoro-en-v0_19/espeak-ng-data"

        // Example 10
        // kokoro-multi-lang-v1_0
        modelDir = "kokoro-int8-multi-lang-v1_1"
        modelName = "model.int8.onnx"
        voices = "voices.bin"
        dataDir = "$modelDir/espeak-ng-data"
        lexicon = "$modelDir/lexicon-us-en.txt,$modelDir/lexicon-zh.txt"

        // Example 11
        // kitten-nano-en-v0_1-fp16
        // modelDir = "kitten-nano-en-v0_1-fp16"
        // modelName = "model.fp16.onnx"
        // voices = "voices.bin"
        // dataDir = "kokoro-multi-lang-v1_0/espeak-ng-data"
        // isKitten = true

        // Example 12
        // matcha-icefall-zh-en
        // https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese-English/matcha-icefall-zh-en.html
        // modelDir = "matcha-icefall-zh-en"
        // acousticModelName = "model-steps-3.onnx"
        // vocoder = "vocos-16khz-univ.onnx"    // Vocoder should be downloaded separately; place in the **root directory of your resources folder**, not under modelDir.
        // dataDir = "matcha-icefall-zh-en/espeak-ng-data"
        // lexicon = "lexicon.txt"

        if (dataDir != null) {
            val newDir = copyDataDir(dataDir!!)
            dataDir = "$newDir/$dataDir"
        }

        val config = getOfflineTtsConfig(
            modelDir = modelDir!!,
            modelName = modelName ?: "",
            acousticModelName = acousticModelName ?: "",
            vocoder = vocoder ?: "",
            voices = voices ?: "",
            lexicon = lexicon ?: "",
            dataDir = dataDir ?: "",
            dictDir = "",
            ruleFsts = ruleFsts ?: "",
            ruleFars = ruleFars ?: "",
            isKitten = isKitten,
        )!!

        tts = OfflineTts(assetManager = assets, config = config)
    }

    private fun copyDataDir(dataDir: String): String {
        Log.i(TAG, "data dir is $dataDir")
        copyAssets(dataDir)

        return application.getExternalFilesDir(null)!!.absolutePath
    }

    private fun copyAssets(path: String) {
        try {
            val list = assets.list(path) ?: return
            if (list.isEmpty()) {
                copyFile(path)
            } else {
                val dir = File("${application.getExternalFilesDir(null)}/$path")
                dir.mkdirs()
                list.forEach { copyAssets("$path/$it") }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed copying $path: $ex")
        }
    }

    private fun copyFile(filename: String) {
        try {
            val istream = assets.open(filename)
            val out = FileOutputStream("${application.getExternalFilesDir(null)}/$filename")
            val buf = ByteArray(1024)
            var read: Int
            while (istream.read(buf).also { read = it } != -1) {
                out.write(buf, 0, read)
            }
            istream.close()
            out.flush()
            out.close()
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to copy $filename: $ex")
        }
    }
}