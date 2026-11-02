package io.github.fachsimpeln.abreisser.print

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import io.github.fachsimpeln.abreisser.model.FlyerState
import io.github.fachsimpeln.abreisser.render.FlyerRenderer
import io.github.fachsimpeln.abreisser.render.PAGE_H
import io.github.fachsimpeln.abreisser.render.PAGE_W
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/** PDF points per millimetre. */
private const val PT_PER_MM = 72f / 25.4f

/** Writes the flyer as an A4 PDF; [include] picks the zero-based pages to write. */
fun writePdf(renderer: FlyerRenderer, state: FlyerState, out: OutputStream, include: (Int) -> Boolean = { true }) {
    val doc = PdfDocument()
    try {
        val w = Math.round(PAGE_W * PT_PER_MM)
        val h = Math.round(PAGE_H * PT_PER_MM)
        for (i in 0 until state.page.pages) {
            if (!include(i)) continue
            val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
            renderer.drawPage(page.canvas, state, PT_PER_MM)
            doc.finishPage(page)
        }
        doc.writeTo(out)
    } finally {
        doc.close()
    }
}

/** Writes the PDF into the app's cache, for sharing. */
fun exportPdf(context: Context, renderer: FlyerRenderer, state: FlyerState, name: String): File {
    val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "$name.pdf")
    FileOutputStream(file).use { writePdf(renderer, state, it) }
    return file
}

fun printFlyer(context: Context, renderer: FlyerRenderer, state: FlyerState, jobName: String) {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val attributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
        .build()
    manager.print(jobName, FlyerPrintAdapter(renderer, state, jobName), attributes)
}

private class FlyerPrintAdapter(
    private val renderer: FlyerRenderer,
    private val state: FlyerState,
    private val name: String,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder("$name.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(state.page.pages)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        val all = pages.any { it == PageRange.ALL_PAGES }
        val include = { i: Int -> all || pages.any { i in it.start..it.end } }
        try {
            FileOutputStream(destination.fileDescriptor).use { writePdf(renderer, state, it, include) }
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
            return
        }
        if (cancellationSignal.isCanceled) {
            callback.onWriteCancelled()
            return
        }
        val written = (0 until state.page.pages).filter(include)
        val ranges = if (all) arrayOf(PageRange.ALL_PAGES) else written.map { PageRange(it, it) }.toTypedArray()
        callback.onWriteFinished(ranges)
    }
}
