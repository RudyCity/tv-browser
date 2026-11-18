# Rudy Design System v1 — Kotlin Compose Material 3

## Product intent

Browser UI is navigation chrome, not a social/dashboard app. Favor viewport,
fast access to the omnibox, quiet surfaces, simple icon states and readable
focus indications. Taste Design / Antislop checks: consistent visual rhythm,
no unexplained cards, no double toolbar/loading indicators, no oversized
decorative buttons, no fake tabs, and no styling injected into user websites.

## Tokens (dark baseline)

Source: `compose-ui/src/main/java/io/rudycity/tvbrowser/compose/RudyDesignSystem.kt`.

- Toolbar `#151C27`, omnibox `#283444`, foreground `#F0F4FA`.
- Muted text `#B3C0CF`, active accent `#9DC6FC`.
- Spacing `2 / 4 / 8 / 12 / 16 dp`; same spacing scale across controls.
- Minimum actionable icon touch target **48dp**; icon drawings 24dp.
- Omnibox 56dp high, toolbar 68dp high, navigation loading bar 2dp.
- Shapes: 6/10/14/22/28dp corner scale; omnibox uses 28dp.
- Typography: Material 3-based body/label/title with deliberate weights,
  line-height and contrast.

## Form-factor rules

- Phone: one row, Back + omnibox + overflow; never create a fake tab count.
- Tablet >=600dp: Forward and Reload become visible; Home from 760dp.
- TV: Java big-target, D-pad-first toolbar until focus UX on actual TV is
  runtime-tested; do not assume touch Compose controls work well by remote.
- Existing page/iframe/video remains owned by BrowserEngine/WebView.

## Evidence and next steps

- Kotlin unit tests enforce minimum touch size and nontransparent theme colors.
- API29 emulator smoke tests verify Activity/Compose connection, loading
  ownership and modern network policy.
- Emulator screenshot captured as workflow artifact for visual audit. Human
  screenshot review and 320/390/600/768dp device snapshots remain pending.
- M3 still requires font scaling and contrast verification, RTL, light mode,
  accessible focus/keyboard behavior, and unification of bookmarks, settings,
  download and consent dialogs in M4.

## Native home composition (0.7.1 refinement)

- Keep exactly one editable omnibox in the browser toolbar; the homepage must not duplicate search input.
- Use a concise brand heading and subdued helper text.
- Quick-access shortcuts are labeled Material 3 buttons, 48dp minimum height, medium corner radius and explicit accessibility descriptions.
- Do not add a fake tab count or fill unused viewport space with decorative widgets.
- Validate a real rendered screenshot from the Android emulator, not just layout source.
