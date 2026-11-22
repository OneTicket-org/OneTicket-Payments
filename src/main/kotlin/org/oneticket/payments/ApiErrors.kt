package org.oneticket.payments

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.web.ErrorResponse
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class FieldError (val field: String, val code: String, val detail: String)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Problem (
    val type: String,
    val title: String,
    val status: Int,
    val detail: String?,
    val instance: String,
    val code: String,
    val errors: List<FieldError>? = null
)

open class ApiException(
    val status: HttpStatus,
    val slug: String,
    val title: String,
    val code: String,
    detail: String
) : RuntimeException(detail)

class ValidationFailedException(
    val errors: List<FieldError>,
) : ApiException(
    status = HttpStatus.UNPROCESSABLE_CONTENT,
    slug = "validation-failed",
    title = "Validation failed",
    code = "VALIDATION_FAILED",
    detail = "The request body has invalid fields."
)

class OrderAlreadyHasPaymentException(
    orderId: Long,
    paymentId: String
) : ApiException(
    status = HttpStatus.CONFLICT,
    slug = "order-already-has-payment",
    title = "Order already has payment",
    code = "ORDER_ALREADY_HAS_PAYMENT",
    detail = "Order $orderId has payment $paymentId."
)

class PaymentNotFoundException(paymentId: String) : ApiException(
    status = HttpStatus.NOT_FOUND,
    slug = "payment-not-found",
    title = "Payment not found",
    code = "PAYMENT_NOT_FOUND",
    detail = "Payment $paymentId does not exist."
)

class MalformedPaymentIdException(paymentId: String) : ApiException(
    status = HttpStatus.BAD_REQUEST,
    slug = "malformed-request",
    title = "Malformed request",
    code = "MALFORMED_REQUEST",
    detail = "Payment id '$paymentId' is not a valid payment identifier."
)

@RestControllerAdvice
class ApiExceptionHandler(private val httpServletRequest: HttpServletRequest) {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun build(
        status: HttpStatus,
        slug: String,
        title: String,
        code: String,
        detail: String?,
        req: HttpServletRequest,
        errors: List<FieldError>? = null,
        retryAfter: Int? = null,
        extraHeaders: HttpHeaders? = null,
    ) : ResponseEntity<Problem> {
        val builder = ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
        if (extraHeaders != null)
            builder.headers(extraHeaders)
        if (retryAfter != null)
            builder.header(HttpHeaders.RETRY_AFTER, retryAfter.toString())
        return builder.body(
            Problem("/problems/$slug",
                title, status.value(),
                detail,
                req.requestURI,
                code,
                errors))
    }

    @ExceptionHandler(ApiException::class)
    fun api(e: ApiException, req: HttpServletRequest) =
        build(e.status, e.slug, e.title, e.code, e.message, req, (e as? ValidationFailedException)?.errors)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun malformed(e: HttpMessageNotReadableException, req: HttpServletRequest) =
        build(HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request", "MALFORMED_REQUEST",
            "The request body is not valid JSON, has unknown fields or fields of the wrong type.", req)

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun unsupported(e: HttpMediaTypeNotSupportedException, req: HttpServletRequest) =
        build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type", "Unsupported media type",
            "UNSUPPORTED_MEDIA_TYPE", "This operation consumes application/json.", req)

    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun notAcceptable(e: HttpMediaTypeNotAcceptableException, req: HttpServletRequest) =
        build(HttpStatus.NOT_ACCEPTABLE, "not-acceptable", "Not acceptable",
            "NOT_ACCEPTABLE", "This operation produces application/json.", req)

    @ExceptionHandler(DataAccessResourceFailureException::class, CannotGetJdbcConnectionException::class)
    fun dbDown(e: Exception, req: HttpServletRequest): ResponseEntity<Problem> {
        log.error("Database unavailable", e)
        return build(HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable", "Service unavailable",
            "SERVICE_UNAVAILABLE", "The service is unavailable; retry later.", req, retryAfter = 5)
    }

    @ExceptionHandler(Exception::class)
    fun other(e: Exception, req: HttpServletRequest): ResponseEntity<Problem> {
        if (e is ErrorResponse) {
            val s = HttpStatus.valueOf(e.statusCode.value())
            // e.headers carries what HTTP requires, e.g. `Allow` on a 405 ([API-11]).
            return build(s, "http-error", s.reasonPhrase, "HTTP_${s.value()}", e.body.detail, req,
                extraHeaders = e.headers)
        }
        log.error("Unexpected error", e)
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal server error",
            "INTERNAL_ERROR", "An unexpected error occurred.", req)
    }
}