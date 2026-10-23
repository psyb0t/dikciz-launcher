package org.fossify.home.dikciz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class DikcizUssdResponseTest {
    @Test
    fun emptyExactAndShortenedAscii() {
        assertEquals(DikcizUssdResponseText("", 0, false), boundedUssdResponse("", 1))
        assertEquals(DikcizUssdResponseText("abc", 3, false), boundedUssdResponse("abc", 3))
        assertEquals(DikcizUssdResponseText("ab", 3, true), boundedUssdResponse("abc", 2))
    }

    @Test
    fun supplementaryCharactersRemainWhole() {
        val emoji = "\uD83D\uDC08"
        assertEquals(DikcizUssdResponseText(emoji, 1, false), boundedUssdResponse(emoji, 1))
        assertEquals(DikcizUssdResponseText("A" + emoji, 3, true), boundedUssdResponse("A" + emoji + "B", 2))
    }

    @Test
    fun combiningMarksCountAsCodePoints() {
        assertEquals(DikcizUssdResponseText("e", 2, true), boundedUssdResponse("e\u0301", 1))
    }

    @Test
    fun maximumAndInvalidBudgets() {
        val full = "x".repeat(MAXIMUM_USSD_RESPONSE_CHARACTERS)
        assertFalse(boundedUssdResponse(full, MAXIMUM_USSD_RESPONSE_CHARACTERS).truncated)
        val shortened = boundedUssdResponse(full + "x", MAXIMUM_USSD_RESPONSE_CHARACTERS)
        assertTrue(shortened.truncated)
        assertEquals(full, shortened.text)
        assertEquals(MAXIMUM_USSD_RESPONSE_CHARACTERS + 1, shortened.characters)
        for (maximum in listOf(-1, 0, MAXIMUM_USSD_RESPONSE_CHARACTERS + 1)) {
            assertThrows(IllegalArgumentException::class.java) { boundedUssdResponse("text", maximum) }
        }
    }
}
