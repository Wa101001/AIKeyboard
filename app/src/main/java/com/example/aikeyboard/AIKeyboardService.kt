package com.example.aikeyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.Toast

class AIKeyboardService : InputMethodService() {

    override fun onCreateInputView(): View {
        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)

        val translateButton = keyboardView.findViewById<Button>(R.id.btn_translate)
        val toggleButton = keyboardView.findViewById<Button>(R.id.btn_toggle_lang)

        translateButton.setOnClickListener {
            val inputConnection: InputConnection? = currentInputConnection
            val text = inputConnection?.getSelectedText(0)?.toString()
            Toast.makeText(this, "Translate: $text", Toast.LENGTH_SHORT).show()
        }

        toggleButton.setOnClickListener {
            Toast.makeText(this, "Language toggled", Toast.LENGTH_SHORT).show()
        }

        return keyboardView
    }
}
