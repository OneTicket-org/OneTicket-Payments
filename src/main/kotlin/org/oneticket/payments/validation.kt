package org.oneticket.payments

import java.math.BigDecimal
import java.util.Currency
import java.util.UUID

private val AMOUNT_RE = Regex("^-?[0-9]{1,10}\\.[0-9]{2}$")
private val CURRENCY_RE = Regex("^[A-Z]{3}$")
private val UUID_RE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
private val PAYMENT_ID_RE = Regex("^pay_[0-9A-Z]{10,26}$")

class ValidPayment(val orderId: Long, val customerId: UUID, val amount: Money)

fun validate(req: FromPayment): ValidPayment {
    val errors = mutableListOf<FieldError>()

    val orderId = req.orderId
    if (orderId == null) errors += FieldError("orderId", "REQUIRED", "Required.")
    else if (orderId < 1) errors += FieldError("orderId", "OUT_OF_RANGE", "Must be at least 1.")

    val customerId = validateCustomerId(req.customerId, errors)
    val amount = validateMoney(req.amount, errors)

    if (errors.isNotEmpty() || orderId == null || customerId == null || amount == null)
        throw ValidationFailedException(errors)
    return ValidPayment(orderId, customerId, amount)
}

private fun validateCustomerId(raw: String?, errors: MutableList<FieldError>): UUID? {
    if (raw == null) {
        errors += FieldError("customerId", "REQUIRED", "Required.")
        return null
    }
    if (!UUID_RE.matches(raw)) {
        errors += FieldError("customerId", "INVALID_FORMAT", "Must be a UUID.")
        return null
    }
    return UUID.fromString(raw)
}

private fun validateMoney(money: AmountStruct?, errors: MutableList<FieldError>): Money? {
    if (money == null) {
        errors += FieldError("amount", "REQUIRED", "Required.")
        return null
    }
    val amount = validateAmount(money.amount, errors)
    val currency = validateCurrency(money.currency, errors)
    return if (amount != null && currency != null) Money(amount, currency) else null
}

private fun validateAmount(raw: String?, errors: MutableList<FieldError>): BigDecimal? {
    if (raw == null) {
        errors += FieldError("amount.amount", "REQUIRED", "Required.")
        return null
    }
    if (!AMOUNT_RE.matches(raw)) {
        errors += FieldError("amount.amount", "INVALID_FORMAT", "Must have exactly two decimals, e.g. 90.20.")
        return null
    }
    val value = BigDecimal(raw)
    if (value.signum() <= 0) {
        errors += FieldError("amount.amount", "OUT_OF_RANGE", "Must be greater than zero.")
        return null
    }
    // At most 10 integer digits and 2 decimals: fits NUMERIC(12,2) ([BIZ-23]).
    return value
}

private fun validateCurrency(raw: String?, errors: MutableList<FieldError>): Currency? {
    if (raw == null) {
        errors += FieldError("amount.currency", "REQUIRED", "Required.")
        return null
    }
    val currency: Currency? =
        if (CURRENCY_RE.matches(raw)) {
            try {
                Currency.getInstance(raw)
            } catch (e: IllegalArgumentException) {
                null
            }
        } else null
    if (currency == null)
        errors += FieldError("amount.currency", "INVALID_FORMAT", "Must be an ISO 4217 code, e.g. EUR.")
    return currency
}

/** Parse, don't validate ([API-49]): a malformed path identifier is a 400, not a lookup that misses. */
fun requirePaymentId(raw: String): String {
    if (!PAYMENT_ID_RE.matches(raw)) throw MalformedPaymentIdException(raw)
    return raw
}
