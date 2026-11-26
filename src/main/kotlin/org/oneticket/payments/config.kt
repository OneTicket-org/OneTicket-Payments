package org.oneticket.payments

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.UUID

/** Time is an injected port ([SVC-15]); tests replace it with `Clock.fixed(...)`. */
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

@Component
class UuidPaymentIdGenerator : PaymentIdGenerator {
    // Matches the PaymentId schema of the contract: ^pay_[0-9A-Z]{10,26}$
    override fun next(): String =
        "pay_" + UUID.randomUUID().toString().replace("-", "").uppercase().take(26)
}
