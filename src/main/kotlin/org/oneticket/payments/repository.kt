package org.oneticket.payments

interface PaymentRepository {
    fun save(payment: Payment)
    fun findById(id: String): Payment?
    fun findByOrderId(orderId: Int): Payment?
}