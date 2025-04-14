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
                            // Insert response into real focused app field
                            currentInputConnection?.commitText(it.trim(), 1)

                           // Reset fake input bar
                            typedText.clear()
                            gptInputTextView.text = ""

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
                Log.d(TAG, "🔙 Deleted from fake bar → $typedText")
            } else {
                // Always delete 1 char from app field as a fallback
                currentInputConnection?.deleteSurroundingText(1, 0)
                Log.d(TAG, "⌫ Deleted from app input field")
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
        val apiKey = BuildConfig.OPENAI_API_KEY
        val url = "https://api.openai.com/v1/chat/completions"

        // Build JSON payload safely
        val messageArray = org.json.JSONArray()
        messageArray.put(JSONObject().put("role", "system").put("content", getPromptInstruction()))
        messageArray.put(JSONObject().put("role", "user").put("content", userInput))

        val jsonBody = JSONObject()
        jsonBody.put("model", "gpt-3.5-turbo")
        jsonBody.put("messages", messageArray)
        jsonBody.put("temperature", 0.7)

        val mediaType = "application/json".toMediaType()
        val body = jsonBody.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "❌ GPT request failed: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val resString = response.body?.string()
                Log.d(TAG, "🪵 Raw GPT response:\n$resString")

                try {
                    val json = JSONObject(resString)
                    val message = json
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    callback(message)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ GPT parse failed: ${e.message}")
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
