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

    private fun send(method: String, path: String, body: String? = null): HttpResponse<String> {
        val req = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .method(method, if (body == null) BodyPublishers.noBody() else BodyPublishers.ofString(body))
            .build()
        return client.send(req, BodyHandlers.ofString())
    }

    private fun payment(orderId: Any, amount: String = "90.20", currency: String = "EUR") =
        """{"orderId":$orderId,"customerId":"b3f1c2d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d",""" +
                """"amount":{"amount":"$amount","currency":"$currency"}}"""

    @Test
    fun `creates a PENDING payment`() {
        val r = send("POST", "/api/v1/payments", payment(5512))
        assertEquals(201, r.statusCode())
        assertTrue(r.body().contains("\"status\":\"PENDING\""))
        assertTrue(r.body().contains("\"amount\":\"90.20\""))
        assertTrue(r.body().contains("\"id\":\"pay_"))
    }

    @Test
    fun `created payment can be read back`() {
        val created = send("POST", "/api/v1/payments", payment(5513)).body()
        val id = Regex("\"id\":\"(pay_[0-9A-Z]+)\"").find(created)!!.groupValues[1]
        val r = send("GET", "/api/v1/payments/$id")
        assertEquals(200, r.statusCode())
        assertTrue(r.body().contains(id))
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
    fun `invalid amount gives 422 with the field`() {
        val r = send("POST", "/api/v1/payments", payment(6002, amount = "90.2"))
        assertEquals(422, r.statusCode())
        assertTrue(r.body().contains("VALIDATION_FAILED"))
        assertTrue(r.body().contains("amount.amount"))
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
    fun `unknown payment gives 404`() {
        val r = send("GET", "/api/v1/payments/pay_UNKNOWN0000")
        assertEquals(404, r.statusCode())
        assertTrue(r.body().contains("PAYMENT_NOT_FOUND"))
    }
}