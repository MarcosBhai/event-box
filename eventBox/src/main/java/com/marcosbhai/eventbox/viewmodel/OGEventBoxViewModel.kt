package com.marcosbhai.eventbox.viewmodel

import android.content.Context
import com.marcosbhai.eventbox.data.OGEventBoxRepository
import com.marcosbhai.eventbox.filter.OGEventBoxQuery
import com.marcosbhai.eventbox.model.OGEventBoxDebugEvent
import com.marcosbhai.eventbox.model.OGEventBoxFilter
import com.marcosbhai.eventbox.model.OGEventBoxPosition
import com.marcosbhai.eventbox.model.OGEventBoxUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel managing presentation logic and UI state for OGEventBox.
 */
internal class OGEventBoxViewModel(private val repository: OGEventBoxRepository = OGEventBoxRepository) {

    var anchorX: Float = Float.NaN
    var anchorY: Float = Float.NaN

    private var _isCollapsed: Boolean = true
    val isCollapsed: Boolean get() = _isCollapsed

    private var _isPinned: Boolean = false
    val isPinned: Boolean get() = _isPinned

    var lastSeenIndex: Int = 0
    var customPanelHeight: Int? = null

    private var _query: String = ""
    /** Text typed into the search bar. Kept here so it survives the view being re-created. */
    val query: String get() = _query

    private var _filter: OGEventBoxFilter = OGEventBoxFilter.ALL
    val filter: OGEventBoxFilter get() = _filter

    private var _isSearchOpen: Boolean = false
    val isSearchOpen: Boolean get() = _isSearchOpen

    private var _sourceFilter: String? = null
    /** When set, only entries logged with this source tag are shown. */
    val sourceFilter: String? get() = _sourceFilter

    private var pausedSnapshot: List<OGEventBoxDebugEvent>? = null
    /** True while the list is frozen; the repository keeps recording underneath. */
    val isPaused: Boolean get() = pausedSnapshot != null

    /** True when the query or filter is hiding some entries. */
    val isFiltering: Boolean get() = _query.isNotBlank() || _filter != OGEventBoxFilter.ALL || _sourceFilter != null

    private val _uiState = MutableStateFlow(OGEventBoxUiState())
    val uiState: StateFlow<OGEventBoxUiState> = _uiState.asStateFlow()

    val events: StateFlow<List<OGEventBoxDebugEvent>> get() = repository.events
    val isActive: Boolean get() = repository.isActive
    val isActiveFlow: StateFlow<Boolean> get() = repository.isActiveFlow

    /** Names passed to `OGEventBoxLog.expect`, for the checklist strip. */
    val expectedEvents: StateFlow<List<String>> get() = repository.expectedEvents

    /** Entries dropped from the top of the log because it was full. */
    val droppedCount: Int get() = repository.droppedCount
    val maxEvents: Int get() = repository.maxEvents

    /** Times each expected name has fired in [events] (repeats included), in [expectedEvents] order. */
    fun expectedCounts(events: List<OGEventBoxDebugEvent>): List<Pair<String, Int>> {
        val expected = repository.expectedEvents.value
        if (expected.isEmpty()) return emptyList()
        val counts = HashMap<String, Int>()
        for (event in events) counts[event.name] = (counts[event.name] ?: 0) + event.repeatCount
        return expected.map { it to (counts[it] ?: 0) }
    }

    fun resetSessionState() {
        anchorX = Float.NaN
        anchorY = Float.NaN
        _isCollapsed = true
        _isPinned = false
        lastSeenIndex = 0
        customPanelHeight = null
        resetSearch()
        updateUiState()
    }

    fun collapse() {
        if (_isCollapsed) return
        _isCollapsed = true
        lastSeenIndex = repository.events.value.lastOrNull()?.index ?: 0
        updateUiState()
    }

    fun expand() {
        if (!_isCollapsed) return
        _isCollapsed = false
        lastSeenIndex = repository.events.value.lastOrNull()?.index ?: 0
        updateUiState()
    }

