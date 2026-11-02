package org.fossify.home.dikciz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class DikcizSimPhoneNumberTest {
    @Test
    fun availableNumbersPreserveCarrierFormatting() {
        for (number in listOf("+12025550123", "  +1 (202) 555-0123  ", "2025550123")) {
            val result = readSimPhoneNumber(true) { number }
            assertEquals(number.trim(), result.number)
            assertEquals(DikcizSimNumberStatus.Available, result.status)
            assertNull(result.failure)
        }
    }

    @Test
    fun missingOrBlankNumbersAreUnavailable() {
        for (number in listOf(null, "", " ", "\t\n")) {
            val result = readSimPhoneNumber(true) { number }
            assertNull(result.number)
            assertEquals(DikcizSimNumberStatus.Unavailable, result.status)
            assertNull(result.failure)
        }
    }

    @Test
    fun missingPermissionDoesNotCallAndroidLookup() {
        var called = false
        val result = readSimPhoneNumber(false) {
            called = true
            "+12025550123"
        }
        assertFalse(called)
        assertNull(result.number)
        assertEquals(DikcizSimNumberStatus.PermissionRequired, result.status)
    }

    @Test
    fun optionalAndroidFailuresHaveExplicitStatusAndRetainCause() {
        for ((failure, status) in listOf(
            SecurityException("restricted") to DikcizSimNumberStatus.AccessDenied,
            IllegalStateException("telephony unavailable") to DikcizSimNumberStatus.Unavailable,
            UnsupportedOperationException("no subscription support") to DikcizSimNumberStatus.Unsupported,
        )) {
            val result = readSimPhoneNumber(true) { throw failure }
            assertNull(result.number)
            assertEquals(status, result.status)
            assertSame(failure, result.failure)
        }
    }

    @Test
    fun unexpectedFailuresAreNotReportedAsMissingNumbers() {
        val failure = IllegalArgumentException("invalid subscription")
        assertSame(failure, assertThrows(IllegalArgumentException::class.java) {
            readSimPhoneNumber(true) { throw failure }
        })
    }
}
