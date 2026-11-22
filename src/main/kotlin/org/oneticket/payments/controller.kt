package org.oneticket.payments

import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

data class AmountStruct (
    val amount: String?,
    val currency: String?
)

data class FromPayment (
    val orderId: Long?,
    val customerId: String?,
    val amount: AmountStruct?
)

data class PaymentsToDto (
    val id: String,
    val orderId: Long,
    val amount: AmountStruct,
    val status: String,
    val createdAt: String,
    val completedAt: String?
) {
    companion object {
        fun fromPayment(payment: Payment): PaymentsToDto {
            return PaymentsToDto(
                id = payment.id,
                orderId = payment.orderId,
                amount = AmountStruct(
                    amount = payment.amount.amount.setScale(2).toPlainString(),
                    currency = payment.amount.currency.currencyCode
                ),
                status = payment.status.name,
                createdAt = payment.createdAt.toString(),
                completedAt = payment.completedAt?.toString()
            )
        }
    }
}

/** Strong ETag carrying the row version ([API-29], contract example `"v2"`). */
fun etagOf(payment: Payment): String = "\"v${payment.version}\""

/** `If-None-Match` matching: `*` or any listed entity tag, weak prefix ignored ([API-32]). */
fun matchesIfNoneMatch(header: String?, etag: String): Boolean {
    if (header == null) return false
    return header.split(",")
        .map { it.trim().removePrefix("W/") }
        .any { it == "*" || it == etag }
}

@RestController
@RequestMapping("/api/v1/payments")
class PaymentsController (private val service: PaymentService) {

    @PostMapping
    fun createPayment(@RequestBody request: FromPayment): ResponseEntity<PaymentsToDto> {
        val v = validate(request)
        val payment = service.createPayment(
            orderId = v.orderId,
            customerId = v.customerId,
            amount = v.amount
        )

        return ResponseEntity.created(URI.create("/api/v1/payments/${payment.id}"))
            .eTag(etagOf(payment))
            .cacheControl(CacheControl.noStore())
            .body(PaymentsToDto.fromPayment(payment))
    }

    @GetMapping("/{id}")
    fun getPayment(
        @PathVariable id: String,
        @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?
    ): ResponseEntity<PaymentsToDto> {
        val payment = service.getPayment(requirePaymentId(id))
        val etag = etagOf(payment)
        if (matchesIfNoneMatch(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .eTag(etag)
                .cacheControl(CacheControl.noStore())
                .build()
        }
        return ResponseEntity.ok()
            .eTag(etag)
            .cacheControl(CacheControl.noStore())
            .body(PaymentsToDto.fromPayment(payment))
    }
}
