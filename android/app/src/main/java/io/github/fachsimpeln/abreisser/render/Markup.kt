package io.github.fachsimpeln.abreisser.render

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan

private val BOLD = Regex("""\*\*(.+?)\*\*""")
private val ITALIC = Regex("""\*(.+?)\*""")

/** Text with **bold** and *italic* markup for parts of a line. */
fun parseMarkup(text: String): CharSequence {
    val out = SpannableStringBuilder()
    var last = 0
    for (m in BOLD.findAll(text)) {
        appendItalic(out, text.substring(last, m.range.first))
        val start = out.length
        appendItalic(out, m.groupValues[1])
        out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        last = m.range.last + 1
    }
    appendItalic(out, text.substring(last))
    return out
}

private fun appendItalic(out: SpannableStringBuilder, text: String) {
    var last = 0
    for (m in ITALIC.findAll(text)) {
        out.append(text, last, m.range.first)
        val start = out.length
        out.append(m.groupValues[1])
        out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        last = m.range.last + 1
    }
    out.append(text, last, text.length)
}

/** Removes the markup markers, e.g. for list labels. */
fun stripMarkup(text: String) = text.replace("*", "")
