package org.oneticket.payments

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["SPRING_DATASOURCE_PASSWORD=test"]
)
class PaymentsHttpTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val client = HttpClient.newHttpClient()

    private fun send(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .method(method, if (body == null) BodyPublishers.noBody() else BodyPublishers.ofString(body))
        headers.forEach { (name, value) -> builder.header(name, value) }
        return client.send(builder.build(), BodyHandlers.ofString())
    }

    private fun payment(orderId: Any, amount: String = "90.20", currency: String = "EUR") =
        """{"orderId":$orderId,"customerId":"b3f1c2d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d",""" +
                """"amount":{"amount":"$amount","currency":"$currency"}}"""

    private fun idOf(body: String): String =
        Regex("\"id\":\"(pay_[0-9A-Z]+)\"").find(body)!!.groupValues[1]

    @Test
    fun `creates a PENDING payment`() {
        val r = send("POST", "/api/v1/payments", payment(5512))
        assertEquals(201, r.statusCode())
        assertTrue(r.body().contains("\"status\":\"PENDING\""))
        assertTrue(r.body().contains("\"amount\":{\"amount\":\"90.20\",\"currency\":\"EUR\"}"))
        assertTrue(r.body().contains("\"id\":\"pay_"))
    }

    @Test
    fun `creation answers with Location, ETag, no-store and a request id`() {
        val r = send("POST", "/api/v1/payments", payment(5514))
        assertEquals(201, r.statusCode())
        assertEquals("/api/v1/payments/${idOf(r.body())}", r.headers().firstValue("Location").get())
        assertEquals("\"v0\"", r.headers().firstValue("ETag").get())
        assertTrue(r.headers().firstValue("Cache-Control").get().contains("no-store"))
        assertTrue(r.headers().firstValue("X-Request-Id").isPresent)
    }

    @Test
    fun `created payment can be read back identical, with the currency unpadded`() {
        val created = send("POST", "/api/v1/payments", payment(5513)).body()
        val r = send("GET", "/api/v1/payments/${idOf(created)}")
        assertEquals(200, r.statusCode())
        assertEquals(created, r.body())
        assertTrue(r.body().contains("\"currency\":\"EUR\""))
        assertEquals("\"v0\"", r.headers().firstValue("ETag").get())
        assertTrue(r.headers().firstValue("Cache-Control").get().contains("no-store"))
    }

    @Test
    fun `reading with a matching If-None-Match gives 304 and no body`() {
        val id = idOf(send("POST", "/api/v1/payments", payment(5515)).body())
        val r = send("GET", "/api/v1/payments/$id", headers = mapOf("If-None-Match" to "\"v0\""))
        assertEquals(304, r.statusCode())
        assertEquals("", r.body())
        assertEquals("\"v0\"", r.headers().firstValue("ETag").get())
    }

    @Test
    fun `reading with a stale If-None-Match gives the payment`() {
        val id = idOf(send("POST", "/api/v1/payments", payment(5516)).body())
        val r = send("GET", "/api/v1/payments/$id", headers = mapOf("If-None-Match" to "\"v9\""))
        assertEquals(200, r.statusCode())
    }

    @Test
    fun `large amounts and order ids beyond 32 bits are stored exactly`() {
        val created = send("POST", "/api/v1/payments", payment(3000000000, amount = "9999999999.99"))
        assertEquals(201, created.statusCode())
        val r = send("GET", "/api/v1/payments/${idOf(created.body())}")
        assertTrue(r.body().contains("\"orderId\":3000000000"))
        assertTrue(r.body().contains("\"amount\":\"9999999999.99\""))
    }

    @Test
    fun `second payment for the same order gives 409`() {
        assertEquals(201, send("POST", "/api/v1/payments", payment(6001)).statusCode())
        val r = send("POST", "/api/v1/payments", payment(6001))
        assertEquals(409, r.statusCode())
        assertTrue(r.body().contains("ORDER_ALREADY_HAS_PAYMENT"))
        assertTrue(r.headers().firstValue("Content-Type").get().startsWith("application/problem+json"))
    }

    @Test
    fun `concurrent payments for one order create exactly one`() {
        val attempts = 8
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(attempts)
        try {
            val futures = (1..attempts).map {
                pool.submit<Int> {
                    start.await()
                    send("POST", "/api/v1/payments", payment(6100)).statusCode()
                }
            }
            start.countDown()
            val codes = futures.map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, codes.count { it == 201 }, "codes: $codes")
            assertEquals(attempts - 1, codes.count { it == 409 }, "codes: $codes")
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `invalid amount gives 422 with the field`() {
        val r = send("POST", "/api/v1/payments", payment(6002, amount = "90.2"))
        assertEquals(422, r.statusCode())
        assertTrue(r.body().contains("VALIDATION_FAILED"))
        assertTrue(r.body().contains("amount.amount"))
    }

    @Test
    fun `unknown currency gives 422 with the field`() {
        val r = send("POST", "/api/v1/payments", payment(6004, currency = "ZZZ"))
        assertEquals(422, r.statusCode())
        assertTrue(r.body().contains("amount.currency"))
    }

    @Test
    fun `malformed json gives 400`() {
        val r = send("POST", "/api/v1/payments", "{not json")
        assertEquals(400, r.statusCode())
        assertTrue(r.body().contains("MALFORMED_REQUEST"))
    }

    @Test
    fun `order id as string is not coerced`() {
        val r = send("POST", "/api/v1/payments", payment("\"6003\""))
        assertEquals(400, r.statusCode())
    }

    @Test
    fun `unknown fields are rejected`() {
        val r = send("POST", "/api/v1/payments", payment(6005).dropLast(1) + ""","extra":1}""")
        assertEquals(400, r.statusCode())
        assertTrue(r.body().contains("MALFORMED_REQUEST"))
    }

    @Test
    fun `unknown payment gives 404`() {
        val r = send("GET", "/api/v1/payments/pay_UNKNOWN0000")
        assertEquals(404, r.statusCode())
        assertTrue(r.body().contains("PAYMENT_NOT_FOUND"))
    }

    @Test
    fun `malformed payment id gives 400`() {
        val r = send("GET", "/api/v1/payments/not-a-payment-id")
        assertEquals(400, r.statusCode())
        assertTrue(r.body().contains("MALFORMED_REQUEST"))
    }

    @Test
    fun `wrong method gives 405 with Allow`() {
        val r = send("PUT", "/api/v1/payments", payment(6006))
        assertEquals(405, r.statusCode())
        assertTrue(r.headers().firstValue("Allow").get().contains("POST"))
    }

    @Test
    fun `liveness and readiness are UP without RabbitMQ`() {
        assertEquals(200, send("GET", "/actuator/health/liveness").statusCode())
        assertEquals(200, send("GET", "/actuator/health/readiness").statusCode())
    }

    @Test
    fun `unknown route gives a problem+json 404`() {
        val r = send("GET", "/api/v1/nothing-here")
        assertEquals(404, r.statusCode())
        assertTrue(r.headers().firstValue("Content-Type").get().startsWith("application/problem+json"))
        assertTrue(r.body().contains("\"instance\":\"/api/v1/nothing-here\""))
    }

    @Test
    fun `the caller's request id is echoed, also on errors`() {
        val ok = send("GET", "/api/v1/payments/pay_UNKNOWN0000", headers = mapOf("X-Request-Id" to "req-123"))
        assertEquals(404, ok.statusCode())
        assertEquals("req-123", ok.headers().firstValue("X-Request-Id").get())
    }

    @Test
    fun `an unsafe request id is replaced`() {
        val r = send("GET", "/api/v1/payments/pay_UNKNOWN0000", headers = mapOf("X-Request-Id" to "bad id with spaces"))
        assertTrue(r.headers().firstValue("X-Request-Id").get() != "bad id with spaces")
    }
}
