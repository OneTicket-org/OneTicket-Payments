package org.oneticket.payments

import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class PaymentService(
    private val repository: PaymentRepository,
    private val clock: Clock,
    private val ids: PaymentIdGenerator
) {

    fun createPayment(orderId: Long, customerId: UUID, amount: Money): Payment {
        repository.findByOrderId(orderId)?.let {
            throw OrderAlreadyHasPaymentException(orderId, it.id)
        }
        val payment = Payment(
            id = ids.next(),
            orderId = orderId,
            customerId = customerId,
            amount = amount,
            status = PaymentStatus.PENDING,
            version = 0,
            // PostgreSQL stores microseconds: truncating here keeps POST and GET identical.
            createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS),
            completedAt = null
        )
        try {
            repository.save(payment)
        } catch (e: DuplicateKeyException) {
            // The check above is check-then-act: the UNIQUE constraint on order_id decides the race.
            val existing = repository.findByOrderId(orderId) ?: throw e
            throw OrderAlreadyHasPaymentException(orderId, existing.id)
        }
        return payment
    }

    fun getPayment(id: String): Payment =
        repository.findById(id) ?: throw PaymentNotFoundException(id)
}
