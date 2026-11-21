package org.oneticket.payments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakePaymentRepository : PaymentRepository {
    private val byId = mutableMapOf<String, Payment>()
    override fun save(payment: Payment) { byId[payment.id] = payment }
    override fun findById(id: String) = byId[id]
    override fun findByOrderId(orderId: Int) = byId.values.firstOrNull { it.orderId == orderId }
}

class PaymentServiceTest {
    private val service = PaymentService(FakePaymentRepository())

    @Test
    fun `creates a PENDING payment`() {
        val p = service.createPayment(1, 9020, "EUR")
        assertEquals(PaymentStatus.PENDING, p.status)
        assertEquals(9020, p.amountCents)
        assertNull(p.completedAt)
        assertTrue(p.id.matches(Regex("^pay_[0-9A-Z]{10,26}$")))
    }

    @Test
    fun `second payment for the same order is refused`() {
        service.createPayment(1, 9020, "EUR")
        assertFailsWith<OrderAlreadyHasPaymentException> { service.createPayment(1, 500, "EUR") }
    }

    @Test
    fun `unknown payment is not found`() {
        assertFailsWith<PaymentNotFoundException> { service.getPayment("pay_UNKNOWN0000") }
    }
}