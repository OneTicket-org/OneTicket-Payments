package org.oneticket.payments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ValidationTest {
    private val customer = "b3f1c2d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d"

    private fun request(
        orderId: Long? = 5512,
        customerId: String? = customer,
        amount: String? = "90.20",
        currency: String? = "EUR"
    ) = FromPayment(orderId, customerId, AmountStruct(amount, currency))

    private fun errorsOf(req: FromPayment): List<FieldError> =
        assertFailsWith<ValidationFailedException> { validate(req) }.errors

    @Test
    fun `a valid request is accepted`() {
        val v = validate(request())
        assertEquals(5512L, v.orderId)
        assertEquals("90.20", v.amount.amount.toPlainString())
        assertEquals("EUR", v.amount.currency.currencyCode)
    }

    @Test
    fun `order ids beyond 32 bits are accepted`() {
        assertEquals(3_000_000_000L, validate(request(orderId = 3_000_000_000L)).orderId)
    }

    @Test
    fun `the largest amount of NUMERIC(12,2) is accepted`() {
        assertEquals("9999999999.99", validate(request(amount = "9999999999.99")).amount.amount.toPlainString())
    }

    @Test
    fun `amount with one decimal is refused`() {
        assertEquals("amount.amount", errorsOf(request(amount = "90.2")).single().field)
    }

    @Test
    fun `zero and negative amounts are refused`() {
        assertEquals("OUT_OF_RANGE", errorsOf(request(amount = "0.00")).single().code)
        assertEquals("OUT_OF_RANGE", errorsOf(request(amount = "-1.00")).single().code)
    }

    @Test
    fun `currency must be a real ISO 4217 code`() {
        assertEquals("amount.currency", errorsOf(request(currency = "eur")).single().field)
        assertEquals("amount.currency", errorsOf(request(currency = "ZZZ")).single().field)
        assertEquals("amount.currency", errorsOf(request(currency = "EURO")).single().field)
    }

    @Test
    fun `missing fields are all reported at once`() {
        val fields = errorsOf(FromPayment(null, null, null)).map { it.field }
        assertEquals(listOf("orderId", "customerId", "amount"), fields)
    }

    @Test
    fun `customer id must be a UUID`() {
        assertEquals("customerId", errorsOf(request(customerId = "not-a-uuid")).single().field)
    }

    @Test
    fun `payment ids are checked against the contract pattern`() {
        assertEquals("pay_01J8Z3Q4M2", requirePaymentId("pay_01J8Z3Q4M2"))
        assertFailsWith<MalformedPaymentIdException> { requirePaymentId("pay_lower") }
        assertFailsWith<MalformedPaymentIdException> { requirePaymentId("123") }
    }

    @Test
    fun `If-None-Match matches a listed or wildcard entity tag`() {
        assertTrue(matchesIfNoneMatch("\"v0\"", "\"v0\""))
        assertTrue(matchesIfNoneMatch("\"v9\", W/\"v0\"", "\"v0\""))
        assertTrue(matchesIfNoneMatch("*", "\"v0\""))
        assertEquals(false, matchesIfNoneMatch("\"v1\"", "\"v0\""))
        assertEquals(false, matchesIfNoneMatch(null, "\"v0\""))
    }
}
