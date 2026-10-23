package org.fossify.home.dikciz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DikcizCpuCountersTest {
    @Test
    fun parsesOnlyTheEightNonGuestCounters() {
        assertEquals(DikcizCpuCounters(1000, 850), DikcizCpuCounters.parse("cpu 100 0 50 850 0 0 0 0 999 888"))
        assertEquals(DikcizCpuCounters(1000, 850), DikcizCpuCounters.parse("  cpu7\t100 0 50 850 0 0 0 0  "))
    }

    @Test
    fun malformedNegativeAndOverflowCountersAreRejected() {
        val invalid = listOf("", "cpu", "intr 1 2 3 4 5 6 7 8", "cpuX 1 2 3 4 5 6 7 8",
            "cpu 1 2 3 4 5 6 7", "cpu 1 2 3 -4 5 6 7 8", "cpu 1 2 3 bad 5 6 7 8",
            "cpu 9223372036854775807 1 0 0 0 0 0 0")
        invalid.forEach { assertNull(it, DikcizCpuCounters.parse(it)) }
    }

    @Test
    fun firstColdResetAndImpossibleSamplesStayUnknown() {
        val sample = DikcizCpuCounters(100, 40)
        assertNull(sample.percentSince(null))
        assertNull(sample.percentSince(sample))
        assertNull(sample.percentSince(DikcizCpuCounters(200, 50)))
        assertNull(sample.percentSince(DikcizCpuCounters(50, 45)))
        assertNull(sample.percentSince(DikcizCpuCounters(90, 0)))
    }

    @Test
    fun measuresIdleBusyAndPartialLoadWithoutGuessing() {
        val cases = listOf(100L to 0.0, 0L to 100.0, 40L to 60.0)
        cases.forEach { (idle, expected) ->
            assertEquals(expected, DikcizCpuCounters(100, idle).percentSince(DikcizCpuCounters(0, 0))!!, 0.0001)
        }
    }
}
