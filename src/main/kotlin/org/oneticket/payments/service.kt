package org.oneticket.payments

import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PaymentService(private val repository: PaymentRepository) {

    fun createPayment(orderId: Int, amountCents: Int, currency: String): Payment {
        repository.findByOrderId(orderId)?.let {
            throw OrderAlreadyHasPaymentException(orderId, it.id)
        }
        val payment = Payment(
            id = generatePaymentId(),
            orderId = orderId,
            amountCents = amountCents,
            currency = currency,
            status = PaymentStatus.PENDING,
            createdAt = Instant.now(),
            completedAt = null
        )
        try {
            repository.save(payment)
        } catch (e: DuplicateKeyException) {
            throw OrderAlreadyHasPaymentException(orderId, repository.findByOrderId(orderId)!!.id)
        }
        return payment
    }

    fun getPayment(id: String): Payment =
        repository.findById(id) ?: throw PaymentNotFoundException(id)
}