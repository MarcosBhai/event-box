# Event Box: Improvement Plan

> Current release: **1.0.7** (see [`CHANGELOG.md`](../CHANGELOG.md)). Status: 1.1.0 to 1.4.0 are implemented and released in 1.0.5 (not
> as separate versions). Every item is marked **✅ DONE**, except the few marked **⬜ NOT DONE** or
> **⏳ PENDING** with the reason. Line references point at the code as of 1.0.4 and may move.
> Items taken out again after 1.0.5 are marked **🗑 REMOVED**; see the next section.

## Changes after 1.0.5 (released in 1.0.6)

The panel was simplified. Each change is also noted where its plan item appears below.

| Change | Replaces |
|---|---|
| 🗑 Filter chips removed (All / ⚠ Warnings / Events / User props / source chips). The search bar is a text field only, and opens with the keyboard ready. | v1.1.0 §3, §5, §6; v1.4.0 item 2 |
| `is:` search keywords: `is:warning`, `is:user_property`, `is:event`, `is:repeated` (negate with `!`). | The chips' filters |
| 🗑 `OGEventBoxFilter` enum and `OGEventBoxUiState.filter` / `sourceFilter` removed (**breaking**). | v1.1.0 §3 |
| 🗑 Expected-event checklist removed, with `expect()`, `clearExpectations()`, `expectedEvents` and `missingExpectedEvents()` (**breaking**). | v1.4.0 item 1 |
| 🗑 Header Resize and Minimize buttons removed: tap the logo to shrink, drag the bottom handle to resize. | n/a |
| Pause and Share moved from the search bar to the header. Closing search no longer resumes; Clear does. | v1.3.0 item 2 |
| Header buttons are 40×48 dp touch targets (visible 32×32), sized from `og_event_box_header_*` dimens shared by the layout and `applyHeaderOrientation()`. | v1.2.0 item 7 |
| Clear uses a trash icon and offers a 4-second **Undo**. | n/a |
| Bottom handle: double-tap resets the height; it pulses on open until first used. Height and opacity are remembered across sessions. | n/a |
| One-time gesture tip; TalkBack labels for the logo, handle and undo; haptics on Clear, Undo and resize. | n/a |

These removals are breaking; 1.0.6 ships them with an upgrade table in the changelog.

## Roadmap at a glance

| Version | Theme | Items | Status |
|---|---|---|---|
| **1.1.0** | Find events fast | Filter chips (🗑 REMOVED), copy/share, text search | ✅ DONE (manual device tests ⏳ PENDING) |
| **1.2.0** | Performance + view | RecyclerView migration, params table, colour-coding, jump-to-latest | ✅ DONE |
| **1.3.0** | Smarter results | Duplicate detection, pause/freeze, configurable max events, relative time, value types | ✅ DONE |
| **1.4.0** | Verification + polish | Expected-event checklist (🗑 REMOVED), source tags, opacity, accessibility, tablet width | ✅ DONE |

Why this order:
1. **Filter chips** are the quickest win and need no keyboard, so there are no window-focus problems.
2. **Copy/share** makes QA bug reports useful straight away.
3. **Text search** is the main feature, but it needs the focus-flag work described below.
4. **RecyclerView** before more features, because filtering makes today's full rebind more costly.
5. **Duplicate detection and pause** build on the new list.

---

## v1.1.0: Search and filtering

### 1. Goal ✅ DONE
Let a tester narrow the log by event name, parameter key or value, and warning state, without
leaving the overlay.

### 2. Key risk: window focus and the keyboard ✅ DONE
> Done in `grantWindowFocus()` / `releaseSearchFocus()`. `ADJUST_PAN` is set when focus is
> granted rather than up front. Back first hides the keyboard and gives focus back to the host
> app; the bar stays open with its query, and ✕ or 🔍 closes it.
Both window types use `FLAG_NOT_FOCUSABLE`:
- `OGEventBoxView.windowParams()` (`OGEventBoxView.kt` ~1331)
- `OGEventBoxView.overlayWindowParams()` (`OGEventBoxView.kt` ~1349)

With that flag set, an `EditText` never receives focus and the IME never opens. Plan:

