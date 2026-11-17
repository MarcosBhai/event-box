package com.marcosbhai.eventbox

import com.marcosbhai.eventbox.data.OGEventBoxRepository
import com.marcosbhai.eventbox.filter.OGEventBoxFormat
import com.marcosbhai.eventbox.filter.OGEventBoxQuery
import com.marcosbhai.eventbox.model.OGEventBoxDebugEvent
import com.marcosbhai.eventbox.viewmodel.OGEventBoxViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** v1.2–v1.4 logic: duplicates, log size, pause, sources, format helpers and expected events. */
class OGEventBoxResultsTest {

    @Before
    fun setUp() {
        OGEventBoxRepository.setActiveForTest(true)
        OGEventBoxRepository.firebaseChecksEnabled = false
        OGEventBoxRepository.detectDuplicates = true
        OGEventBoxRepository.duplicateWindowMs = 60_000L
        OGEventBoxRepository.maxEvents = OGEventBoxRepository.MAX_EVENTS
        OGEventBoxRepository.setExpectedEvents(emptyList())
        OGEventBoxRepository.clear()
    }

    @After
    fun tearDown() {
        OGEventBoxRepository.clear()
        OGEventBoxRepository.setExpectedEvents(emptyList())
        OGEventBoxRepository.maxEvents = OGEventBoxRepository.MAX_EVENTS
        OGEventBoxRepository.duplicateWindowMs = OGEventBoxRepository.DEFAULT_DUPLICATE_WINDOW_MS
        OGEventBoxRepository.firebaseChecksEnabled = true
        OGEventBoxRepository.setActiveForTest(false)
    }

    private val events get() = OGEventBoxRepository.events.value

    @Test
    fun identicalEventsBackToBackAreMerged() {
        repeat(3) { OGEventBoxRepository.log("tap", mapOf("id" to 1)) }
        assertEquals(1, events.size)
        assertEquals(3, events[0].repeatCount)
        assertTrue(events[0].isRepeated)
    }

    @Test
    fun differentParamsOrSourceAreNotMerged() {
        OGEventBoxRepository.log("tap", mapOf("id" to 1))
        OGEventBoxRepository.log("tap", mapOf("id" to 2))
        OGEventBoxRepository.log("tap", mapOf("id" to 2), source = "appsflyer")
        assertEquals(3, events.size)
        assertTrue(events.all { it.repeatCount == 1 })
    }

    @Test
    fun duplicateDetectionCanBeTurnedOff() {
        OGEventBoxRepository.detectDuplicates = false
        repeat(2) { OGEventBoxRepository.log("tap") }
        assertEquals(2, events.size)
    }

    @Test
    fun maxEventsDropsOldestAndCountsThem() {
        OGEventBoxRepository.detectDuplicates = false
        OGEventBoxRepository.maxEvents = 10
        repeat(15) { OGEventBoxRepository.log("e$it") }
        assertEquals(10, events.size)
        assertEquals("e5", events.first().name)
        assertEquals(5, OGEventBoxRepository.droppedCount)

        OGEventBoxRepository.clear()
        assertEquals(0, OGEventBoxRepository.droppedCount)
    }

    @Test
    fun maxEventsIsClamped() {
        OGEventBoxRepository.maxEvents = 1
        assertEquals(OGEventBoxRepository.MIN_MAX_EVENTS, OGEventBoxRepository.maxEvents)
        OGEventBoxRepository.maxEvents = 1_000_000
        assertEquals(OGEventBoxRepository.MAX_MAX_EVENTS, OGEventBoxRepository.maxEvents)
    }

