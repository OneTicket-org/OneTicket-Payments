package org.oneticket.payments

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.Types
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Currency
import java.util.UUID

@Repository
class JdbcRepository (private val jdbc: JdbcClient) : PaymentRepository {
    private val mapper = RowMapper { rs, _ ->
        val amount: BigDecimal = rs.getBigDecimal("amount")
        val customerId: UUID = rs.getObject("customer_id", UUID::class.java)
        Payment(
            id = rs.getString("payment_id"),
            orderId = rs.getLong("order_id"),
            customerId = customerId,
            amount = Money(amount, Currency.getInstance(rs.getString("currency"))),
            status = PaymentStatus.valueOf(rs.getString("status")),
            version = rs.getLong("version"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant()
        )
    }

    private fun ts(i: Instant?) : OffsetDateTime? = i?.atOffset(ZoneOffset.UTC)

    override fun save(payment: Payment) {
        jdbc.sql(
            """
            INSERT INTO payments (payment_id, order_id, customer_id, amount, currency, status, version, created_at, completed_at)
            VALUES (:id, :orderId, :customerId, :amount, :currency, :status, :version, :createdAt, :completedAt)
            """.trimIndent()
        )
        .param("id", payment.id)
        .param("orderId", payment.orderId)
        .param("customerId", payment.customerId)
        .param("amount", payment.amount.amount)
        .param("currency", payment.amount.currency.currencyCode)
        .param("status", payment.status.name)
        .param("version", payment.version)
        .param("createdAt", ts(payment.createdAt), Types.TIMESTAMP_WITH_TIMEZONE)
        .param("completedAt", ts(payment.completedAt), Types.TIMESTAMP_WITH_TIMEZONE)
        .update()
    }

    override fun findById(id: String): Payment? {
        return jdbc.sql(
            """
            SELECT * FROM payments WHERE payment_id = :id
            """.trimIndent()
        )
        .param("id", id)
        .query(mapper)
        .optional().orElse(null)
    }

    override fun findByOrderId(orderId: Long): Payment? {
        return jdbc.sql(
            """
            SELECT * FROM payments WHERE order_id = :orderId
            """.trimIndent()
        )
        .param("orderId", orderId)
        .query(mapper)
        .optional().orElse(null)
    }

}
