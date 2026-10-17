package com.govtech.movies.showtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SeatsTest {
    @ParameterizedTest
    @ValueSource(strings = ["A1", "A12", "H1", "H12", "C7"])
    fun `seats inside an 8 by 12 theatre are valid`(label: String) {
        assertTrue(isValidSeat(label, rows = 8, seatsPerRow = 12))
    }

    @ParameterizedTest
    @ValueSource(strings = ["I1", "A13", "A0", "A01", "a1", "AA1", "1A", "A", "", " A1", "A1 ", "A100", "Z99"])
    fun `labels outside the layout or badly formed are invalid`(label: String) {
        assertFalse(isValidSeat(label, rows = 8, seatsPerRow = 12))
    }

    @Test
    fun `last row letter follows the row count`() {
        assertTrue(isValidSeat("Z1", rows = 26, seatsPerRow = 1))
        assertFalse(isValidSeat("B1", rows = 1, seatsPerRow = 1))
    }

    @Test
    fun `seats sort by row then by number, not as text`() {
        assertEquals(listOf("A2", "A10", "B1", "C3"), listOf("B1", "A10", "C3", "A2").sortedWith(seatOrder))
    }
}
