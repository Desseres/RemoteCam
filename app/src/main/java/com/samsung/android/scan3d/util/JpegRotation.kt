package com.samsung.android.scan3d.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Make JPEG orientation visible even to MJPEG decoders that ignore EXIF. */
object JpegRotation {
    fun normalize(jpeg: ByteArray): ByteArray {
        val orientation = ExifInterface(ByteArrayInputStream(jpeg))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        if (orientation !in 2..8) return jpeg // Camera already rotated the pixels: no recompression.
        val matrix = Matrix().apply {
            // EXIF transforms, including mirrored orientations. Bitmap.createBitmap
            // translates the transformed bounds to the output origin automatically.
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> setValues(floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
                6 -> setRotate(90f)
                7 -> setValues(floatArrayOf(0f, -1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f))
                8 -> setRotate(270f)
            }
        }
        val original = checkNotNull(BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)) { "Cannot decode camera JPEG" }
        var rotated: Bitmap? = null
        try {
            rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, false)
            return ByteArrayOutputStream(jpeg.size).use { output ->
                // Preserve dimensions; avoid a second low-quality quantization pass.
                // This compatibility fallback is still lossy and can increase bytes/CPU.
                check(rotated.compress(Bitmap.CompressFormat.JPEG, 100, output)) { "Cannot encode rotated JPEG" }
                output.toByteArray() // No stale EXIF orientation to trigger a second rotation.
            }
        } finally {
            if (rotated !== original) rotated?.recycle()
            original.recycle()
        }
    }
}
