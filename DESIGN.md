# Rudy Browser Design System (v2)

## Design Read & Dials

> Reading this as: Multi-surface native Android browser chrome and home workspace (phone, tablet, and 10-foot Android TV) for everyday touch and remote-control users, in a disciplined slate-instrument utility language extending Rudy Design System v1, dial `ENERGY 2 / RHYTHM 2 / MOTION 2`.

- **ENERGY 2 (Balanced)**: Confident typographic hierarchy, crisp 1dp hairline framing, and clear primary focus without decorative gradients or neon glow.
- **RHYTHM 2 (Consistent with purposeful breaks)**: Compact single-row browser chrome gives maximum viewport to webpages; the home workspace, tab manager, and consent modals step into structured spatial compositions with clear focal anchors.
- **MOTION 2 (Purposeful transitions)**: Smooth progress interpolation, subtle focus-border transitions, tactile `0.98f` press feedback, and a single calm home entrance transition. Zero endless pulsing or floating loops.

## Palette Tokens (WCAG AA Verified)

Consistent cool-slate neutral ramp in both modes with one deliberate accent (`Rudy Sky` / `Rudy Cobalt`) and strictly semantic status colors (`positive` for verified HTTPS/active state, `danger` for destructive actions/security warnings).

### Dark Slate Theme (Default)

| Token | Hex | Role | Verified Contrast |
|---|---|---|---|
| `background` | `#0C131E` | Root viewport & home canvas | `17.02:1` with `foreground` |
| `surface` | `#131B28` | Toolbar & navigation chrome | `15.80:1` with `foreground` |
| `surfaceElevated` | `#1B2536` | Dialogs, menus, tab cards | `14.07:1` with `foreground` |
| `surfaceContainer` | `#222E42` | Omnibox resting & tiles | `12.48:1` with `foreground` |
| `surfaceContainerFocused` | `#293850` | Focused omnibox & active tile | `> 11.0:1` with `foreground` |
| `foreground` | `#F1F5FB` | Primary text & icons | `15.80:1` on `surface` |
| `muted` | `#B5C3D4` | Secondary labels & hostnames | `9.65:1` on `surface` |
| `accent` | `#9DC6FC` | Focus halo, active tab, primary CTA | `9.81:1` on `surface` |
| `onAccent` | `#071D38` | Text/icon on filled `accent` | `9.60:1` on `accent` |
| `outline` | `#6E89AA` | Interactive boundaries & inputs | `4.27:1` on `surfaceElevated` |
| `outlineSubtle` | `#394B63` | Structural hairline dividers | Hairline separator |
| `positive` | `#7AD894` | Verified HTTPS lock & enabled state | `7.86:1` on `surfaceContainer` |
| `danger` | `#F48B82` | Destructive action & APK/HTTP warning | `6.49:1` on `surfaceElevated` |

### Light Slate Theme

| Token | Hex | Role | Verified Contrast |
|---|---|---|---|
| `background` | `#F2F5FA` | Root home canvas | `15.02:1` with `foreground` |
| `surface` | `#FFFFFF` | Toolbar & navigation chrome | `16.42:1` with `foreground` |
| `surfaceElevated` | `#F8FAFD` | Dialogs, menus, tab cards | `> 15.5:1` with `foreground` |
| `surfaceContainer` | `#E7EDF6` | Omnibox resting & tiles | `13.95:1` with `foreground` |
| `surfaceContainerFocused` | `#DCE5F2` | Focused omnibox & active tile | `> 12.5:1` with `foreground` |
| `foreground` | `#142030` | Primary text & icons | `16.42:1` on `surface` |
| `muted` | `#445468` | Secondary labels & hostnames | `7.74:1` on `surface` |
| `accent` | `#1E54A8` | Focus halo, active tab, primary CTA | `7.27:1` on `surface` |
| `onAccent` | `#FFFFFF` | Text/icon on filled `accent` | `7.27:1` on `accent` |
| `outline` | `#708297` | Interactive boundaries & inputs | `3.94:1` on `surface` |
| `outlineSubtle` | `#CFD8E4` | Structural hairline dividers | Hairline separator |
| `positive` | `#1A6B35` | Verified HTTPS lock & enabled state | `5.58:1` on `surfaceContainer` |
| `danger` | `#B3261E` | Destructive action & APK/HTTP warning | `6.54:1` on `surface` |

## Shape Consistency Lock

- **Omnibox (`28.dp`)**: Full-pill anchor distinguishing the browser's single editable address bar from all rectangular controls.
- **Modals & Sheets (`18.dp`)**: Dialogs, overflow menus, and tab/consent overlays.
- **Tiles & Cards (`14.dp`)**: Quick access tiles, tab cards, and security detail blocks.
- **Buttons & Input Controls (`10.dp`)**: Action buttons, find bar inputs, and TV remote buttons.
- **Status Badges (`6.dp`)**: Compact `ACTIVE` / `PRIVATE` / `HTTPS` indicators.

## Five Levers & Key Decisions (R-31)

