package org.oneticket.payments

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

data class AmountStruct (
    val amount: String,
    val currency: String
)

data class FromPayment (
    val orderId: Int,
    val customerId: String,
    val amount: AmountStruct
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

fun parseAmountCents(amount: String): Int {
    return BigDecimal(amount).movePointRight(2).toInt()
}
@RestController
@RequestMapping("api/v1/payments")
class PaymentsController (private val service: PaymentService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)

    fun createPayment(@RequestBody fromPayment: FromPayment): PaymentsToDto {

        val payment = service.createPayment(
            orderId = fromPayment.orderId,
            amountCents = parseAmountCents(fromPayment.amount.amount),
            currency = fromPayment.amount.currency
        )

        return PaymentsToDto.fromPayment(payment)
    }
}
