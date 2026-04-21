package com.example.touchvision

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.nio.charset.Charset
import java.util.*

class TagProgrammingActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var recognizedText: TextView
    private val VOICE_REQUEST_CODE = 1001
    private val CONFIRM_REQUEST_CODE = 1002

    private lateinit var tts: TextToSpeech
    private val handler = Handler(Looper.getMainLooper())

    private var pendingText: String? = null
    private var nfcAdapter: NfcAdapter? = null
    private var readyToWrite = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tag_programming)

        recognizedText = findViewById(R.id.recognizedText)
        val backButton: ImageView = findViewById(R.id.backButton)

        backButton.setOnClickListener {
            finish()
        }

        tts = TextToSpeech(this, this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC не поддерживается", Toast.LENGTH_LONG).show()
            finish()
            return
        }


        handler.postDelayed({ startVoiceInput() }, 400)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.forLanguageTag("ru-RU")
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Произнесите текст для записи")
        }
        try {
            startActivityForResult(intent, VOICE_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка голосового ввода", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startConfirmationVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Подтвердите запись: да или нет")
        }
        try {
            startActivityForResult(intent, CONFIRM_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка подтверждения", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK || data == null) return
        val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val spokenText = results?.firstOrNull()?.lowercase(Locale.getDefault()) ?: return

        when (requestCode) {
            VOICE_REQUEST_CODE -> {
                recognizedText.text = spokenText
                pendingText = spokenText
                speakText("Вы хотите записать: $spokenText ?")
                handler.postDelayed({ startConfirmationVoiceInput() }, 3500)
            }

            CONFIRM_REQUEST_CODE -> {
                if (spokenText.contains("да") || spokenText.contains("правильно") || spokenText.contains("записывай")) {
                    speakText("Поднесите метку к телефону")
                    recognizedText.text = "Приложите метку..."
                    readyToWrite = true
                } else {
                    speakText("Хорошо, давайте повторим текст")
                    recognizedText.text = "Повторите ввод..."
                    handler.postDelayed({ startVoiceInput() }, 2000)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (!readyToWrite || pendingText.isNullOrEmpty()) return

        val tag: Tag? = intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (tag != null) {
            try {
                writeToTag(tag, pendingText!!)
                speakText("Готово. Метка успешно записана")
                saveToHistory(pendingText!!)
                readyToWrite = false

                handler.postDelayed({ finish() }, 2500)
            } catch (e: Exception) {
                speakText("Произошла ошибка записи")
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun writeToTag(tag: Tag, text: String) {
        val ndef = Ndef.get(tag) ?: throw Exception("Метка не NDEF")
        ndef.connect()

        val lang = "ru"
        val textBytes = text.toByteArray(Charset.forName("UTF-8"))
        val langBytes = lang.toByteArray(Charset.forName("US-ASCII"))
        val payload = ByteArray(1 + langBytes.size + textBytes.size)

        payload[0] = langBytes.size.toByte()
        System.arraycopy(langBytes, 0, payload, 1, langBytes.size)
        System.arraycopy(textBytes, 0, payload, 1 + langBytes.size, textBytes.size)

        val record = NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), payload)
        val message = NdefMessage(arrayOf(record))

        ndef.writeNdefMessage(message)
        ndef.close()
    }

    private fun saveToHistory(text: String) {
        val prefs = getSharedPreferences("TouchVisionPrefs", Context.MODE_PRIVATE)
        val historySet = prefs.getStringSet("history_items", mutableSetOf()) ?: mutableSetOf()
        val historyList = historySet.toMutableList()

        val timestamp = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val entry = "[$timestamp] Запись: $text"

        historyList.add(0, entry) // Добавляем в начало списка
        if (historyList.size > 10) historyList.removeAt(historyList.size - 1)

        prefs.edit().putStringSet("history_items", historyList.toSet()).apply()
    }

    private fun speakText(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent, android.app.PendingIntent.FLAG_MUTABLE
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}