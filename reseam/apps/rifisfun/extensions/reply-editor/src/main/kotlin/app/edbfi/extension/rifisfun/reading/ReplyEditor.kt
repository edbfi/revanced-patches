// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("ReplyEditor")

package app.edbfi.extension.rifisfun.reading

import android.graphics.Rect
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import app.reseam.runtime.Export

@Export
fun watch(root: View?, editorId: Int) {
    val editor = root?.findViewById<View>(editorId) as? EditText ?: return
    editor.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(text: Editable?) {
            if (!editor.hasFocus()) return
            editor.post {
                if (!editor.hasFocus()) return@post
                val layout = editor.layout ?: return@post
                val caret = editor.selectionEnd
                if (caret !in 0..layout.text.length) return@post
                val line = layout.getLineForOffset(caret)
                val top = editor.totalPaddingTop + layout.getLineTop(line)
                val bottom = editor.totalPaddingTop + layout.getLineBottom(line)
                editor.requestRectangleOnScreen(Rect(0, top, editor.width, bottom))
            }
        }
    })
}
