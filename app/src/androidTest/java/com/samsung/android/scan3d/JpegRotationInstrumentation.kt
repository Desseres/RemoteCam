package com.samsung.android.scan3d

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import androidx.exifinterface.media.ExifInterface
import com.samsung.android.scan3d.util.JpegRotation
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.abs

/** Exercises Android's real Bitmap codec with synthetic images, never the camera. */
class JpegRotationInstrumentation : Instrumentation() {
    private var previewContinuity = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        previewContinuity = arguments?.getString("previewContinuity") == "true"
        start()
    }

    override fun onStart() {
        try {
            if (previewContinuity) {
                PreviewContinuityCheck.run(this)
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: WebRTC/JPEG preview toggles, expanded settings and background/return preserve capture\n") })
                return
            }
            verifyOrientations()
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: all 8 EXIF transforms; dimensions, corner colors, metadata and identity path\n") })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL: ${error.stackTraceToString()}\n") })
        }
    }

    private fun verifyOrientations() {
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        val corners = listOf(listOf(0, 1, 2, 3), listOf(1, 0, 3, 2), listOf(3, 2, 1, 0),
            listOf(2, 3, 0, 1), listOf(0, 2, 1, 3), listOf(2, 0, 3, 1),
            listOf(3, 1, 2, 0), listOf(1, 3, 0, 2))
        val original = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(original)
        colors.forEachIndexed { index, color ->
            val left = (index % 2) * 60f
            val top = (index / 2) * 40f
            canvas.drawRect(left, top, left + 60, top + 40, Paint().apply { this.color = color })
        }
        val file = File.createTempFile("rotation-", ".jpg", targetContext.cacheDir)
        try {
            for (orientation in 1..8) {
                file.outputStream().use { check(original.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
                ExifInterface(file).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes()
                }
                val input = file.readBytes()
                val output = JpegRotation.normalize(input)
                if (orientation == 1) check(output === input) { "Normal JPEG was re-encoded" }
                val outputOrientation = ExifInterface(ByteArrayInputStream(output)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                check(outputOrientation in 0..1) { "EXIF $orientation: stale output orientation $outputOrientation" }
                val image = checkNotNull(BitmapFactory.decodeByteArray(output, 0, output.size))
                try {
                    check(image.width == if (orientation >= 5) 80 else 120)
                    check(image.height == if (orientation >= 5) 120 else 80)
                    corners[orientation - 1].forEachIndexed { index, colorIndex ->
                        val actual = image.getPixel(image.width * (if (index % 2 == 0) 1 else 3) / 4,
                            image.height * (if (index / 2 == 0) 1 else 3) / 4)
                        val expected = colors[colorIndex]
                        check(abs(Color.red(actual) - Color.red(expected)) < 25 &&
                            abs(Color.green(actual) - Color.green(expected)) < 25 &&
                            abs(Color.blue(actual) - Color.blue(expected)) < 25) {
                            "EXIF $orientation: incorrect corner $index"
                        }
                    }
                } finally { image.recycle() }
            }
        } finally { original.recycle(); file.delete() }
    }
}