1. **One focal point per screen**: The omnibox anchors browsing chrome; the home workspace anchors on its header + Quick Access launch grid without duplicating an editable search field.
2. **Hierarchical contrast**: Surface elevation uses deliberate cool-slate value steps (`#0C131E` -> `#131B28` -> `#1B2536` -> `#222E42`) plus `1.dp` hairlines instead of heavy drop shadows or glass blur.
3. **Whitespace as structure**: `4 / 8 / 12 / 16 / 24 / 32 dp` scale separates chrome controls (`48.dp` minimum touch/D-pad targets) cleanly from webpage content.
4. **One deliberate accent**: `Rudy Sky` (`#9DC6FC`) / `Rudy Cobalt` (`#1E54A8`) appears exclusively on focus rings, active tab/progress indicators, and primary actions.
5. **Identity motif**: Precision 1dp hairline framing paired with high-contrast 2dp focus halos (for D-pad and keyboard `Tab`) and tabular monospace readouts for URLs, tab counts, and memory/match counters.

## Cross-Surface & Security Invariants

- **Tabs Management Architecture**:
  - **Toolbar Tab Counter Pill (`28.dp`)**: High-contrast, bordered badge displaying live open tab count (`state.tabCount`), accessible touch target (`48.dp`), and tactile press feedback.
  - **Tablet Tab Strip (`42.dp`)**: Dedicated horizontal scrolling desktop-class tab bar on screens `>= 600.dp`, featuring active elevation, private lock indicators, page titles, per-tab close buttons (`x`), and quick `+` new tab creation.
  - **Responsive Tab Manager**: Live search filtering by title/domain, responsive 1-to-2 column card grid, live status badges (`ACTIVE`, `PRIVATE`, `CRASHED`, progress), duplicate tab action, private Gecko launch, and safe "Close all" dialog with affirmative confirmation.
  - **TV & Classic Parity**: Native `tabsButton` in classic toolbar and Android TV actions bar, updating tab count dynamically with >= 48dp D-pad target.
- **History & Bookmarks Management Architecture**:
  - **Live Search Filtering**: Instant client-side search filtering across page titles, full URLs, and domain hosts with clear action.
  - **Action Ergonomics**: Cards support direct opening on card tap, instant background loading via "+" (Open in new tab), and individual deletion with "x".
  - **Manual Bookmark Creation**: In-dialog expandable bookmark creation form with URL format validation and clean slate inputs.
  - **Affirmative Destructive Action**: "Clear history" executes with a secondary red accent button requiring affirmative confirmation before clearing data.
- **Settings Architecture**:
  - **Logical Categorization**: Grouped into Appearance, Engine & Performance, Privacy & Data, and Extensions & About.
  - **Stateful Segmented Toggles**: Instant reactive switches for Dark/Light mode, Mobile/Desktop site requests, and TV remote pointer mode.
  - **Parity Across Flavors**: Modern Jetpack Compose presentation reflectively delegating to native controller actions; legacy Android dialog fallback on API 19.
- **Zero website styling injection**: External web pages keep their native DOM and colors.
- **Single progress indicator**: Compose renders its `2.dp` top-edge progress bar on modern touch devices; Java `ProgressBar` remains `View.GONE` when Compose is active.
- **Safe defaults on consent & destructive dialogs**: Dismiss, Back, and outside taps never execute destructive or consent callbacks; TV focus starts on the safe negative button (`Block` / `Keep data` / `Cancel`).
- **API 19 Legacy & 10-foot Android TV parity**: Legacy Java Views, HTML Home fallback, and TV remote toolbar share the exact v2 slate palette, >= 48dp focusable targets, and zero emoji indicators.

## 8. Minimalist Header & Bespoke Vector Iconography (v2.2)

- **Compact Minimalist Geometry**:
  - `toolbarHeight = 58.dp`: Slimmer, elegant horizontal layout that preserves vertical browsing viewport.
  - `omniboxHeight = 48.dp`: Pill-shaped (`RoundedCornerShape(24.dp)`) address bar with subtle surface fill (`palette.omnibox`) and 1.dp hairline border (`palette.outlineSubtle`).
  - `TabCounterButton`: Refined 26x26dp squircle (`RoundedCornerShape(7.dp)`) with subtle translucent fill and clean centered semi-bold counter typography.
  - `BrowserOverflow`: Clean horizontal triple dots (`···`) aligned with modern browser chrome aesthetics (Arc, Safari iOS).
- **Bespoke Vector Line Iconography (`RudyIcons.kt`)**:
  - Custom Canvas-rendered geometric line icons replacing clunky stock Material 1 silhouettes.
  - Uniform 1.8dp - 2.0dp optical stroke weight with `StrokeCap.Round` and `StrokeJoin.Round`.
  - Pixel-perfect scalability, responsive `LocalContentColor` tinting, and zero external runtime dependencies.
  - Icons crafted: `Back` (sleek directional arrow), `Forward`, `Search` (geometric lens), `Lock` (clean padlock with shackle), `More` (horizontal dots), `Clear` (fine cross), `Reload` (sweep arc), `Star` (geometric 5-point star), `Home`, `Bookmark`, `History`, `Tabs`, `Settings` (clean sliders), `Extension`, `Plus`, `Info`.
