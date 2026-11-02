package io.github.fachsimpeln.abreisser.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import io.github.fachsimpeln.abreisser.model.Align
import io.github.fachsimpeln.abreisser.model.Direction
import io.github.fachsimpeln.abreisser.model.FlyerState
import io.github.fachsimpeln.abreisser.model.FontChoice
import io.github.fachsimpeln.abreisser.model.ImagePos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

const val PAGE_W = 210f
const val PAGE_H = 297f

/** Millimetres per typographic point. */
private const val PT = 25.4f / 72f

/**
 * Drawing units per millimetre. Text is laid out in these units so that the
 * integer font metrics StaticLayout uses stay precise at small font sizes.
 */
private const val U = 100f

private val CUT_COLOR = Color.rgb(0x9a, 0xa1, 0xad)

data class Metrics(
    val innerW: Float,
    val innerH: Float,
    val zettelH: Float,
    val fit: Int,
    val stripW: Float,
) {
    /** Usable width inside a strip, after its padding. */
    fun cross(padding: Float) = max(0f, stripW - 2 * padding)
}

sealed interface Issue {
    data class Rows(val fit: Int) : Issue
    data class Scheme(val link: String) : Issue
    data object QrSmall : Issue
    data object TextCut : Issue
    data object HeadCut : Issue
}

data class QrInfo(val sizeMm: Float, val dotMm: Float?, val limitedFrom: Float?, val tooSmall: Boolean)

data class Analysis(val metrics: Metrics, val issues: List<Issue>, val qr: QrInfo?)

fun metrics(state: FlyerState): Metrics {
    val (page, header, strips) = state
    val innerW = PAGE_W - 2 * page.marginX
    val innerH = PAGE_H - 2 * page.marginY
    val zettelH = (if (header.enabled) header.height else 0f) + strips.length
    val fit = max(0, floor((innerH + page.gap) / (zettelH + page.gap)).toInt())
    val stripW = innerW / max(1, strips.count)
    return Metrics(innerW, innerH, zettelH, fit, stripW)
}

