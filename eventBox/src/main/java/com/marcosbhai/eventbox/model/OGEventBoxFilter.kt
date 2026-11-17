package com.marcosbhai.eventbox.model

/**
 * Quick filters shown as chips in the Event Box search bar. They combine with the text query.
 */
enum class OGEventBoxFilter {
    /** Every logged entry. */
    ALL,

    /** Only entries that broke a Firebase limit (see [OGEventBoxDebugEvent.hasWarning]). */
    WARNINGS,

    /** Only analytics events, without user properties. */
    EVENTS,

    /** Only user properties recorded through `OGEventBoxLog.setUserProperty`. */
    USER_PROPERTIES,
}
