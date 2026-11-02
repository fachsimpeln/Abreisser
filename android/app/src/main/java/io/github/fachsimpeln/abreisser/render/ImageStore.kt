package io.github.fachsimpeln.abreisser.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Loads the images the flyer uses. Picked images are copied into the app's
 * private storage so they survive restarts; decoded bitmaps are cached.
 */
class ImageStore(context: Context) {
    private val dir = File(context.filesDir, "images").apply { mkdirs() }
    private val resolver = context.contentResolver
    private val cache = HashMap<String, Bitmap?>()
    private val recolored = HashMap<String, Bitmap?>()

    /** Copies the picked image and returns the path of the copy, or null if it can't be read. */
    fun import(uri: Uri): String? {
        val file = File(dir, UUID.randomUUID().toString())
        return try {
            resolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: return null
            if (decode(file.path) == null) {
                file.delete()
                null
            } else file.path
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    /** Deletes stored images that the state no longer refers to. */
    fun prune(keep: Set<String>) {
        dir.listFiles()?.forEach { f ->
            if (f.path !in keep) {
                f.delete()
                synchronized(this) {
                    cache.remove(f.path)
                    recolored.remove(f.path)
                }
            }
        }
    }

    @Synchronized
    fun bitmap(path: String, recolor: Boolean): Bitmap? {
        if (!recolor) return cache.getOrPut(path) { decode(path) }
        return recolored.getOrPut(path) {
            cache.getOrPut(path) { decode(path) }?.let(::recolorWhite)
        }
    }

    private fun decode(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, opts)
    }

    private companion object {
        const val MAX_SIDE = 2000
    }
}

/**
 * White artwork on a transparent background (e.g. logo lettering) vanishes on
 * paper. Recolor only the white areas that touch transparency to black, so
 * white shapes enclosed by other colors (inside a sign, say) stay white.
 */
fun recolorWhite(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)

    fun isClear(i: Int) = Color.alpha(px[i]) < 10
    fun isLight(i: Int): Boolean {
        val c = px[i]
        return Color.alpha(c) >= 10 && minOf(Color.red(c), Color.green(c), Color.blue(c)) > 170
    }

    val seen = BooleanArray(w * h)
    val stack = IntArray(w * h)
    var top = 0
    fun visitNeighbours(i: Int) {
        val x = i % w
        if (i - w >= 0 && !seen[i - w] && isLight(i - w)) { seen[i - w] = true; stack[top++] = i - w }
        if (i + w < w * h && !seen[i + w] && isLight(i + w)) { seen[i + w] = true; stack[top++] = i + w }
        if (x > 0 && !seen[i - 1] && isLight(i - 1)) { seen[i - 1] = true; stack[top++] = i - 1 }
        if (x < w - 1 && !seen[i + 1] && isLight(i + 1)) { seen[i + 1] = true; stack[top++] = i + 1 }
    }

    for (i in 0 until w * h) if (isClear(i)) visitNeighbours(i)
    while (top > 0) {
        val i = stack[--top]
        px[i] = Color.argb(Color.alpha(px[i]), 0, 0, 0)
        visitNeighbours(i)
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}
