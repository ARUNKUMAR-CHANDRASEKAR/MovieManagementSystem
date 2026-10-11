package com.govtech.movies.movie

/**
 * Detects the image type from the file's first bytes ("magic numbers").
 * We do not trust the type the browser claims: serving an uploaded HTML file as an
 * "image" could otherwise run script in the user's browser.
 *
 * Returns the MIME type, or null when the bytes are not a supported image.
 */
fun detectImageType(data: ByteArray): String? {
    fun startsWith(vararg bytes: Int, offset: Int = 0) =
        data.size >= offset + bytes.size && bytes.indices.all { data[offset + it] == bytes[it].toByte() }

    return when {
        startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
        startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
        startsWith(0x47, 0x49, 0x46, 0x38) -> "image/gif" // "GIF8"
        startsWith(0x52, 0x49, 0x46, 0x46) && startsWith(0x57, 0x45, 0x42, 0x50, offset = 8) -> "image/webp" // "RIFF....WEBP"
        else -> null
    }
}
