package com.shikkhasathi.core.network

import io.ktor.http.encodeURLParameter

/**
 * Relative path with a query string: `withQuery("classroom/assignments", "status" to "pending", "courseId" to null)`
 * → `classroom/assignments?status=pending`. Null values are left out; values are URL-encoded.
 */
fun withQuery(
    path: String,
    vararg params: Pair<String, Any?>,
): String {
    val query =
        params
            .filter { it.second != null }
            .joinToString("&") { (k, v) -> "${k.encodeURLParameter()}=${v.toString().encodeURLParameter()}" }
    return if (query.isEmpty()) path else "$path?$query"
}

/** Path segment (`courses/{id}/stream`) with reserved characters escaped. */
fun segment(value: String): String = value.encodeURLParameter()
