package com.samsung.android.scan3d.http

object Mjpeg {
    fun header(length: Int): ByteArray {
        require(length >= 0)
        return "--FRAME\r\nContent-Type: image/jpeg\r\nContent-Length: $length\r\n\r\n"
            .toByteArray(Charsets.US_ASCII)
    }
    val terminator = "\r\n".toByteArray(Charsets.US_ASCII)
}