| Step | Action |
|---|---|
| Search field tapped | Clear `FLAG_NOT_FOCUSABLE` → `windowManager.updateViewLayout(this, params)` → `requestFocus()` → `InputMethodManager.showSoftInput()` |
| Search closed / panel collapsed / bubble shrunk / app backgrounded | Hide IME → set `FLAG_NOT_FOCUSABLE` again → `updateViewLayout` |
| Back pressed while focused | Handle via `dispatchKeyEvent(KEYCODE_BACK)`: first clear focus, then close search |
| Keyboard showing | Set `softInputMode = SOFT_INPUT_ADJUST_RESIZE` (or `ADJUST_PAN`) on both param sets |
| While focused | Suspend `scheduleAutoShrink()` so the panel doesn't close mid-typing |

**Must never happen:** the box keeping focus after search closes. That would steal the back
button and key input from the host app.

Where the box is added as a child of the Activity content view (fallback path in
`OGEventBoxOverlay.kt` ~128), no flag change is needed, but the IME handling still applies.

### 3. Data layer ✅ DONE
> 🗑 REMOVED after 1.0.5: `OGEventBoxFilter` and the `filter` field. The query alone does this now, with
> `is:` keywords (`is:warning`, `is:user_property`, `is:event`, `is:repeated`).
> `totalCount` was not added as a field; the view uses `events.size`. A `type` field was not
> added either; user properties are still detected by the name prefix.

**`OGEventBoxUiState`**: add
```kotlin
val query: String = "",
val filter: OGEventBoxFilter = OGEventBoxFilter.ALL,
val visibleEvents: List<OGEventBoxDebugEvent> = emptyList(),
val totalCount: Int = 0,
```

**New `model/OGEventBoxFilter.kt`**
```kotlin
enum class OGEventBoxFilter { ALL, WARNINGS, EVENTS, USER_PROPERTIES }
```
`USER_PROPERTIES` matches names starting with `"user_property: "` (see
`OGEventBoxRepository.setUserProperty`). Consider adding an explicit `type` field on
`OGEventBoxDebugEvent` instead of relying on the name prefix (with a default value, so it stays
binary compatible through `@JvmOverloads`).

**New `filter/OGEventBoxQuery.kt`**: a pure, unit-testable matcher:
```kotlin
internal object OGEventBoxQuery {
    fun matches(event: OGEventBoxDebugEvent, query: String, filter: OGEventBoxFilter): Boolean
}
```
Query syntax (case-insensitive):
| Input | Meaning |
|---|---|
| `login` | name **or** any param key/value contains "login" |
| `screen:home` | param `screen` value contains "home" |
| `!user_property` | exclude events matching the term |
| `login screen:home` | space = AND across terms |

Parameter values are compared with `toString()` inside `runCatching`, because the library must never throw.

### 4. ViewModel (`OGEventBoxViewModel`) ✅ DONE (option **b**, synchronous)
- Add `private val query = MutableStateFlow("")` and `private val filter = MutableStateFlow(ALL)`.
- Today the class has no coroutine scope. Either:
  - **(a)** add a `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` that is
    cancelled when the session resets, then `combine(repository.events, query.debounce(150), filter)`; or
  - **(b)** keep it synchronous: filter inside `updateUiState()` and call it from the view's
    events collector and from `setQuery()`/`setFilter()`.
  With at most 200 events, **(b)** is enough and simpler. Choose (a) only together with the
  RecyclerView move.
- Add `fun setQuery(text: String)`, `fun setFilter(f: OGEventBoxFilter)` and `fun clearSearch()`.
- `resetSessionState()` and `clearEvents()` also clear the query and filter.
- Unread badge logic stays on **all** events, not just the filtered ones.

### 5. View (`OGEventBoxView`) ✅ DONE
**Layout (`og_event_box.xml`)**
- Add a 🔍 `ImageButton` `og_event_box_search` to the header. It must also be added to both
  branches of `applyHeaderOrientation()` (~537–644), which rebuilds the header for left and right sides.
- Add a collapsible row `og_event_box_search_bar` between the header and `og_event_box_content_container`:
  - `EditText og_event_box_search_input` (single line, `imeOptions="actionSearch"`, hint
    "Search name, key:value…")
  - ✕ clear button
  - ~~`HorizontalScrollView` with chips: All · ⚠ Warnings · Events · User props~~ 🗑 REMOVED after 1.0.5
