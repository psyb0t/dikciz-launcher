package org.fossify.home.dikciz

internal data class DikcizLuaScriptRunStatus(
    val lastRunAt: String?,
    val outcome: DikcizLuaScriptRunOutcome,
    val status: String,
) {
    companion object {
        val NotRun = DikcizLuaScriptRunStatus(
            lastRunAt = null,
            outcome = DikcizLuaScriptRunOutcome.NotRun,
            status = "Not run yet",
        )
    }
}

internal enum class DikcizLuaScriptRunOutcome(
    val persistedValue: String,
) {
    Completed("completed"),
    Failed("failed"),
    NotRun("notRun"),
    Rejected("rejected"),
    ;

    companion object {
        fun fromPersistedValue(value: String): DikcizLuaScriptRunOutcome? {
            return entries.firstOrNull { it.persistedValue == value }
        }
    }
}