    fun togglePin(): Boolean {
        _isPinned = !_isPinned
        updateUiState()
        return _isPinned
    }

    fun clearEvents() {
        repository.clear()
        lastSeenIndex = 0
        resetSearch()
        updateUiState()
    }

    fun setQuery(text: String) {
        if (_query == text) return
        _query = text
        updateUiState()
    }

    fun setFilter(filter: OGEventBoxFilter) {
        if (_filter == filter) return
        _filter = filter
        updateUiState()
    }

    fun openSearch() {
        if (_isSearchOpen) return
        _isSearchOpen = true
        updateUiState()
    }

    /** Closes the search bar and drops the query and filter, so nothing stays hidden unseen. */
    fun closeSearch() {
        if (!_isSearchOpen && !isFiltering) return
        resetSearch()
        updateUiState()
    }

    /** Selects a source chip, or shows every source when [source] is null. */
    fun setSourceFilter(source: String?) {
        if (_sourceFilter == source) return
        _sourceFilter = source
        updateUiState()
    }

    /** Freezes the list on what is shown now. New events keep being recorded. */
    fun pause() {
        if (pausedSnapshot != null) return
        pausedSnapshot = repository.events.value
        updateUiState()
    }

    fun resume() {
        if (pausedSnapshot == null) return
        pausedSnapshot = null
        updateUiState()
    }

    /** Entries recorded since [pause]; 0 while not paused. */
    fun newSincePause(events: List<OGEventBoxDebugEvent>): Int {
        val snapshot = pausedSnapshot ?: return 0
        val lastIndex = snapshot.lastOrNull()?.index ?: 0
        val repeatsBefore = snapshot.lastOrNull()?.repeatCount ?: 0
        return events.sumOf { event ->
            when {
                event.index > lastIndex -> event.repeatCount
                event.index == lastIndex -> (event.repeatCount - repeatsBefore).coerceAtLeast(0)
                else -> 0
            }
        }
    }

    /** Every source tag present in [events], in first-seen order (for the source chips). */
    fun sources(events: List<OGEventBoxDebugEvent>): List<String> =
        events.mapNotNullTo(LinkedHashSet()) { it.source }.toList()

    /**
     * What the list shows: the paused snapshot (or the live [events]) narrowed by the current
     * query, filter and source. The unread badge never uses this.
     */
    fun visibleEvents(events: List<OGEventBoxDebugEvent>): List<OGEventBoxDebugEvent> =
        OGEventBoxQuery.apply(pausedSnapshot ?: events, _query, _filter, _sourceFilter)

    fun closeTargetDropped() {
        repository.disable()
        resetSessionState()
    }

    fun getSavedPosition(context: Context): OGEventBoxPosition? =
        repository.getBubblePosition(context)

    fun persistPosition(context: Context, onRightEdge: Boolean, yRatio: Float) {
        if (!repository.isActive) return
        repository.saveBubblePosition(context, OGEventBoxPosition(onRightEdge, yRatio))
    }

    fun getUnreadCount(eventsList: List<OGEventBoxDebugEvent>): Int {
        return eventsList.count { it.index > lastSeenIndex }
    }

    private fun resetSearch() {
        _query = ""
        _filter = OGEventBoxFilter.ALL
        _sourceFilter = null
        _isSearchOpen = false
        // Closing the tools bar also resumes, so the list never stays frozen out of sight.
        pausedSnapshot = null
    }

    private fun updateUiState() {
        val currentEvents = repository.events.value
        _uiState.value = OGEventBoxUiState(
            events = currentEvents,
            isActive = repository.isActive,
            isCollapsed = _isCollapsed,
            isPinned = _isPinned,
            unreadCount = getUnreadCount(currentEvents),
            remainingMinutes = repository.getContext()?.let { repository.remainingMinutes(it) } ?: 0,
            query = _query,
            filter = _filter,
            visibleEvents = visibleEvents(currentEvents),
            isSearchOpen = _isSearchOpen,
            sourceFilter = _sourceFilter,
            isPaused = isPaused,
        )
    }
}