- ~~**Chips are visible without opening the keyboard.** Tapping 🔍 shows the bar, and only tapping
  the EditText switches focus as described in §2.~~ 🗑 With the chips gone, 🔍 opens the bar and
  focuses the field straight away (same focus handling as §2).

**Rendering**
- `renderEvents()` renders `visibleEvents` instead of `events`.
- Count chip: `"12 / 200"` when a filter is active, otherwise `"200"`.
- Empty state: "No events match" (new string) when `visibleEvents` is empty but the total is not.
- **Auto-scroll:** skip `fullScroll(FOCUS_DOWN)` (~316) while a query or filter is active.
- **Highlight:** in `bindRow()` add a `BackgroundColorSpan` (new colour
  `og_event_box_search_highlight`) over matches in the name. The params text can be highlighted the same way.
- **`bindKey` (~361) must include the query** (or a hash of it), otherwise rows won't rebind
  when only the query changes.
- `expandedEventIndex` is kept when the row is still visible and cleared when it is filtered out
  (this logic already exists at ~298).

### 6. Strings, colours and dimens ✅ DONE
> 🗑 REMOVED after 1.0.5: the four `og_event_box_filter_*` strings and `og_event_box_chip_selected`.
- `strings.xml`: `og_event_box_search`, `og_event_box_search_hint`,
  `og_event_box_search_clear`, `og_event_box_no_match`, `og_event_box_filter_all`,
  `og_event_box_filter_warnings`, `og_event_box_filter_events`, `og_event_box_filter_user_props`,
  `og_event_box_count_filtered` (`%1$d / %2$d`).
- `colors.xml`: `og_event_box_search_highlight`, `og_event_box_chip_selected`, `og_event_box_chip`.

### 7. Public API (optional) ⬜ NOT DONE (left out on purpose, as this section advises)
```kotlin
OGEventBoxLog.search(query: String)  // open the panel pre-filtered, e.g. from a debug menu
```
Keep it out of 1.1.0 unless it's needed, because adding it later is non-breaking.

### 8. Tests
- ✅ DONE `OGEventBoxQueryTest` (as `OGEventBoxSearchTest`): plain term, `key:value`, negation, AND, case-insensitivity, null
  params, a value whose `toString()` throws, and each filter enum value.
- ✅ DONE `OGEventBoxViewModel`: the query is reset on `clearEvents()` and `resetSessionState()`, and the
  unread count ignores the filter.
- ✅ DONE Java interop: if `OGEventBoxFilter` or a public `search()` is exposed, add it to `OGEventBoxJavaInteropTest`.
- ⏳ PENDING (needs a real device) Manual: Activity-window mode, overlay mode (AUTO, OVERLAY_ONLY), rotation with the keyboard
  open, back button behaviour, the host app's EditText still working after search closes.

### 9. Other 1.1.0 items ✅ DONE
- **Copy:** long-press a row to copy `{"name": ..., "params": {...}, "warnings": [...]}` to the
  clipboard and show a toast. A `ClipboardManager` call doesn't need window focus.
- **Share log:** add a header action (or an overflow menu, since the header is crowded) that
  builds a text/JSON dump of the **visible** events and fires `Intent.ACTION_SEND` with
  `FLAG_ACTIVITY_NEW_TASK` (this is needed when sharing from the overlay window).

---

## v1.2.0: Better view (UI and performance) ✅ DONE

1. ✅ DONE **RecyclerView + ListAdapter/DiffUtil**
   - Today: `ScrollView` + `LinearLayout`, with up to 200 inflated rows and every row rebound on each event.
   - Replace with `RecyclerView` and `ListAdapter<OGEventBoxDebugEvent, VH>`, using
     `DiffUtil.ItemCallback` keyed on `index`.
   - Keep the expand/collapse state in the adapter. Use `ItemAnimator` or a payload-based partial
     bind instead of `TransitionManager` on the whole list.
   - Adds the `androidx.recyclerview` dependency. Check its size and the consumer R8 rules.
2. ✅ DONE **Params as a key/value table:** key column in `og_event_box_time` colour, value in
   `og_event_box_name`, aligned (a `TableLayout`, or monospace with padded keys).