    @Test
    fun pauseFreezesListAndCountsNewEntries() {
        val vm = OGEventBoxViewModel()
        OGEventBoxRepository.log("a", null as Map<String, Any?>?)
        vm.pause()
        OGEventBoxRepository.log("b", null as Map<String, Any?>?)
        OGEventBoxRepository.log("b") // merged repeat, still counts as a new firing
        OGEventBoxRepository.log("c", null as Map<String, Any?>?)
        assertEquals(listOf("a"), vm.visibleEvents(events).map { it.name })
        assertEquals(3, vm.newSincePause(events))

        vm.resume()
        assertEquals(listOf("a", "b", "c"), vm.visibleEvents(events).map { it.name })
        assertEquals(0, vm.newSincePause(events))
    }

    @Test
    fun sourceTagsFilterAndListInOrder() {
        OGEventBoxRepository.log("a", null as Map<String, Any?>?, source = "firebase")
        OGEventBoxRepository.log("b", null as Map<String, Any?>?, source = "mixpanel")
        OGEventBoxRepository.log("c", null as Map<String, Any?>?, source = "firebase")
        OGEventBoxRepository.log("d", null as Map<String, Any?>?)
        val vm = OGEventBoxViewModel()
        assertEquals(listOf("firebase", "mixpanel"), vm.sources(events))
        vm.setSourceFilter("firebase")
        assertEquals(listOf("a", "c"), vm.visibleEvents(events).map { it.name })
        assertTrue(OGEventBoxQuery.matches(events[1], "mixpanel", com.marcosbhai.eventbox.model.OGEventBoxFilter.ALL))
    }

    @Test
    fun expectedEventsChecklist() {
        OGEventBoxLog.expect("login_start", "login_success", "login_start", " ")
        assertEquals(listOf("login_start", "login_success"), OGEventBoxLog.expectedEvents)

        OGEventBoxRepository.log("login_start")
        OGEventBoxRepository.log("login_start")
        assertEquals(listOf("login_success"), OGEventBoxLog.missingExpectedEvents())
        assertEquals(
            listOf("login_start" to 2, "login_success" to 0),
            OGEventBoxViewModel().expectedCounts(events),
        )

        OGEventBoxLog.clearExpectations()
        assertTrue(OGEventBoxLog.expectedEvents.isEmpty())
    }

    @Test
    fun relativeTimeFormats() {
        assertEquals("", OGEventBoxFormat.relativeTime(null))
        assertEquals("", OGEventBoxFormat.relativeTime(-5))
        assertEquals("+120ms", OGEventBoxFormat.relativeTime(120))
        assertEquals("+1.2s", OGEventBoxFormat.relativeTime(1_234))
        assertEquals("+75s", OGEventBoxFormat.relativeTime(75_000))
        assertEquals("+12m", OGEventBoxFormat.relativeTime(12 * 60_000L))
    }

    @Test
    fun typeTagsTellStringsFromNumbers() {
        assertEquals("S", OGEventBoxFormat.typeTag("5"))
        assertEquals("L", OGEventBoxFormat.typeTag(5L))
        assertEquals("L", OGEventBoxFormat.typeTag(5))
        assertEquals("D", OGEventBoxFormat.typeTag(5.0))
        assertEquals("B", OGEventBoxFormat.typeTag(true))
        assertEquals("∅", OGEventBoxFormat.typeTag(null))
    }

    @Test
    fun paramLinesPadKeysAndSurviveBadValues() {
        val bad = object {
            override fun toString(): String = throw IllegalStateException("boom")
        }
        val lines = OGEventBoxFormat.paramLines(mapOf("id" to 1L, "screen" to "home", "x" to bad))
        assertEquals(3, lines.size)
        assertEquals("id    ", lines[0].paddedKey)
        assertEquals("L", lines[0].type)
        assertEquals("<unprintable>", lines[2].value)
        assertTrue(OGEventBoxFormat.paramLines(null).isEmpty())
    }

    @Test
    fun repeatCountDefaultsKeepOldConstructorWorking() {
        val event = OGEventBoxDebugEvent(1, 0L, "x")
        assertEquals(1, event.repeatCount)
        assertEquals(null, event.source)
        assertFalse(event.isRepeated)
    }
}
