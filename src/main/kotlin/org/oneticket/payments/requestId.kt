package org.oneticket.payments

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

private const val REQUEST_ID_HEADER = "X-Request-Id"
private val SAFE_REQUEST_ID = Regex("^[A-Za-z0-9._-]{1,64}$")

/**
 * Correlation id ([OPS-12], [OPS-13], [STY-14]): reuses the caller's `X-Request-Id` when it is a
 * harmless token, otherwise generates a UUID; returns it on every response and exposes it in the
 * logging MDC, removed in `finally` because threads are pooled.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val incoming: String? = request.getHeader(REQUEST_ID_HEADER)
        val requestId = if (incoming != null && SAFE_REQUEST_ID.matches(incoming)) incoming
        else UUID.randomUUID().toString()

        response.setHeader(REQUEST_ID_HEADER, requestId)
        MDC.put("requestId", requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove("requestId")
        }
    }
}