3. ✅ DONE **Colour-coding:** a 3 dp left stripe or dot per row: warning = amber, user property = blue,
   normal = none.
4. ✅ DONE (as a "↓ N new" pill) **"Jump to latest" FAB:** shown when the user has scrolled up and new events arrive, with a "+N" label.
5. ✅ DONE **Move hard-coded strings to `strings.xml`:** `"Parameters:"` (`og_event_box_item.xml`),
   `"Event: ${name}"` and `"  (no parameters)"` (`OGEventBoxView.bindRow`).
6. ✅ DONE (`setPanelAlpha`, long-press the logo to cycle) **Panel opacity:** a builder option `panelAlpha(0.6f..1f)`, with an optional long-press on
   the title to cycle through values.
7. ✅ DONE (40×48 dp after 1.0.5) **Accessibility:** header buttons are 32–34 dp. Bring them to a 48 dp touch target (padding or
   `TouchDelegate`). Add row `contentDescription` (`"Event 12, login_success, 3 parameters, has warning"`).
8. ✅ DONE **Tablet and landscape:** cap the panel width at about 480 dp instead of `match_parent`.

---

## v1.3.0: Better results (data and logic) ✅ DONE

1. ✅ DONE **Duplicate detection:** in `OGEventBoxRepository.append()`, when the previous event has the
   same name and equal params within 500 ms, increment a `repeatCount` on it instead of (or as
   well as) adding a row. Show `×3`. Add `repeatCount: Int = 1` to `OGEventBoxDebugEvent` with a
   default value for binary compatibility. Make it configurable: `detectDuplicates(true)` and `duplicateWindowMs(500)`.
2. ✅ DONE (moved to the header after 1.0.5, next to Share) **Pause / freeze:** a header toggle. While paused, the view keeps rendering a snapshot, the
   repository keeps recording, and a "Resume (+N)" banner appears. This is view-model state only.
3. ✅ DONE **Configurable `MAX_EVENTS`:** `OGEventBoxRepository.MAX_EVENTS = 200` (~line 19) becomes a
   builder option `maxEvents(n)` (with a sensible cap, e.g. 1000). Track a `droppedCount` and show
   "N older events dropped" at the top of the list.
4. ✅ DONE **Relative time:** show `+1.2s` since the previous event next to the clock time (and switch
   to the delta when space is tight). ⬜ The "switch to the delta when space is tight" part is
   NOT DONE: both are always shown and the row wraps to 2 lines.
5. ✅ DONE **Value types:** show `String` / `Long` / `Double` / `Boolean` (e.g. a small `L`/`D`/`S` tag).
   Firebase treats `"5"` and `5L` differently, and `toString()` hides that.

---

## v1.4.0: Verification and polish ✅ DONE

1. 🗑 REMOVED after 1.0.5 (was ✅ DONE) **Expected-event checklist**
   ```kotlin
   OGEventBoxLog.expect("login_start", "login_success", "purchase")
   ```
   Show a checklist tab or strip: fired ✓ / missing ○, with a count of times fired. Optionally
   allow an expected order. This turns Event Box from a viewer into a verification tool.
2. ✅ DONE **Source tags:** `log(name, params, source = "firebase")`, so you can tell events from
   Firebase, AppsFlyer, Mixpanel and others apart and filter by source with a chip. (After 1.0.5: by typing the source name in search; the chips are 🗑 REMOVED.) Add
   `source: String? = null` to the event model.
3. ✅ DONE (used when the log is over 100k characters) **Session export to a file:** save the session JSON in the app cache dir and share it with a
   `FileProvider`, for long sessions that don't fit in an intent text extra.

---

## Compatibility checklist (applies to every version)
- ✅ DONE New fields on public data classes always get default values, and the constructor keeps `@JvmOverloads`.
- ✅ DONE New public API gets a Java interop test.
- ✅ DONE Consumer R8 rules (`consumer-rules.pro`) are updated for any new public classes.
- ✅ DONE Nothing new throws into the host app: wrap work in `runCatching` like the existing `log()` paths.
- ✅ DONE When the kill switch is off, nothing new allocates or records.
- Update the README feature list and the installation version when each version is released.
- ✅ Feature list DONE. ✅ Install version bumped to 1.0.6.
