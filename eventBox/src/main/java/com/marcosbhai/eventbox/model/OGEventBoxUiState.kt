package com.marcosbhai.eventbox.model

/**
 * Encapsulates the UI state of the OGEventBox overlay for MVVM pattern.
 */
data class OGEventBoxUiState(
    val events: List<OGEventBoxDebugEvent> = emptyList(),
    val isActive: Boolean = false,
    val isCollapsed: Boolean = true,
    val isPinned: Boolean = false,
    val unreadCount: Int = 0,
    val remainingMinutes: Int = 0,
    /** Text typed into the search bar, or empty. */
    val query: String = "",
    /** The selected quick filter chip. */
    val filter: OGEventBoxFilter = OGEventBoxFilter.ALL,
    /** [events] after [query] and [filter] are applied; equals [events] when nothing is set. */
    val visibleEvents: List<OGEventBoxDebugEvent> = events,
    /** True while the search bar is open. */
    val isSearchOpen: Boolean = false,
    /** The selected source chip, or null for every source. */
    val sourceFilter: String? = null,
    /** True while the list is frozen. */
    val isPaused: Boolean = false,
) {
    /** True when the query or filter is hiding some entries. */
    val isFiltering: Boolean get() = query.isNotBlank() || filter != OGEventBoxFilter.ALL || sourceFilter != null
}
