package org.oneticket.payments

import java.math.BigDecimal

private val AMOUNT_RE = Regex("^-?[0-9]{1,10}\\.[0-9]{2}$")
private val CURRENCY_RE = Regex("^[A-Z]{3}$")
private val UUID_RE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

class ValidPayment(val orderId: Int, val amountCents: Int, val currency: String)
fun validate(req: FromPayment): ValidPayment {
    val errors = mutableListOf<FieldError>()
    val orderId = req.orderId
    if (orderId == null) errors += FieldError("orderId", "REQUIRED", "Required.")
    else if (orderId < 1) errors += FieldError("orderId", "OUT_OF_RANGE", "Must be at least 1.")

    val customerId = req.customerId
    if (customerId == null) errors += FieldError("customerId", "REQUIRED", "Required.")
    else if (!UUID_RE.matches(customerId)) errors += FieldError("customerId", "INVALID_FORMAT", "Must be a UUID.")

    var cents: Int? = null
    val money = req.amount
    if (money == null) {
        errors += FieldError("amount", "REQUIRED", "Required.")
    } else {
        val a = money.amount
        if (a == null) errors += FieldError("amount.amount", "REQUIRED", "Required.")
        else if (!AMOUNT_RE.matches(a))
            errors += FieldError("amount.amount", "INVALID_FORMAT", "Must have exactly two decimals, e.g. 90.20.")
        else {
            val v = BigDecimal(a)
            if (v.signum() <= 0) errors += FieldError("amount.amount", "OUT_OF_RANGE", "Must be greater than zero.")
            else try {
                cents = v.movePointRight(2).intValueExact()
            } catch (e: ArithmeticException) {
                errors += FieldError("amount.amount", "OUT_OF_RANGE", "Amount too large.")
            }
        }
        val c = money.currency
        if (c == null) errors += FieldError("amount.currency", "REQUIRED", "Required.")
        else if (!CURRENCY_RE.matches(c)) errors += FieldError(
            "amount.currency",
            "INVALID_FORMAT",
            "Must be an ISO 4217 code, e.g. EUR."
        )
    }

    if (errors.isNotEmpty()) throw ValidationFailedException(errors)
    return ValidPayment(orderId!!, cents!!, money!!.currency!!)
}