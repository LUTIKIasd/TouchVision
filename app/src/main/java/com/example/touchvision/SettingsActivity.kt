package com.example.touchvision

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

class SettingsActivity : AppCompatActivity() {

    private lateinit var voiceSpinner: Spinner
    private lateinit var emotionSpinner: Spinner
    private lateinit var cacheLimitInput: EditText
    private lateinit var saveButton: AppCompatButton
    private lateinit var historyButton: AppCompatButton
    private lateinit var instructionButton: AppCompatButton
    private lateinit var backButton: ImageView

    private var mediaPlayer: MediaPlayer? = null
    private val prefs by lazy { getSharedPreferences("TouchVisionPrefs", Context.MODE_PRIVATE) }


    //только голоса яндекса v1!!!!!!!!!!!!!!!!!!!!
    private val voiceEmotions = mapOf(
        "alena" to listOf("neutral", "good"),
        "ermil" to listOf("neutral", "good"),
        "jane" to listOf("neutral", "good", "evil"),
        "zahar" to listOf("neutral", "good"),
        "marina" to listOf("neutral", "friendly", "whisper"),
        "filipp" to listOf("neutral"),
        "madi_ru" to listOf("neutral")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        voiceSpinner = findViewById(R.id.voiceSpinner)
        emotionSpinner = findViewById(R.id.emotionSpinner)
        cacheLimitInput = findViewById(R.id.cacheLimitInput)
        saveButton = findViewById(R.id.saveButton)
        historyButton = findViewById(R.id.historyButton)
        instructionButton = findViewById(R.id.instructionButton)
        backButton = findViewById(R.id.backButton)

        setupSpinners()
        loadSettings()
        updateHistoryButtonText()

        backButton.setOnClickListener { finish() }

        instructionButton.setOnClickListener { playInstruction() }

        saveButton.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
            finish()
        }

        historyButton.setOnClickListener {
            val current = prefs.getBoolean("show_history", false)
            prefs.edit().putBoolean("show_history", !current).apply()
            updateHistoryButtonText()
            val status = if (!current) "включена" else "скрыта"
            Toast.makeText(this, "История $status", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playInstruction() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(this, R.raw.teaching)
        mediaPlayer?.setOnCompletionListener { it.release() }
        mediaPlayer?.start()
    }

    private fun updateHistoryButtonText() {
        val isShowing = prefs.getBoolean("show_history", false)
        historyButton.text = if (isShowing) "Скрыть\nисторию чтения" else "Показать\nисторию чтения"
    }

    private fun setupSpinners() {
        val voices = voiceEmotions.keys.toList()
        val voiceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, voices)
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        voiceSpinner.adapter = voiceAdapter

        voiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                updateEmotionsForVoice(voices[pos])
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun updateEmotionsForVoice(voice: String) {
        val emotions = voiceEmotions[voice] ?: listOf("neutral")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, emotions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        emotionSpinner.adapter = adapter

        val savedEmotion = prefs.getString("tts_emotion", "neutral")
        val index = emotions.indexOf(savedEmotion)
        if (index >= 0) emotionSpinner.setSelection(index)
    }

    private fun loadSettings() {
        val savedVoice = prefs.getString("tts_voice", "alena") ?: "alena"
        val cacheLimit = prefs.getInt("cache_limit_mb", 50)

        val voices = voiceEmotions.keys.toList()
        val voiceIndex = voices.indexOf(savedVoice).coerceAtLeast(0)
        voiceSpinner.setSelection(voiceIndex)
        cacheLimitInput.setText(cacheLimit.toString())
    }

    private fun saveSettings() {
        prefs.edit()
            .putString("tts_voice", voiceSpinner.selectedItem as String)
            .putString("tts_emotion", emotionSpinner.selectedItem as String)
            .putInt("cache_limit_mb", cacheLimitInput.text.toString().toIntOrNull() ?: 50)
            .apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}