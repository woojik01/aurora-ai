package com.aurora.core.domain.model

/**
 * Locally generated, time-sortable identifier (ULID-like).
 *
 * Rationale (PRD-02): records must be creatable offline on multiple devices,
 * so identity must never depend on a central auto-increment sequence.
 */
class AuroraId private constructor(val value: String) {

    override fun toString(): String = value
    override fun equals(other: Any?): Boolean = other is AuroraId && other.value == value
    override fun hashCode(): Int = value.hashCode()

    companion object {
        // Crockford base32 alphabet (no I, L, O, U).
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private const val TIME_CHARS = 10
        private const val RANDOM_CHARS = 16
        private val random = java.security.SecureRandom()

        fun generate(nowMillis: Long = System.currentTimeMillis()): AuroraId {
            require(nowMillis >= 0) { "nowMillis must be >= 0" }
            val sb = StringBuilder(TIME_CHARS + RANDOM_CHARS)
            var ts = nowMillis
            for (i in 0 until TIME_CHARS) {
                sb.insert(0, ALPHABET[(ts % 32).toInt()])
                ts /= 32
            }
            for (i in 0 until RANDOM_CHARS) {
                sb.append(ALPHABET[random.nextInt(32)])
            }
            return AuroraId(sb.toString())
        }
    }
}
