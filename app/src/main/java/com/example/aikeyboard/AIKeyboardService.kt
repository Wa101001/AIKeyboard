package com.example.aikeyboard

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class AIKeyboardService : InputMethodService() {

    private val TAG = "AIKeyboardDebug"
    private var isFrenchToEnglish = true

    override fun onCreateInputView(): View {
        Log.d(TAG, "✅ onCreateInputView triggered")
        Toast.makeText(this, "✅ Keyboard loaded", Toast.LENGTH_SHORT).show()

        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)

        val gptInputTextView = keyboardView.findViewById<TextView>(R.id.gpt_input)
        val translateButton = keyboardView.findViewById<Button>(R.id.btn_translate)
        val toggleButton = keyboardView.findViewById<Button>(R.id.btn_toggle_lang)

        val typedText = StringBuilder()

        // Confirm translate button exists
        if (translateButton == null) {
            Toast.makeText(this, "❌ Translate button not found!", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "❌ Translate button is null")
        } else {
            Log.d(TAG, "✅ Translate button found")
        }

        // Translate button logic
        translateButton?.setOnClickListener {
            Toast.makeText(this, "🔥 Translate clicked", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "🔥 Translate clicked")

            val inputText = typedText.toString()
            if (inputText.isNotBlank()) {
                Toast.makeText(this, "Translating: $inputText", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "🚀 Sending to GPT: $inputText")

                callGPT(inputText) { result ->
                    result?.let {
                        runOnUiThread {
                            gptInputTextView.text = "💬 ${it.trim()}"
                            typedText.clear()
                            Log.d(TAG, "✅ GPT response received: ${it.trim()}")
                        }
                    } ?: runOnUiThread {
                        Toast.makeText(this, "❌ Translation failed", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "❌ GPT returned null")
                    }
                }
            } else {
                Toast.makeText(this, "Nothing to translate", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "⚠️ Input was blank")
            }
        }

        // AZERTY keymap
        val keyMap = mapOf(
            R.id.key_a to "a", R.id.key_z to "z", R.id.key_e to "e", R.id.key_r to "r",
            R.id.key_t to "t", R.id.key_y to "y", R.id.key_u to "u", R.id.key_i to "i",
            R.id.key_o to "o", R.id.key_p to "p", R.id.key_q to "q", R.id.key_s to "s",
            R.id.key_d to "d", R.id.key_f to "f", R.id.key_g to "g", R.id.key_h to "h",
            R.id.key_j to "j", R.id.key_k to "k", R.id.key_l to "l", R.id.key_m to "m",
            R.id.key_w to "w", R.id.key_x to "x", R.id.key_c to "c", R.id.key_v to "v",
            R.id.key_b to "b", R.id.key_n to "n"
        )

        keyMap.forEach { (id, char) ->
            keyboardView.findViewById<Button>(id)?.setOnClickListener {
                typedText.append(char)
                gptInputTextView.text = typedText.toString()
                Log.d(TAG, "🔤 Typed: $typedText")
            }
        }

        keyboardView.findViewById<Button>(R.id.key_space)?.setOnClickListener {
            typedText.append(" ")
            gptInputTextView.text = typedText.toString()
            Log.d(TAG, "🔤 Added space")
        }

        keyboardView.findViewById<Button>(R.id.key_delete)?.setOnClickListener {
            if (typedText.isNotEmpty()) {
                typedText.deleteCharAt(typedText.length - 1)
                gptInputTextView.text = typedText.toString()
                Log.d(TAG, "🔙 Deleted char → $typedText")
            }
        }

        keyboardView.findViewById<Button>(R.id.key_enter)?.setOnClickListener {
            typedText.append("\n")
            gptInputTextView.text = typedText.toString()
            Log.d(TAG, "↩️ New line added")
        }

        toggleButton?.setOnClickListener {
            isFrenchToEnglish = !isFrenchToEnglish

            val langLabel = if (isFrenchToEnglish) "FR → EN" else "EN → FR"
            toggleButton.text = langLabel

            Toast.makeText(this, "Direction: $langLabel", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "🔁 Language toggled → $langLabel")
        }


        return keyboardView
    }
    private fun getPromptInstruction(): String {
        return if (isFrenchToEnglish)
            "Translate this from French to English informally"
        else
            "Translate this from English to French informally"
    }

    private fun callGPT(userInput: String, callback: (String?) -> Unit) {
        val TAG = "AIKeyboardDebug"
        val apiKey = BuildConfig.OPENAI_API_KEY
        val url = "https://api.openai.com/v1/chat/completions"

        val jsonPayload = """
        {
            "model": "gpt-3.5-turbo",
            "messages": [
                {
                    "role": "system",
                    "content": "You are a casual French-English translator. Always return informal, friendly language."
                },
                {
                    "role": "user",
                    "content": "${getPromptInstruction()}: $userInput"

                }
            ],
            "temperature": 0.7
        }
    """.trimIndent()

        val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                Log.e(TAG, "❌ GPT request failed: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val resString = response.body?.string()
                Log.d(TAG, "🪵 Raw GPT response:\n$resString")

                try {
                    val json = JSONObject(resString)
                    if (json.has("choices")) {
                        val message = json
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                        callback(message)
                    } else if (json.has("error")) {
                        val errorMsg = json.getJSONObject("error").getString("message")
                        Log.e(TAG, "❌ GPT API Error: $errorMsg")
                        callback("GPT Error: $errorMsg")
                    } else {
                        Log.e(TAG, "❌ Unexpected GPT response structure")
                        callback(null)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e(TAG, "❌ JSON parsing error: ${e.message}")
                    callback(null)
                }
            }
        })
    }

    private fun runOnUiThread(action: () -> Unit) {
        val handler = Handler(mainLooper)
        handler.post { action() }
    }
}
