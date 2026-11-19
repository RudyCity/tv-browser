# Rudy Design System v2: Cross-Surface Browser Chrome & Workspace

## Product intent

Browser UI is navigation chrome, not a social or dashboard app. Favor viewport
area, immediate access to the omnibox, calm cool-slate surfaces, high-contrast
keyboard/D-pad focus indicators, and honest empty/loading/error states.
No unexplained cards, no duplicate loading bars, no fake tab counters, no emoji
ornaments, and no styling injected into user websites.

Canonical specification and WCAG AA contrast proofs live in
[DESIGN.md](../DESIGN.md).

## Tokens (Dark & Light Slate)

Source: `compose-ui/src/main/java/io/rudycity/tvbrowser/compose/RudyDesignSystem.kt`
and `app/src/main/java/io/rudycity/tvbrowser/RudyDialogUi.java`.

- **Dark baseline**: root `#0C131E`, toolbar `#131B28`, elevated surface `#1B2536`,
  omnibox `#222E42`, foreground `#F1F5FB`, muted `#B5C3D4`, accent `#9DC6FC`,
  interactive border `#6E89AA`, positive `#7AD894`, danger `#F48B82`.
- **Light theme**: root `#F2F5FA`, toolbar `#FFFFFF`, elevated surface `#F8FAFD`,
  omnibox `#E7EDF6`, foreground `#142030`, muted `#445468`, accent `#1E54A8`,
  interactive border `#708297`, positive `#1A6B35`, danger `#B3261E`.
- **Spacing**: `2 / 4 / 8 / 12 / 16 / 24 / 32 dp`.
- **Targets & sizing**: Minimum actionable touch/D-pad target **48dp**;
  icon drawings 24dp; omnibox 52dp high inside 68dp toolbar; progress line 2dp.
- **Shapes**: `6 / 10 / 14 / 18 / 28 dp` scale (omnibox `28dp` pill, modals `18dp`,
  cards/tiles `14dp`, buttons `10dp`, status tags `6dp`).
- **Typography**: Material 3 scale with tight display tracking, readable `65ch`
  line measure on home guidance, and monospace reserved strictly for URLs,
  tab metrics, and security destination hosts.

## Form-factor rules

- **Phone (`< 600dp`)**: single toolbar row (Back + omnibox + overflow); never
  display a fake tab counter.
- **Tablet (`>= 600dp`)**: Forward, Reload, and Bookmark toggle appear inline;
  Home and Extensions join from `760dp`.
- **Android TV**: large 48–52dp D-pad-first controls with 2.5dp `#9DC6FC` focus
  halos, high-contrast dual-ring cursor reticle (`TvCursorOverlay`), and dark
  slate dialog styling (`RudyDialogUi`).
- Existing page, iframe, and video surfaces remain owned by `BrowserEngine`.

## Native home & dialog composition

- Keep exactly one editable omnibox in the browser toolbar; the homepage may
  provide a button that focuses the toolbar omnibox, never a second text input.
- Quick-access tiles use explicit accessibility labels (`Open <Site> website`),
  monogram marks, host captions, 48dp+ height, and visible focus rings.
- Rich Compose dialogs cover overflow actions, saved Bookmarks/History (with
  explicit empty states), Tab Manager (active/private badges, crash/loading
  states, safe close-all flow), and security confirmations (popup/download/data
  clear with source/destination details).
