package com.seblful.klas.domain.model

/**
 * How a payment was made. **Optional on every payment**: those recorded before it was asked have none, and a
 * tutor may not say. It moves no money — a balance is the same whatever it is — so an unreadable stored name
 * reads as none rather than failing (see `EnumConverters`).
 */
enum class PaymentMethod {
    CASH,
    CARD,
    TRANSFER,
    ;

    companion object {
        fun parse(stored: String?): PaymentMethod? = entries.firstOrNull { it.name == stored }
    }
}
