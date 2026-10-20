package com.jarvis.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val statusText = mutableStateOf("COLD STANDBY")
    private val statusColor = mutableStateOf(Color(0xFF555555))
    private val logMessages = mutableStateListOf<String>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            appendLog("Audio permission denied. Voice recognition disabled.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        appendLog("Liquid Glass Matrix Online.")
        appendLog("[TABLET MATRIX SUBSYSTEM READY]")

        setContent {
            JarvisThemeUI()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setSpeechRate(1.1f)
            speak("Liquid Glass JARVIS System online, sir.")
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_TTS")
    }

    private fun appendLog(msg: String) {
        logMessages.add(msg)
    }

    private fun getBatteryStatus(): String {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging
        val state = if (isCharging) "charging on mains" else "discharging"
        return "Battery capacity is at $level percent, currently $state."
    }

    private fun openAndroidApp(appKey: String): String {
        val packageMap = mapOf(
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "drive" to "com.google.android.apps.docs",
            "word" to "com.microsoft.office.word",
            "excel" to "com.microsoft.office.excel",
            "powerpoint" to "com.microsoft.office.powerpoint"
        )

        val pkg = packageMap[appKey.lowercase()]
        if (pkg != null) {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                startActivity(launchIntent)
                return "Launching $appKey, sir."
            }
        }

        val webFallbackMap = mapOf(
            "gmail" to "https://mail.google.com",
            "outlook" to "https://outlook.live.com",
            "drive" to "https://drive.google.com"
        )
        val url = webFallbackMap[appKey.lowercase()]
        if (url != null) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
            return "Opening web interface for $appKey, sir."
        }

        return "Application $appKey is not installed or unreachable, sir."
    }

      private suspend fun askAI(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://integrate.api.nvidia.com/v1/chat/completions"

            val json = JSONObject().apply {
                put("model", "meta/llama-3.1-8b-instruct")
                put("max_tokens", 200)
                val msgArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are JARVIS under Liquid Glass Protocol. Keep answers short, sharp, and address the user as 'sir'.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }
                put("messages", msgArray)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer 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")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext "Matrix link error: HTTP ${response.code}"
                val responseData = response.body?.string() ?: ""
                val resObj = JSONObject(responseData)
                resObj.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            }
        } catch (e: Exception) {
            "NVIDIA core offline, sir: ${e.message}"
        }
    }

    private fun processDirective(input: String) {
        val directive = input.trim()
        if (directive.isEmpty()) return

        appendLog("Input Directive: '$directive'")
        val low = directive.lowercase()

        statusText.value = "PROCESSING MATRIX..."
        statusColor.value = Color(0xFFFFCC00)

        CoroutineScope(Dispatchers.Main).launch {
            val response: String = when {
                low.contains("battery") -> getBatteryStatus()
                low.contains("diagnostics") -> "Diagnostics complete. Core integrity at 100%. Thermal levels nominal."
                low.contains("stealth") -> "Stealth protocol active. HUD minimized to low-frequency background mode."
                low.contains("scan") -> "Perimeter scan complete. Local network loopback clear."
                low.startsWith("open ") -> {
                    val app = low.removePrefix("open ").trim()
                    openAndroidApp(app)
                }
                else -> askAI(directive)
            }

            appendLog(response)
            speak(response)
            statusText.value = "COLD STANDBY"
            statusColor.value = Color(0xFF555555)
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Yes, sir?")
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText.value = "ACTIVE (LISTENING...)"
                statusColor.value = Color(0xFF00D2FF)
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    processDirective(matches[0])
                } else {
                    statusText.value = "COLD STANDBY"
                    statusColor.value = Color(0xFF555555)
                }
            }
            override fun onError(error: Int) {
                statusText.value = "COLD STANDBY"
                statusColor.value = Color(0xFF555555)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    @Composable
    fun JarvisThemeUI() {
        var inputText by remember { mutableStateOf("") }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0B132B)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "JARVIS COGNITIVE CORE - LIQUID GLASS",
                    color = Color(0xFF00D2FF),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Text(
                    text = "STATUS: ${statusText.value}",
                    color = statusColor.value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp, bottom = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541))
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        items(logMessages) { msg ->
                            Text(
                                text = ">> $msg",
                                color = Color(0xFFE0E0E0),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Enter system directive, sir...", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00D2FF),
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    trailingIcon = {
                        Button(
                            onClick = {
                                val query = inputText
                                inputText = ""
                                processDirective(query)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF))
                        ) {
                            Text("Send", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { startVoiceInput() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A86FF))
                    ) {
                        Text("Voice Hook")
                    }
                    Button(
                        onClick = { processDirective("battery") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50))
                    ) {
                        Text("Battery")
                    }
                    Button(
                        onClick = { processDirective("diagnostics") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50))
                    ) {
                        Text("Diagnostics")
                    }
                    Button(
                        onClick = { openAndroidApp("chrome") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50))
                    ) {
                        Text("Chrome")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        speechRecognizer.destroy()
        super.onDestroy()
    }
}
