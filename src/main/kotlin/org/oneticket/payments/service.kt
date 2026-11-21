package org.oneticket.payments

import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PaymentService(private val repository: Paymentrepository) {
    fun createPayment(orderId: Int, amountCents: Int, currency: String): Payment {
        repository.findByOrderId(orderId)?.let { return it }
        val payment = Payment(
            id = generatePaymentId(),
            orderId = orderId,
            amountCents = amountCents,
            currency = currency,
            status = PaymentStatus.PENDING,
            createdAt = Instant.now(),
            completedAt = null
        )
        repository.save(payment)
        return payment
    }
}