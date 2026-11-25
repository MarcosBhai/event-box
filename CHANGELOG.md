# Changelog

All notable changes to Event Box. Install snippets are in the [README](README.md).

## 1.0.7

### Added
- **Clock button in the header** shows or hides the `+1.2s` time since the previous event on every
  row. It is green while the times are shown, confirms each change with a short toast, and the
  choice is remembered on the device (default: shown). TalkBack reads it as "Show/Hide time since
  previous event".

### Changed
- **Header**: `[Logo] [Count] … [Clock] [Search] [Pause] [Share] [Pin] [Clear]`, mirrored for a
  right-side bubble. Header buttons are 35×44 dp and the logo is 35 dp, so the row fits on narrow
  phones.

### Removed
- **`EVENTS` title** in the header. It already hid itself on narrow phones; the logo and count
  identify the panel. The code that measured the header to hide it is gone too.

No public API changes; upgrading from 1.0.6 needs no code changes.

## 1.0.6

A simpler, safer panel. **This release removes two public APIs**; see
[Upgrading from 1.0.5](#upgrading-from-105).

### Added
- **Undo for Clear**: Clear shows a `Cleared N events · UNDO` pill for 4 seconds. Events logged
  in the meantime are kept, renumbered after the restored ones.
- **`is:` search keywords**: `is:warning`, `is:user_property`, `is:event` and `is:repeated`.
  They combine with other terms and with `!`, for example `is:warning !purchase`.
- **Double-tap the bottom handle** to reset the panel height.
- **Remembered panel settings**: the panel height and the opacity picked by long-pressing the logo
  are saved on the device, across restarts and debug-mode sessions.
- **One-time hints**: a tip explains the logo gestures the first time the panel opens, and the
  resize handle lights up on open until it has been used once.
- **Haptic feedback** on Clear, Undo, starting a resize, hitting its limit and resetting it.
- **TalkBack labels** for the logo's tap and long-press, the resize handle (reset as its action)
  and the undo pill.

### Changed
- **Header**: `[Logo] [Title] [Count] … [Search] [Pause] [Share] [Pin] [Clear]`. Pause and Share
  moved here from the search bar. Clear uses a trash icon.
- **Header buttons** have 40×48 dp touch targets (the visible button is still 32×32).
- **Search** opens with the keyboard ready, since the bar is now a text field only.
- **Pause** is independent of search: closing search keeps it paused; Clear resumes it.
- **Firebase limit counters** are reset only once the undo for Clear has expired.

### Removed
- **Header Resize and Minimize buttons.** Tap the logo to shrink the panel; drag the bottom
  handle to resize it.
- **Filter chips** (All / ⚠ Warnings / Events / User props / source chips). Use search instead
  (see below).
- **Expected-event checklist** and its strip in the panel.
- **Public API**: `OGEventBoxFilter`; `OGEventBoxUiState.filter` and `.sourceFilter`;
  `OGEventBoxLog.expect()`, `clearExpectations()`, `expectedEvents` and
  `missingExpectedEvents()`.

### Fixed
- The header no longer adds another layout listener each time it switches sides.
- `requestOverlayPermission()` returns straight away on Android 5 (API 21–22), where the
  permission is granted at install and the settings screen doesn't exist.
- R8 rules no longer keep a class that has been removed.

### Upgrading from 1.0.5

| If you used | Do this instead |
|---|---|
| The ⚠ Warnings chip | Type `is:warning` |
| The Events / User props chips | Type `is:event` / `is:user_property` |
| A source chip | Type the source name, e.g. `firebase` |
| `OGEventBoxFilter`, `uiState.filter`, `uiState.sourceFilter` | Remove them; filter `OGEventBoxLog.events` yourself if needed |
| `OGEventBoxLog.expect(...)` / `missingExpectedEvents()` | Check `OGEventBoxLog.events.value` for the names you expect |
| `clearExpectations()`, `expectedEvents` | Remove the call |

For example, a test that used `missingExpectedEvents()` can do:

```kotlin
val fired = OGEventBoxLog.events.value.map { it.name }.toSet()
val missing = listOf("login_start", "purchase").filterNot { it in fired }
```

## 1.0.5

Search, filter chips, a RecyclerView list, duplicate detection, pause, configurable log size,
source tags, the expected-event checklist, panel opacity, session export and accessibility
work. See [`docs/improve_md.md`](docs/improve_md.md) for the full plan.
