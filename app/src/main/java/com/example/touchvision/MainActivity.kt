package com.example.touchvision

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val TAG = "TouchVision"
    private var nfcAdapter: NfcAdapter? = null
    private val client = OkHttpClient()
    private val PASSWORD_HASH = "937e8d5fbb48bd4949536cd65b8d35c426b80d2f830c5c308e2cdec422ae2244"

    private val audioQueue: Queue<File> = LinkedList()
    private var isPlaying = false
    private val ttsCache: MutableMap<String, File> = mutableMapOf()

    private lateinit var textValue: TextView
    private lateinit var delayValue: TextView
    private lateinit var rootLayout: View
    private lateinit var historyContainer: LinearLayout

    private val historyCards = mutableListOf<View>()

    private val handler = Handler(Looper.getMainLooper())
    private var errorAnimator: ObjectAnimator? = null
    private val prefs by lazy { getSharedPreferences("TouchVisionPrefs", Context.MODE_PRIVATE) }

    private var lastTapTime = 0L
    private var tapCount = 0

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateHistoryVisibility()
        loadHistory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textValue = findViewById(R.id.textValue)
        delayValue = findViewById(R.id.delayValue)
        rootLayout = findViewById(R.id.rootLayout)
        historyContainer = findViewById(R.id.historyContainer)

        findViewById<View>(R.id.card1)?.let { historyCards.add(it) }
        findViewById<View>(R.id.card2)?.let { historyCards.add(it) }
        findViewById<View>(R.id.card3)?.let { historyCards.add(it) }

        checkAndFixHistoryFormat()
        updateHistoryVisibility()
        loadHistory()

        val settingsButton = findViewById<ImageView>(R.id.settingsButton)
        val settingsGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                settingsLauncher.launch(Intent(this@MainActivity, SettingsActivity::class.java))
                return true
            }
            override fun onDown(e: MotionEvent): Boolean = true
        })

        settingsButton.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) v.alpha = 0.5f
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) v.alpha = 1.0f
            settingsGestureDetector.onTouchEvent(event)
        }

        rootLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 600) {
                    tapCount++
                    if (tapCount == 3) {
                        tapCount = 0
                        startActivity(Intent(this, TagProgrammingActivity::class.java))
                    }
                } else tapCount = 1
                lastTapTime = now
            }
            false
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        checkFirstLaunch()
    }

    private fun checkAndFixHistoryFormat() {
        val history = prefs.getString("history_json", null)
        if (history != null && (history.contains("Считано:") || history.contains("["))) {
            prefs.edit().remove("history_json").apply()
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleNfcIntent(it) }
    }

    private fun handleNfcIntent(intent: Intent) {
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            val messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (!messages.isNullOrEmpty()) {
                val ndefMessage = messages[0] as NdefMessage
                val payload = String(ndefMessage.records[0].payload)
                val text = if (payload.length > 3) payload.substring(3) else payload
                textValue.text = text
                stopErrorAnimation()
                saveEvent(text)

                val startTime = System.currentTimeMillis()
                fetchAndCacheTTS(text) {
                    runOnUiThread { delayValue.text = "${System.currentTimeMillis() - startTime} мс" }
                    playAudioFile(it)
                }
            }
        } else {
            showError("Ошибка чтения метки")
        }
    }

    private fun updateHistoryVisibility() {
        val show = prefs.getBoolean("show_history", true)
        (historyContainer.parent as? View)?.visibility = if (show) View.VISIBLE else View.GONE
    }

    fun saveEvent(text: String) {
        if (!prefs.getBoolean("show_history", false)) return
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val date = SimpleDateFormat("03.01.2026", Locale.getDefault()).format(Date())
        val newEntry = "$time|$text|$date"

        val rawHistory = prefs.getString("history_json", "") ?: ""
        val historyList = if (rawHistory.isEmpty()) mutableListOf() else rawHistory.split(";").toMutableList()
        historyList.add(0, newEntry)

        val limitedList = historyList.take(3)
        prefs.edit().putString("history_json", limitedList.joinToString(";")).apply()
        runOnUiThread { updateHistoryView(limitedList) }
    }

    private fun loadHistory() {
        val rawHistory = prefs.getString("history_json", "") ?: ""
        val historyList = if (rawHistory.isEmpty()) listOf() else rawHistory.split(";")
        updateHistoryView(historyList)
    }

    private fun updateHistoryView(history: List<String>) {
        historyCards.forEach { it.visibility = View.GONE }
        history.forEachIndexed { index, entry ->
            if (index < historyCards.size) {
                val card = historyCards[index]
                val parts = entry.split("|")
                if (parts.size >= 2) {
                    card.findViewById<TextView>(R.id.itemTime)?.text = parts[0]
                    card.findViewById<TextView>(R.id.itemText)?.text = parts[1]
                    card.findViewById<TextView>(R.id.itemDate)?.text = parts.getOrNull(2) ?: ""
                    card.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun fetchAndCacheTTS(text: String, callback: (File) -> Unit) {
        val voice = prefs.getString("tts_voice", "alena") ?: "alena"
        val emotion = prefs.getString("tts_emotion", "neutral") ?: "neutral"
        val fileName = "speech_${voice}_${emotion}_${text.hashCode()}.ogg"
        val file = File(cacheDir, fileName)
        if (file.exists()) { callback(file); return }

        val (authHash, timestamp) = generateAuthHash()
        val jsonBody = JSONObject().apply {
            put("text", text); put("authHash", authHash); put("timestamp", timestamp)
            put("voice", voice); put("emotion", emotion)
        }
        val request = Request.Builder().url("https://projectasmp.ru/tts")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { showError("Нет связи с сервером") }
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnUiThread { showError("Ошибка сервера") }
                    return
                }
                val bytes = response.body?.bytes() ?: return
                FileOutputStream(file).use { it.write(bytes) }
                runOnUiThread { enforceCacheLimit(); callback(file) }
            }
        })
    }

    private fun playAudioFile(file: File) {
        synchronized(audioQueue) {
            audioQueue.add(file)
            if (!isPlaying) playNextInQueue()
        }
    }

    private fun playNextInQueue() {
        synchronized(audioQueue) {
            if (audioQueue.isEmpty()) { isPlaying = false; return }
            isPlaying = true
            val file = audioQueue.poll() ?: return
            try {
                val player = MediaPlayer()
                player.setDataSource(file.path)
                player.setOnCompletionListener { player.release(); playNextInQueue() }
                player.prepare(); player.start()
            } catch (e: Exception) { isPlaying = false; playNextInQueue() }
        }
    }

    private fun showError(msg: String) {
        textValue.text = msg
        startErrorAnimation()
        try {
            val player = MediaPlayer.create(this, R.raw.error)
            player.setOnCompletionListener { it.release() }
            player.start()
        } catch (e: Exception) { Log.e(TAG, "Sound error missing") }
    }

    private fun startErrorAnimation() {
        if (errorAnimator?.isRunning == true) return
        errorAnimator = ObjectAnimator.ofObject(rootLayout, "backgroundColor", ArgbEvaluator(),
            Color.WHITE, Color.RED).apply {
            duration = 500; repeatMode = ObjectAnimator.REVERSE; repeatCount = ObjectAnimator.INFINITE; start()
        }
    }

    private fun stopErrorAnimation() {
        errorAnimator?.cancel()
        rootLayout.setBackgroundColor(Color.WHITE)
    }

    private fun enforceCacheLimit() {
        val maxMb = prefs.getInt("cache_limit_mb", 25)
        val files = cacheDir.listFiles { f -> f.extension == "ogg" } ?: return
        var total = files.sumOf { it.length() }
        if (total > maxMb * 1024L * 1024L) {
            files.sortBy { it.lastModified() }
            for (f in files) {
                total -= f.length(); f.delete()
                if (total <= maxMb * 1024L * 1024L) break
            }
        }
    }

    private fun checkFirstLaunch() {
        if (prefs.getBoolean("is_first_launch", true)) {
            prefs.edit().putBoolean("is_first_launch", false).apply()
            handler.postDelayed({
                try {
                    val player = MediaPlayer.create(this, R.raw.teaching)
                    player.setOnCompletionListener { it.release() }
                    player.start()
                } catch (e: Exception) {}
            }, 1500)
        }
    }

    private fun generateAuthHash(): Pair<String, Long> {
        val ts = System.currentTimeMillis()
        val timeHash = sha256((ts / 300000).toString())
        return sha256(PASSWORD_HASH + timeHash) to ts
    }

    private fun sha256(input: String): String {
        val b = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return b.joinToString("") { "%02x".format(it) }
    }
}