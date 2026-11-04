package com.shikkhasathi.core.i18n

/**
 * Month, weekday and clock text for both locales (no platform formatter, so it is identical on Android,
 * iOS and the JVM tests). Times are expected already converted to Asia/Dhaka (`core.common.time`).
 */
object DateText {
    private val monthsBn =
        listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")
    private val monthsEn = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    // ISO order: Monday first.
    private val weekdaysBn = listOf("সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার", "রবিবার")
    private val weekdaysBnShort = listOf("সোম", "মঙ্গল", "বুধ", "বৃহঃ", "শুক্র", "শনি", "রবি")
    private val weekdaysEn = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val weekdaysEnShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private const val NOON = 12
    private const val DAWN = 4
    private const val MORNING = 6
    private const val AFTERNOON = 15
    private const val EVENING = 18
    private const val NIGHT = 20

    fun digits(
        locale: AppLocale,
        text: String,
    ): String = if (locale == AppLocale.Bn) toBanglaDigits(text) else text

    fun monthName(
        locale: AppLocale,
        month: Int,
    ): String = (if (locale == AppLocale.Bn) monthsBn else monthsEn)[(month - 1).coerceIn(0, monthsEn.lastIndex)]

    fun weekday(
        locale: AppLocale,
        isoDay: Int,
        short: Boolean = false,
    ): String {
        val names =
            when {
                locale == AppLocale.Bn && short -> weekdaysBnShort
                locale == AppLocale.Bn -> weekdaysBn
                short -> weekdaysEnShort
                else -> weekdaysEn
            }
        return names[(isoDay - 1).coerceIn(0, names.lastIndex)]
    }

    fun dayMonth(
        locale: AppLocale,
        day: Int,
        month: Int,
    ): String = digits(locale, day.toString()) + " " + monthName(locale, month)

    /** Bangla: part of day + 12-hour time ("রাত ১১:৫৯", "সকাল ১০:০০"). English: "11:59 PM". */
    fun clock(
        locale: AppLocale,
        hour: Int,
        minute: Int,
    ): String {
        val h12 = if (hour % NOON == 0) NOON else hour % NOON
        val hm = "$h12:${minute.toString().padStart(2, '0')}"
        if (locale == AppLocale.En) return "$hm ${if (hour < NOON) "AM" else "PM"}"
        val part =
            when (hour) {
                in DAWN until MORNING -> "ভোর"
                in MORNING until NOON -> "সকাল"
                in NOON until AFTERNOON -> "দুপুর"
                in AFTERNOON until EVENING -> "বিকাল"
                in EVENING until NIGHT -> "সন্ধ্যা"
                else -> "রাত"
            }
        return "$part ${toBanglaDigits(hm)}"
    }
}