/** Lays out and draws A4 pages of tear-off flyers. All page geometry is in millimetres. */
class FlyerRenderer(private val images: ImageStore) {

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val qrPaint = Paint().apply { isAntiAlias = false }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = CUT_COLOR
        strokeWidth = 0.25f * U
        pathEffect = DashPathEffect(floatArrayOf(0.9f * U, 0.6f * U), 0f)
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = CUT_COLOR
        strokeWidth = 0.2f * U
    }
    private val scissorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CUT_COLOR
        textSize = 4f * U
    }

    /** Draws one page. The canvas origin is the page's top-left corner. */
    fun drawPage(canvas: Canvas, state: FlyerState, pxPerMm: Float) {
        val m = metrics(state)
        val page = state.page
        canvas.save()
        canvas.scale(pxPerMm / U, pxPerMm / U)
        canvas.clipRect(0f, 0f, PAGE_W * U, PAGE_H * U)
        canvas.drawColor(Color.WHITE)
        canvas.translate(page.marginX * U, page.marginY * U)

        val header = if (state.header.enabled) headerLayout(state, m.innerW) else null
        val strips = (0 until state.strips.count).map { stripLayout(state, it, m.stripW) }

        for (r in 0 until page.rows) {
            val y = r * (m.zettelH + page.gap)
            if (y > PAGE_H) break
            canvas.save()
            canvas.translate(0f, y * U)
            drawZettel(canvas, state, m, header, strips)
            canvas.restore()
            if (page.cutLines && r < page.rows - 1) drawCut(canvas, m.innerW, y + m.zettelH + page.gap / 2)
        }
        canvas.restore()
    }

    fun analyze(state: FlyerState): Analysis {
        val m = metrics(state)
        val s = state.strips
        val issues = mutableListOf<Issue>()
        if (state.page.rows > m.fit) issues += Issue.Rows(m.fit)

        val links = (0 until s.count).map { state.stripContent(it).link }.filter { it.isNotEmpty() }.toSet()
        val cross = m.cross(s.padding)
        var qr: QrInfo? = null
        if (links.isNotEmpty()) {
            val qrMm = min(s.qrSize, cross)
            var worst: Float? = null
            for (link in links) {
                if (!SCHEME.containsMatchIn(link)) issues += Issue.Scheme(link)
                QrCodes.matrix(link)?.let { worst = min(worst ?: Float.MAX_VALUE, qrMm / it.width) }
            }
            val tooSmall = (worst ?: Float.MAX_VALUE) < 0.4f
            qr = QrInfo(qrMm, worst, s.qrSize.takeIf { it > cross }, tooSmall)
            if (tooSmall) issues += Issue.QrSmall
        }

        if ((0 until s.count).any { stripLayout(state, it, m.stripW).textCut }) issues += Issue.TextCut
        if (state.header.enabled && headerLayout(state, m.innerW).textCut) issues += Issue.HeadCut
        return Analysis(m, issues, qr)
    }

    /* ---------- main part ---------- */

    private class HeaderLayout(
        val height: Float,
        val image: Bitmap?,
        val imageRect: RectF?,
        val imageBg: Int?,
        val title: StaticLayout?,
        val desc: StaticLayout?,
        val textX: Float,
        val textY: Float,
        val textCut: Boolean,
    )

    private fun headerLayout(state: FlyerState, innerW: Float): HeaderLayout {
        val h = state.header
        val padX = 4f
        val padY = 3f
        val gap = 4f
        val cw = max(0f, innerW - 2 * padX)
        val ch = max(0f, h.height - 2 * padY)
        val bitmap = h.image?.let { images.bitmap(it, h.imageRecolor) }
        val inset = if (h.imageBgOn) 0.8f else 0f

        fun texts(width: Float): Triple<StaticLayout?, StaticLayout?, Float> {
            val title = h.title.takeIf { it.isNotEmpty() }?.let {
                val p = textPaint(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD), h.titleSize)
                layout(it, p, width, h.align, 1.15f * h.titleSize * PT)
            }
            val desc = h.desc.takeIf { it.isNotEmpty() }?.let {
                val p = textPaint(Typeface.SANS_SERIF, h.descSize)
                layout(it, p, width, h.align, 1.3f * h.descSize * PT)
            }
            val height = (title?.let { it.height / U + 1.5f } ?: 0f) + (desc?.let { it.height / U } ?: 0f)
            return Triple(title, desc, height)
        }

        if (bitmap != null && h.imagePos == ImagePos.TOP) {
            val box = fitInto(bitmap, cw - 2 * inset, min(h.imageSize, ch) - 2 * inset)
            val boxW = box.first + 2 * inset
            val boxH = box.second + 2 * inset
            val (title, desc, th) = texts(cw)
            val total = boxH + gap + th
            val top = padY + (ch - total) / 2
            val imgX = padX + (cw - boxW) / 2
            return HeaderLayout(
                h.height, bitmap, RectF(imgX, top, imgX + boxW, top + boxH), h.imageBg.takeIf { h.imageBgOn },
                title, desc, padX, top + boxH + gap, total > ch + 0.1f,
            )
        }

        var imageRect: RectF? = null
        var textX = padX
        var textW = cw
        if (bitmap != null) {
            val boxW = min(h.imageSize, cw)
            val contentH = (boxW - 2 * inset) * bitmap.height / bitmap.width
            val boxH = min(contentH + 2 * inset, ch)
            val top = padY + (ch - boxH) / 2
            val left = if (h.imagePos == ImagePos.RIGHT) padX + cw - boxW else padX
            imageRect = RectF(left, top, left + boxW, top + boxH)
            textW = max(0f, cw - boxW - gap)
            if (h.imagePos == ImagePos.LEFT) textX = padX + boxW + gap
        }
        val (title, desc, th) = texts(textW)
        return HeaderLayout(
            h.height, bitmap, imageRect, h.imageBg.takeIf { h.imageBgOn },
            title, desc, textX, padY + (ch - th) / 2, th > ch + 0.1f,
        )
    }

    private fun drawHeader(canvas: Canvas, innerW: Float, l: HeaderLayout) {
        canvas.save()
        canvas.clipRect(0f, 0f, innerW * U, l.height * U)
        if (l.image != null && l.imageRect != null) drawImage(canvas, l.image, l.imageRect, l.imageBg)
        canvas.translate(l.textX * U, l.textY * U)
        l.title?.let {
            it.draw(canvas)
            canvas.translate(0f, it.height + 1.5f * U)
        }
        l.desc?.draw(canvas)
        canvas.restore()
    }

    /* ---------- strips ---------- */

    /** A strip laid out horizontally, before it is rotated into place. */
    private class StripLayout(
        val image: Bitmap?,
        val imageW: Float,
        val text: StaticLayout,
        val textX: Float,
        val qr: String?,
        val qrX: Float,
        val qrSize: Float,
        val textCut: Boolean,
    )

    private fun stripLayout(state: FlyerState, i: Int, stripW: Float): StripLayout {
        val s = state.strips
        val content = state.stripContent(i)
        val pad = s.padding
        val cross = max(1f, stripW - 2 * pad)
        val gap = 1.5f
        val length = s.length - 2 * pad

        val bitmap = content.image?.let { images.bitmap(it, s.imageRecolor) }
        val imageW = if (bitmap != null) s.imageSize else 0f
        val qr = content.link.takeIf { it.isNotEmpty() && QrCodes.matrix(it) != null }
        val qrSize = if (qr != null) min(s.qrSize, cross) else 0f

        val gaps = listOf(bitmap != null, qr != null).count { it } * gap
        val textW = max(0.5f, length - imageW - qrSize - gaps)
        val x = if (bitmap != null) pad + imageW + gap else pad

        val style = (if (s.bold) Typeface.BOLD else 0) or (if (s.italic) Typeface.ITALIC else 0)
        val paint = textPaint(Typeface.create(family(s.font), style), s.fontSize).apply {
            isUnderlineText = s.underline
        }
        val text = layout(parseMarkup(content.text), paint, textW, s.align, 1.15f * s.fontSize * PT)
        val textCut = text.height / U > cross + 0.1f
        return StripLayout(bitmap, imageW, text, x, qr, x + textW + gap, qrSize, textCut)
    }

    private fun drawStrip(canvas: Canvas, state: FlyerState, stripW: Float, l: StripLayout) {
        val s = state.strips
        val pad = s.padding
        val cross = max(1f, stripW - 2 * pad)
        val boxH = stripW
        canvas.save()
        canvas.clipRect(0f, 0f, stripW * U, s.length * U)
        canvas.translate(stripW / 2 * U, s.length / 2 * U)
        canvas.rotate(if (s.direction == Direction.UP) -90f else 90f)
        canvas.translate(-s.length / 2 * U, -boxH / 2 * U)
        val mid = boxH / 2

        if (l.image != null) {
            val rect = RectF(pad, mid - cross / 2, pad + l.imageW, mid + cross / 2)
            drawImage(canvas, l.image, rect, s.imageBg.takeIf { s.imageBgOn })
        }

        canvas.save()
        val textH = l.text.height / U
        val top = mid - min(textH, cross) / 2
        canvas.clipRect(l.textX * U, (mid - cross / 2) * U, (l.textX * U + l.text.width), (mid + cross / 2) * U)
        canvas.translate(l.textX * U, top * U)
        l.text.draw(canvas)
        canvas.restore()

        if (l.qr != null) drawQr(canvas, l.qr, l.qrX, mid - l.qrSize / 2, l.qrSize)
        canvas.restore()
    }

    /* ---------- page parts ---------- */

    private fun drawZettel(
        canvas: Canvas,
        state: FlyerState,
        m: Metrics,
        header: HeaderLayout?,
        strips: List<StripLayout>,
    ) {
        val headH = header?.height ?: 0f
        if (header != null) drawHeader(canvas, m.innerW, header)

        canvas.save()
        canvas.translate(0f, headH * U)
        strips.forEachIndexed { i, l ->
            canvas.save()
            canvas.translate(i * m.stripW * U, 0f)
            drawStrip(canvas, state, m.stripW, l)
            canvas.restore()
        }
        val len = state.strips.length * U
        if (header != null) canvas.drawLine(0f, 0f, m.innerW * U, 0f, dashPaint)
        for (i in 1 until strips.size) {
            val x = i * m.stripW * U
            canvas.drawLine(x, 0f, x, len, dashPaint)
        }
        canvas.restore()

        if (state.page.outline) {
            val o = 0.1f * U
            canvas.drawRect(-o, -o, m.innerW * U + o, m.zettelH * U + o, outlinePaint)
        }
    }

    private fun drawCut(canvas: Canvas, innerW: Float, y: Float) {
        canvas.drawLine(0f, y * U, innerW * U, y * U, dashPaint)
        val glyph = "✂"
        val w = scissorPaint.measureText(glyph)
        val fm = scissorPaint.fontMetrics
        fillPaint.color = Color.WHITE
        canvas.drawRect(0f, (y - 2f) * U, w, (y + 2f) * U, fillPaint)
        canvas.drawText(glyph, 0f, y * U - (fm.ascent + fm.descent) / 2, scissorPaint)
    }

    /** Draws the image contained in [rect] (mm), with an optional rounded backdrop. */
    private fun drawImage(canvas: Canvas, bitmap: Bitmap, rect: RectF, bg: Int?) {
        var r = rect
        if (bg != null) {
            fillPaint.color = bg
            val radius = 0.8f * U
            canvas.drawRoundRect(r.left * U, r.top * U, r.right * U, r.bottom * U, radius, radius, fillPaint)
            r = RectF(r.left + 0.8f, r.top + 0.8f, r.right - 0.8f, r.bottom - 0.8f)
        }
        val (w, h) = fitInto(bitmap, r.width(), r.height())
        if (w <= 0f || h <= 0f) return
        val left = r.left + (r.width() - w) / 2
        val top = r.top + (r.height() - h) / 2
        canvas.drawBitmap(bitmap, null, RectF(left * U, top * U, (left + w) * U, (top + h) * U), bitmapPaint)
    }

    private fun drawQr(canvas: Canvas, text: String, x: Float, y: Float, size: Float) {
        val matrix = QrCodes.matrix(text) ?: return
        val n = matrix.width
        val cell = size * U / n
        qrPaint.color = Color.WHITE
        canvas.drawRect(x * U, y * U, x * U + size * U, y * U + size * U, qrPaint)
        // One rectangle per horizontal run keeps the PDF small and free of seams
        val path = Path()
        for (row in 0 until n) {
            var col = 0
            while (col < n) {
                if (!matrix.get(col, row)) { col++; continue }
                val start = col
                while (col < n && matrix.get(col, row)) col++
                path.addRect(
                    x * U + start * cell, y * U + row * cell,
                    x * U + col * cell, y * U + (row + 1) * cell, Path.Direction.CW,
                )
            }
        }
        qrPaint.color = Color.BLACK
        canvas.drawPath(path, qrPaint)
    }

    /* ---------- text ---------- */

    private fun textPaint(typeface: Typeface, sizePt: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        color = Color.BLACK
        textSize = sizePt * PT * U
        flags = flags or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG
    }

    /** A paragraph layout [widthMm] wide with CSS-like line height [lineHeightMm]. */
    private fun layout(text: CharSequence, paint: TextPaint, widthMm: Float, align: Align, lineHeightMm: Float): StaticLayout {
        val fmi = paint.fontMetricsInt
        val fm = (fmi.descent - fmi.ascent).toFloat()
        val alignment = when (align) {
            Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, (widthMm * U).toInt()))
            .setAlignment(alignment)
            .setIncludePad(false)
            .setLineSpacing(0f, if (fm > 0f) lineHeightMm * U / fm else 1f)
        if (align == Align.JUSTIFY) builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        return builder.build()
    }

    private fun family(font: FontChoice): Typeface = when (font) {
        FontChoice.SANS -> Typeface.SANS_SERIF
        FontChoice.SERIF -> Typeface.SERIF
        FontChoice.MONO -> Typeface.MONOSPACE
        FontChoice.HAND -> Typeface.create("casual", Typeface.NORMAL)
    }

    private companion object {
        val SCHEME = Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE)

        /** Largest size with the bitmap's aspect ratio that fits in [w] × [h]. */
        fun fitInto(bitmap: Bitmap, w: Float, h: Float): Pair<Float, Float> {
            if (w <= 0f || h <= 0f) return 0f to 0f
            val scale = min(w / bitmap.width, h / bitmap.height)
            return bitmap.width * scale to bitmap.height * scale
        }
    }
}
