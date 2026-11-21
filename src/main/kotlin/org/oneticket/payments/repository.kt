package org.oneticket.payments

import org.springframework.stereotype.Repository

@Repository
interface Paymentrepository {
    fun save(payment: Payment)
    fun findById(id: String): Payment?
    fun findByOrderId(orderId: Int): Payment?
}