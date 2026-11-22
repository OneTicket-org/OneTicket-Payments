package org.oneticket.payments

import org.springframework.dao.DuplicateKeyException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Currency
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakePaymentRepository : PaymentRepository {
    private val byId = mutableMapOf<String, Payment>()
    // Like the UNIQUE constraint on order_id: a second payment for the same order is refused.
    override fun save(payment: Payment) {
        if (byId.values.any { it.orderId == payment.orderId }) throw DuplicateKeyException("order_id")
        byId[payment.id] = payment
    }
    override fun findById(id: String) = byId[id]
    override fun findByOrderId(orderId: Long) = byId.values.firstOrNull { it.orderId == orderId }
}

/** Loses the race on purpose: the pre-check sees nothing, the insert hits the UNIQUE constraint. */
class RacingPaymentRepository(private val winner: Payment) : PaymentRepository {
    private var checked = false
    override fun save(payment: Payment) {
        throw DuplicateKeyException("order_id")
    }
    override fun findById(id: String): Payment? = null
    override fun findByOrderId(orderId: Long): Payment? {
        if (!checked) {
            checked = true
            return null
        }
        return winner
    }
}

class PaymentServiceTest {
    private val now = Instant.parse("2026-11-22T10:00:00.123456789Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private var counter = 0
    private val ids = PaymentIdGenerator { "pay_TEST" + (++counter).toString().padStart(8, '0') }
    private val customer = UUID.fromString("b3f1c2d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
    private val eur = Currency.getInstance("EUR")
    private val service = PaymentService(FakePaymentRepository(), clock, ids)

    private fun money(amount: String = "90.20") = Money(BigDecimal(amount), eur)

    @Test
    fun `creates a PENDING payment`() {
        val p = service.createPayment(1, customer, money())
        assertEquals(PaymentStatus.PENDING, p.status)
        assertEquals(BigDecimal("90.20"), p.amount.amount)
        assertEquals(eur, p.amount.currency)
        assertEquals(customer, p.customerId)
        assertEquals(0, p.version)
        assertNull(p.completedAt)
        assertTrue(p.id.matches(Regex("^pay_[0-9A-Z]{10,26}$")))
    }

    @Test
    fun `creation time comes from the injected clock, truncated to microseconds`() {
        val p = service.createPayment(1, customer, money())
        assertEquals(Instant.parse("2026-11-22T10:00:00.123456Z"), p.createdAt)
    }

    @Test
    fun `second payment for the same order is refused`() {
        service.createPayment(1, customer, money())
        assertFailsWith<OrderAlreadyHasPaymentException> { service.createPayment(1, customer, money("5.00")) }
    }

    @Test
    fun `a lost race on the unique constraint is reported as the same conflict`() {
        val winner = PaymentService(FakePaymentRepository(), clock, ids).createPayment(7, customer, money())
        val racing = PaymentService(RacingPaymentRepository(winner), clock, ids)
        val e = assertFailsWith<OrderAlreadyHasPaymentException> { racing.createPayment(7, customer, money()) }
        assertEquals("ORDER_ALREADY_HAS_PAYMENT", e.code)
    }

    @Test
    fun `unknown payment is not found`() {
        assertFailsWith<PaymentNotFoundException> { service.getPayment("pay_UNKNOWN0000") }
    }

    @Test
    fun `generated ids match the contract pattern`() {
        val id = UuidPaymentIdGenerator().next()
        assertTrue(id.matches(Regex("^pay_[0-9A-Z]{10,26}$")), id)
    }
}
