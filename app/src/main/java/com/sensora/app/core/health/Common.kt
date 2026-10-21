package com.sensora.app.core.health

/** How soon a person should act on a result. Wording is in the UI; thresholds live in this package. */
enum class Urgency { NORMAL, DISCUSS, SOON, URGENT;
    companion object { fun max(a: Urgency, b: Urgency) = if (a.ordinal >= b.ordinal) a else b }
}

enum class Sex { FEMALE, MALE }
