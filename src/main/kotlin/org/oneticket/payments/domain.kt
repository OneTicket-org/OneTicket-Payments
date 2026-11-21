package org.oneticket.payments

import java.time.Instant
import java.util.UUID

enum class PaymentStatus {
    PENDING,
    SUCCEEDED,
    DECLINED,
    TIMED_OUT,
    REFUNDED
}

data class Payment (
    val id: String,
    val orderId: Int,
    val amountCents: Int,
    val currency: String,
    val status: PaymentStatus,
    val createdAt: Instant,
    val completedAt: Instant?
)

// strategy is decided here
fun generatePaymentId(): String {
    return UUID.randomUUID().toString()
}