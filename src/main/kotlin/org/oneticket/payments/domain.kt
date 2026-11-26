package org.oneticket.payments

import java.math.BigDecimal
import java.time.Instant
import java.util.Currency
import java.util.UUID

enum class PaymentStatus {
    PENDING,
    SUCCEEDED,
    DECLINED,
    TIMED_OUT,
    REFUNDED
}

/** Exact amount of money with its ISO 4217 currency ([BIZ-22]): never a floating point. */
data class Money(
    val amount: BigDecimal,
    val currency: Currency
)

data class Payment (
    val id: String,
    val orderId: Long,
    val customerId: UUID,
    val amount: Money,
    val status: PaymentStatus,
    /** Row version, exposed as the strong ETag ([API-29]). */
    val version: Long,
    val createdAt: Instant,
    val completedAt: Instant?
)

/** Port producing payment identifiers, so that services never generate ids themselves ([SVC-15]). */
fun interface PaymentIdGenerator {
    fun next(): String
}
