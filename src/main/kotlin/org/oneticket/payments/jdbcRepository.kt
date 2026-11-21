package org.oneticket.payments

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Types
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JdbcRepository (private val jdbc: JdbcClient) : PaymentRepository {
    private val mapper = RowMapper { rs, _ ->
        Payment(
            id = rs.getString("payment_id"),
            orderId = rs.getInt("order_id"),
            amountCents = rs.getInt("amount_cents"),
            currency = rs.getString("currency"),
            status = PaymentStatus.valueOf(rs.getString("status")),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant()
        )
    }

    private fun ts(i: Instant?) : OffsetDateTime? = i?.atOffset(ZoneOffset.UTC)

    override fun save(payment: Payment) {
        jdbc.sql(
            """
            INSERT INTO payments (payment_id, order_id, amount_cents, currency, status, created_at, completed_at)
            VALUES (:id, :orderId, :amountCents, :currency, :status, :createdAt, :completedAt)
            """.trimIndent()
        )
        .param("id", payment.id)
        .param("orderId", payment.orderId)
        .param("amountCents", payment.amountCents)
        .param("currency", payment.currency)
        .param("status", payment.status.name)
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

    override fun findByOrderId(orderId: Int): Payment? {
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