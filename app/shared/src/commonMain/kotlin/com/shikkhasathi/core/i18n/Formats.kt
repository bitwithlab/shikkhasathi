package com.shikkhasathi.core.i18n

/*
 * Number formats shared by course cards, the player, downloads and mock tests. Every function returns [UiText],
 * so Bangla numerals follow the app locale and the logic is unit-tested on the JVM.
 */

private const val SEC_PER_MIN = 60
private const val MIN_PER_HOUR = 60
private const val KB = 1024.0
private const val TENTHS = 10
private const val GROUP = 3

/** Course/lesson length: "৪৫ মিনিট", "২ ঘণ্টা ১০ মিনিট", "৩ ঘণ্টা". Anything under a minute reads "১ মিনিট". */
fun durationText(seconds: Int): UiText {
    val minutes = ((seconds + SEC_PER_MIN / 2) / SEC_PER_MIN).coerceAtLeast(1)
    val h = minutes / MIN_PER_HOUR
    val m = minutes % MIN_PER_HOUR
    return when {
        h == 0 -> Str.DurationMinutes.asUiText("n" to m)
        m == 0 -> Str.DurationHours.asUiText("n" to h)
        else -> Str.DurationHoursMinutes.asUiText("h" to h, "m" to m)
    }
}

/** Player / timer clock: "৫:২৩", "১:০৫:০৯". Negative values read as 0. */
fun clockText(seconds: Int): UiText {
    val s = seconds.coerceAtLeast(0)
    val h = s / (SEC_PER_MIN * MIN_PER_HOUR)
    val m = (s / SEC_PER_MIN) % MIN_PER_HOUR
    val sec = s % SEC_PER_MIN
    val text = if (h > 0) "$h:${m.pad()}:${sec.pad()}" else "$m:${sec.pad()}"
    return UiText.Digits(text)
}

/** File size: "৮৫০ KB", "২.৪ MB", "১.১ GB". */
fun sizeText(bytes: Long): UiText {
    val kb = bytes / KB
    return when {
        kb < KB -> Str.SizeKb.asUiText("n" to UiText.Digits(kb.toLong().coerceAtLeast(1).toString()))
        kb < KB * KB -> Str.SizeMb.asUiText("n" to UiText.Digits(oneDecimal(kb / KB)))
        else -> Str.SizeGb.asUiText("n" to UiText.Digits(oneDecimal(kb / (KB * KB))))
    }
}

/** Whole taka: "৳৩৪৯", "৳১,৪৯৯" (Bangladeshi grouping isn't needed below a lakh). */
fun takaText(amount: Int): UiText = Str.CoursePrice.asUiText("price" to UiText.Digits(grouped(amount)))

private fun grouped(amount: Int): String {
    val digits = amount.coerceAtLeast(0).toString()
    return digits
        .reversed()
        .chunked(GROUP)
        .joinToString(",")
        .reversed()
}

private fun oneDecimal(value: Double): String {
    val tenths = kotlin.math.round(value * TENTHS).toLong()
    return "${tenths / TENTHS}.${tenths % TENTHS}"
}

private fun Int.pad(): String = toString().padStart(2, '0')
