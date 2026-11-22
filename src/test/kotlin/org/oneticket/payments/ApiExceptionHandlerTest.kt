package org.oneticket.payments

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.bind.MissingServletRequestParameterException
import kotlin.test.Test
import kotlin.test.assertEquals

// No Spring context, no container: the handler is a plain class.
class ApiExceptionHandlerTest {
    private val handler = ApiExceptionHandler()
    private val request = MockHttpServletRequest("GET", "/api/v1/payments")

    @Test
    fun `a 400 raised by Spring is the contract's MALFORMED_REQUEST`() {
        val r = handler.other(MissingServletRequestParameterException("status", "String"), request)
        assertEquals(400, r.statusCode.value())
        assertEquals("MALFORMED_REQUEST", r.body?.code)
        assertEquals("/problems/malformed-request", r.body?.type)
        assertEquals("/api/v1/payments", r.body?.instance)
    }

    @Test
    fun `an unexpected exception is a 500 without leaking details`() {
        val r = handler.other(IllegalStateException("secret internals"), request)
        assertEquals(500, r.statusCode.value())
        assertEquals("INTERNAL_ERROR", r.body?.code)
        assertEquals("An unexpected error occurred.", r.body?.detail)
    }
}
