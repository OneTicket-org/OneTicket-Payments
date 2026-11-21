package org.oneticket.payments

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

data class AmountStruct (
    val amount: String?,
    val currency: String?
)

data class FromPayment (
    val orderId: Int?,
    val customerId: String?,
    val amount: AmountStruct?
)

data class PaymentsToDto (
    val id: String,
    val orderId: Int,
    val amount: String,
    val currency: String,
    val status: String,
    val createdAt: String,
    val completedAt: String?
) {
    companion object {
        fun fromPayment(payment: Payment): PaymentsToDto {
            return PaymentsToDto(
                id = payment.id,
                orderId = payment.orderId,
                amount = amountIntToString(payment.amountCents),
                currency = payment.currency,
                status = payment.status.name,
                createdAt = payment.createdAt.toString(),
                completedAt = payment.completedAt?.toString()
            )
        }
    }
}

fun amountIntToString(amount: Int): String {
    return BigDecimal.valueOf(amount.toLong(), 2).toPlainString()
}

@RestController
@RequestMapping("/api/v1/payments")
class PaymentsController (private val service: PaymentService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)

    fun createPayment(@RequestBody request: FromPayment): PaymentsToDto {
        val v = validate(request);
        val payment = service.createPayment(
            orderId = v.orderId,
            amountCents = v.amountCents,
            currency = v.currency
        )

        return PaymentsToDto.fromPayment(payment)
    }

    @GetMapping("/{id}")
    fun getPayment(@PathVariable id: String): PaymentsToDto =
        PaymentsToDto.fromPayment(service.getPayment(id))
}
