package com.shikkhasathi.core.model

/**
 * Bangladeshi mobile number normalisation — same rules as `BdPhone` in website/src/api/contracts/common.ts:
 * strips spaces/dashes, accepts `01…`, `8801…`, `+8801…`, and returns `+8801XXXXXXXXX` or `null`.
 * Bangla digits (০-৯) are accepted too, because many keyboards type them by default.
 */
object BdPhone {
    private val valid = Regex("^\\+8801[3-9]\\d{8}$")
    private const val LOCAL_LENGTH = 11
    private const val PREFIX_LENGTH = 5

    fun normalize(input: String): String? {
        val ascii = toAsciiDigits(input.trim()).replace(Regex("[\\s-]"), "")
        val e164 =
            when {
                ascii.startsWith("+880") -> ascii
                ascii.startsWith("880") -> "+$ascii"
                ascii.startsWith("0") -> "+88$ascii"
                else -> ascii
            }
        return e164.takeIf { valid.matches(it) }
    }

    /** `+8801712345678` → `01712-345678` for display. */
    fun pretty(e164: String): String {
        val local = e164.removePrefix("+88")
        return if (local.length == LOCAL_LENGTH) "${local.take(PREFIX_LENGTH)}-${local.drop(PREFIX_LENGTH)}" else e164
    }

    fun toAsciiDigits(input: String): String =
        buildString(input.length) {
            input.forEach { c -> append(if (c in '০'..'৯') '0' + (c - '০') else c) }
        }
}
