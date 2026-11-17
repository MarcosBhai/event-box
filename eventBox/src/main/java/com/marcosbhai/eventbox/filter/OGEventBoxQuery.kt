package com.marcosbhai.eventbox.filter

import com.marcosbhai.eventbox.model.OGEventBoxDebugEvent
import com.marcosbhai.eventbox.model.OGEventBoxFilter

/**
 * Matches log entries against the search bar's text query and quick filter.
 *
 * Query syntax (case-insensitive, terms separated by spaces and combined with AND):
 * - `login`        the name, or any parameter key or value, contains "login"
 * - `screen:home`  parameter `screen` has a value containing "home"
 * - `!user_prop`   excludes entries that match the term
 *
 * Pure Kotlin on purpose so it can be unit tested on the JVM; it never throws.
 */
internal object OGEventBoxQuery {

    /** Name prefix that the repository gives entries recorded via `setUserProperty`. */
    const val USER_PROPERTY_PREFIX = "user_property: "

    internal data class Term(val key: String?, val value: String, val negated: Boolean)

    fun parse(query: String): List<Term> = query.trim()
        .split(WHITESPACE)
        .filter { it.isNotEmpty() }
        .mapNotNull { raw ->
            val negated = raw.startsWith("!")
            val body = if (negated) raw.substring(1) else raw
            if (body.isEmpty()) return@mapNotNull null
            val colon = body.indexOf(':')
            if (colon > 0) {
                Term(body.substring(0, colon).lowercase(), body.substring(colon + 1).lowercase(), negated)
            } else {
                Term(null, body.lowercase(), negated)
            }
        }

    /** The positive words to highlight in a row: plain terms and the value part of `key:value`. */
    fun highlightWords(query: String): List<String> =
        parse(query).filter { !it.negated && it.value.isNotEmpty() }.map { it.value }.distinct()

    fun isUserProperty(event: OGEventBoxDebugEvent): Boolean = event.name.startsWith(USER_PROPERTY_PREFIX)

    fun matchesFilter(event: OGEventBoxDebugEvent, filter: OGEventBoxFilter): Boolean = when (filter) {
        OGEventBoxFilter.ALL -> true
        OGEventBoxFilter.WARNINGS -> event.hasWarning
        OGEventBoxFilter.EVENTS -> !isUserProperty(event)
        OGEventBoxFilter.USER_PROPERTIES -> isUserProperty(event)
    }

    fun matches(event: OGEventBoxDebugEvent, query: String, filter: OGEventBoxFilter, source: String? = null): Boolean =
        matches(event, parse(query), filter, source)

    fun matches(event: OGEventBoxDebugEvent, terms: List<Term>, filter: OGEventBoxFilter, source: String? = null): Boolean {
        if (source != null && event.source != source) return false
        if (!matchesFilter(event, filter)) return false
        return terms.all { term -> termMatches(event, term) != term.negated }
    }

    fun apply(
        events: List<OGEventBoxDebugEvent>,
        query: String,
        filter: OGEventBoxFilter,
        source: String? = null,
    ): List<OGEventBoxDebugEvent> {
        val terms = parse(query)
        if (terms.isEmpty() && filter == OGEventBoxFilter.ALL && source == null) return events
        return events.filter { matches(it, terms, filter, source) }
    }

    private fun termMatches(event: OGEventBoxDebugEvent, term: Term): Boolean {
        val params = event.params.orEmpty()
        if (term.key != null) {
            return params.entries.any { (k, v) ->
                k.lowercase() == term.key && safeString(v).lowercase().contains(term.value)
            }
        }
        if (event.name.lowercase().contains(term.value)) return true
        if (event.source?.lowercase()?.contains(term.value) == true) return true
        return params.entries.any { (k, v) ->
            k.lowercase().contains(term.value) || safeString(v).lowercase().contains(term.value)
        }
    }

    private fun safeString(value: Any?): String = runCatching { value.toString() }.getOrDefault("")

    private val WHITESPACE = Regex("\\s+")
}
