package com.marcosbhai.eventbox

import com.marcosbhai.eventbox.filter.OGEventBoxExport
import com.marcosbhai.eventbox.filter.OGEventBoxQuery
import com.marcosbhai.eventbox.model.OGEventBoxDebugEvent
import com.marcosbhai.eventbox.model.OGEventBoxFilter
import com.marcosbhai.eventbox.viewmodel.OGEventBoxViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OGEventBoxSearchTest {

    private val login = OGEventBoxDebugEvent(1, 1_000L, "login_success", mapOf("screen" to "home", "method" to "google"))
    private val purchase = OGEventBoxDebugEvent(
        2, 2_000L, "purchase", mapOf("value" to 9.99, "currency" to "USD"),
        warnings = listOf("Too many parameters"),
    )
    private val userProp = OGEventBoxDebugEvent(
        3, 3_000L, "user_property: plan", mapOf("property_name" to "plan", "property_value" to "pro"),
    )
    private val noParams = OGEventBoxDebugEvent(4, 4_000L, "app_open")
    private val all = listOf(login, purchase, userProp, noParams)

    private fun names(query: String, filter: OGEventBoxFilter = OGEventBoxFilter.ALL) =
        OGEventBoxQuery.apply(all, query, filter).map { it.name }

    @Test
    fun plainTermMatchesNameCaseInsensitive() {
        assertEquals(listOf("login_success"), names("LOGIN"))
    }

    @Test
    fun plainTermMatchesParamKeysAndValues() {
        assertEquals(listOf("login_success"), names("google"))
        assertEquals(listOf("purchase"), names("currency"))
        assertEquals(listOf("purchase"), names("9.99"))
    }

    @Test
    fun keyValueTermMatchesOnlyThatKey() {
        assertEquals(listOf("login_success"), names("screen:ho"))
        assertEquals(emptyList<String>(), names("method:home"))
        assertEquals(emptyList<String>(), names("missing:home"))
    }

    @Test
    fun negationExcludes() {
        assertEquals(listOf("login_success", "purchase", "app_open"), names("!user_property"))
    }

    @Test
    fun termsCombineWithAnd() {
        assertEquals(listOf("login_success"), names("login screen:home"))
        assertEquals(emptyList<String>(), names("login purchase"))
    }

    @Test
    fun blankQueryAndAllFilterReturnSameList() {
        assertSame(all, OGEventBoxQuery.apply(all, "   ", OGEventBoxFilter.ALL))
        assertTrue(OGEventBoxQuery.parse("! ").isEmpty())
    }

    @Test
    fun eachFilterSelectsItsEntries() {
        assertEquals(listOf("purchase"), names("", OGEventBoxFilter.WARNINGS))
        assertEquals(listOf("login_success", "purchase", "app_open"), names("", OGEventBoxFilter.EVENTS))
        assertEquals(listOf("user_property: plan"), names("", OGEventBoxFilter.USER_PROPERTIES))
        assertEquals(listOf("purchase"), names("usd", OGEventBoxFilter.EVENTS))
    }

    @Test
    fun valueWhoseToStringThrowsDoesNotCrash() {
        val bad = OGEventBoxDebugEvent(9, 0L, "weird", mapOf("x" to object {
            override fun toString(): String = throw IllegalStateException("boom")
        }))
        assertFalse(OGEventBoxQuery.matches(bad, "boom", OGEventBoxFilter.ALL))
        assertTrue(OGEventBoxQuery.matches(bad, "weird", OGEventBoxFilter.ALL))
        assertTrue(OGEventBoxExport.eventJson(bad).contains("\"x\": \"<unprintable>\""))
    }

    @Test
    fun highlightWordsSkipNegatedTermsAndUseValues() {
        assertEquals(listOf("login", "home"), OGEventBoxQuery.highlightWords("login screen:home !purchase"))
    }

    @Test
    fun exportEscapesAndTypesValues() {
        val event = OGEventBoxDebugEvent(
            5, 0L, "quote\"d", linkedMapOf("n" to 3L, "b" to true, "s" to "a\nb", "nil" to null),
            warnings = listOf("w1"),
        )
        val json = OGEventBoxExport.eventJson(event)
        assertTrue(json.contains("\"name\": \"quote\\\"d\""))
        assertTrue(json.contains("\"n\": 3"))
        assertTrue(json.contains("\"b\": true"))
        assertTrue(json.contains("\"s\": \"a\\nb\""))
        assertTrue(json.contains("\"nil\": null"))
        assertTrue(json.contains("\"warnings\": [\"w1\"]"))
        assertEquals("[]", OGEventBoxExport.logJson(emptyList()))
        val log = OGEventBoxExport.logJson(listOf(login, noParams))
        assertTrue(log.startsWith("[\n") && log.endsWith("\n]"))
        assertTrue(log.contains("\"params\": {}"))
    }

    @Test
    fun viewModelSearchStateResets() {
        val vm = OGEventBoxViewModel()
        vm.openSearch()
        vm.setQuery("login")
        vm.setFilter(OGEventBoxFilter.WARNINGS)
        assertTrue(vm.isSearchOpen)
        assertTrue(vm.isFiltering)
        assertEquals("login", vm.uiState.value.query)
        assertEquals(OGEventBoxFilter.WARNINGS, vm.uiState.value.filter)

        vm.closeSearch()
        assertFalse(vm.isSearchOpen)
        assertFalse(vm.isFiltering)

        vm.setQuery("x")
        vm.clearEvents()
        assertEquals("", vm.query)

        vm.setFilter(OGEventBoxFilter.EVENTS)
        vm.resetSessionState()
        assertEquals(OGEventBoxFilter.ALL, vm.filter)
    }

    @Test
    fun unreadCountIgnoresFilter() {
        val vm = OGEventBoxViewModel()
        vm.setQuery("login")
        assertEquals(1, vm.visibleEvents(all).size)
        assertEquals(4, vm.getUnreadCount(all))
    }
}
