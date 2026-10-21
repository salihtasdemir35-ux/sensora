package com.sensora.app.core.model

/**
 * User-facing measurement quality. Never assigned arbitrarily: every module derives it
 * from explicit rules in [com.sensora.app.core.math.QualityRules].
 */
enum class Quality {
    GOOD, LIMITED, UNRELIABLE;

    companion object {
        fun worst(a: Quality, b: Quality): Quality = if (a.ordinal >= b.ordinal) a else b
    }
}
