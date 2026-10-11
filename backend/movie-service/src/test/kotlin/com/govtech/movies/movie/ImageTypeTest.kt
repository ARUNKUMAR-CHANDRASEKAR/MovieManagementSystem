package com.govtech.movies.movie

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ImageTypeTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `detects jpeg`() = assertEquals("image/jpeg", detectImageType(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0)))

    @Test
    fun `detects png`() =
        assertEquals("image/png", detectImageType(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0)))

    @Test
    fun `detects gif`() = assertEquals("image/gif", detectImageType("GIF89a...".toByteArray()))

    @Test
    fun `detects webp`() = assertEquals("image/webp", detectImageType("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()))

    @Test
    fun `RIFF that is not WEBP is rejected`() = assertNull(detectImageType("RIFF\u0000\u0000\u0000\u0000WAVEfmt ".toByteArray()))

    @Test
    fun `empty input is rejected`() = assertNull(detectImageType(ByteArray(0)))

    @Test
    fun `input shorter than the signature is rejected`() = assertNull(detectImageType(bytes(0x89, 0x50, 0x4E)))

    @Test
    fun `text and html are rejected`() {
        assertNull(detectImageType("<svg onload=alert(1)>".toByteArray()))
        assertNull(detectImageType("hello".toByteArray()))
    }
}
