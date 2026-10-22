package org.fossify.home.dikciz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DikcizSmsReceiptTest {
    @Test
    fun submissionIsNotSendingOrDelivery() {
        assertEquals("submission_unknown", summary(submitted = false).state)
        val accepted = summary()
        assertEquals("accepted", accepted.state)
        assertFalse(accepted.deliveryConfirmed)
        assertEquals(0, accepted.sentParts)
    }

    @Test
    fun sentRequiresEveryDistinctPart() {
        assertEquals("accepted", summary(parts = 2, sent = mapOf(0 to OK)).state)
        val sent = summary(parts = 2, sent = mapOf(0 to OK, 1 to OK))
        assertEquals("sent", sent.state)
        assertEquals(2, sent.sentParts)
        assertFalse(sent.deliveryConfirmed)
        assertEquals("accepted", summary(parts = 2, sent = mapOf(0 to OK, 5 to OK)).state)
    }

    @Test
    fun deliveryNeedsCarrierReportsForEveryPart() {
        val first = mapOf(0 to report(0))
        assertEquals("accepted", summary(parts = 2, delivery = first).state)
        val delivered = summary(parts = 2, delivery = first + (1 to report(0)))
        assertEquals("delivered", delivered.state)
        assertEquals(2, delivered.deliveredParts)
        assertTrue(delivered.deliveryConfirmed)
        assertEquals("accepted", summary(parts = 2, delivery = first + (9 to report(0))).state)
    }

    @Test
    fun pendingOrMissingPduDoesNotConfirmDelivery() {
        for (report in listOf(report(32), report(63), report(null))) {
            val result = summary(sent = mapOf(0 to OK), delivery = mapOf(0 to report))
            assertEquals("sent", result.state)
            assertFalse(result.deliveryConfirmed)
        }
    }

    @Test
    fun failureTakesPrecedence() {
        assertEquals("failed", summary(failed = true).state)
        assertEquals("failed", summary(sent = mapOf(0 to 1), delivery = mapOf(0 to report(0))).state)
        for (report in listOf(report(64), report(128), DikcizSmsDeliveryResult(1, 0))) {
            val result = summary(sent = mapOf(0 to OK), delivery = mapOf(0 to report))
            assertEquals("delivery_failed", result.state)
            assertFalse(result.deliveryConfirmed)
        }
    }

    @Test
    fun duplicatePartUpdatesCannotCompleteMultipartMessage() {
        val callbacks = mutableMapOf(0 to OK)
        callbacks[0] = OK
        assertEquals("accepted", summary(parts = 2, sent = callbacks).state)
        callbacks[1] = OK
        assertEquals("sent", summary(parts = 2, sent = callbacks).state)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroPartsAreInvalid() {
        summary(parts = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun tooManyPartsAreInvalid() {
        summary(parts = DikcizSmsReceipt.MAXIMUM_PARTS + 1)
    }

    private fun report(status: Int?) = DikcizSmsDeliveryResult(OK, status)
    private fun summary(
        parts: Int = 1,
        submitted: Boolean = true,
        failed: Boolean = false,
        sent: Map<Int, Int> = emptyMap(),
        delivery: Map<Int, DikcizSmsDeliveryResult> = emptyMap(),
    ) = DikcizSmsReceipt.summarize(parts, submitted, failed, sent, delivery)

    companion object {
        private const val OK = -1
    }
}
