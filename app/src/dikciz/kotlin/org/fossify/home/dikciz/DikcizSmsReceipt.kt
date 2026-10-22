package org.fossify.home.dikciz

internal data class DikcizSmsDeliveryResult(val resultCode: Int, val status: Int?)
internal data class DikcizSmsReceiptSummary(
    val state: String,
    val sentParts: Int,
    val deliveredParts: Int,
    val deliveryConfirmed: Boolean,
)

/** Aggregates distinct callbacks without treating submission as carrier delivery. */
internal object DikcizSmsReceipt {
    const val MAXIMUM_PARTS = 32
    const val RESULT_OK = -1
    private const val DELIVERY_PENDING = 32
    private const val DELIVERY_FAILED = 64

    fun summarize(
        parts: Int,
        submitted: Boolean,
        submissionFailed: Boolean,
        sent: Map<Int, Int>,
        delivery: Map<Int, DikcizSmsDeliveryResult>,
    ): DikcizSmsReceiptSummary {
        require(parts in 1..MAXIMUM_PARTS)
        val validSent = sent.filterKeys { it in 0 until parts }.values
        val validDelivery = delivery.filterKeys { it in 0 until parts }.values
        val sentParts = validSent.count { it == RESULT_OK }
        val deliveredParts = validDelivery.count { it.resultCode == RESULT_OK && it.status != null && it.status in 0 until DELIVERY_PENDING }
        val state = when {
            submissionFailed || validSent.any { it != RESULT_OK } -> "failed"
            validDelivery.any { it.resultCode != RESULT_OK || (it.status ?: -1) >= DELIVERY_FAILED } -> "delivery_failed"
            deliveredParts == parts -> "delivered"
            sentParts == parts -> "sent"
            submitted -> "accepted"
            else -> "submission_unknown"
        }
        return DikcizSmsReceiptSummary(state, sentParts, deliveredParts, state == "delivered")
    }
}
