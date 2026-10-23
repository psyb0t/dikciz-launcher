package org.fossify.home.dikciz

internal data class DikcizCpuCounters(val total: Long, val idle: Long) {
    fun percentSince(previous: DikcizCpuCounters?): Double? {
        if (previous == null || total <= previous.total || idle < previous.idle) return null
        val elapsed = total - previous.total
        val idleElapsed = idle - previous.idle
        if (idleElapsed > elapsed) return null
        return (elapsed - idleElapsed).toDouble() * PERCENT / elapsed
    }

    companion object {
        private const val COUNTER_COUNT = 8
        private const val IDLE_INDEX = 3
        private const val IOWAIT_INDEX = 4
        private const val PERCENT = 100.0
        private val WHITESPACE = Regex("\\s+")

        fun parse(line: String): DikcizCpuCounters? {
            val fields = line.trim().split(WHITESPACE)
            if (fields.size < COUNTER_COUNT + 1 || !fields.first().matches(Regex("cpu[0-9]*"))) return null
            val values = fields.drop(1).take(COUNTER_COUNT).map { field ->
                field.toLongOrNull()?.takeIf { it >= 0 } ?: return null
            }
            val total = try {
                values.fold(0L, Math::addExact)
            } catch (_: ArithmeticException) {
                return null
            }
            val idle = values[IDLE_INDEX] + values[IOWAIT_INDEX]
            return DikcizCpuCounters(total, idle)
        }
    }
}
