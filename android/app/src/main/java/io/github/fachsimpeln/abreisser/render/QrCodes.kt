package io.github.fachsimpeln.abreisser.render

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR codes with error correction M and a one-module quiet zone, cached by text. */
object QrCodes {
    private val cache = HashMap<String, BitMatrix?>()

    @Synchronized
    fun matrix(text: String): BitMatrix? = cache.getOrPut(text) {
        try {
            QRCodeWriter().encode(
                text, BarcodeFormat.QR_CODE, 0, 0,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                    EncodeHintType.MARGIN to 1,
                ),
            )
        } catch (e: Exception) {
            null
        }
    }
}
