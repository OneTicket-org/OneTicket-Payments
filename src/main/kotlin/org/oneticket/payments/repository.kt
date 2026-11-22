package org.oneticket.payments

interface PaymentRepository {
    /** Throws [org.springframework.dao.DuplicateKeyException] if the order already has a payment. */
    fun save(payment: Payment)
    fun findById(id: String): Payment?
    fun findByOrderId(orderId: Long): Payment?
}
