# Design system

The visual and interaction language of tvshields. Every mockup (roadmap 2.0) and every Compose
screen follows this document. The constraints in [architecture §12](architecture.md#12-ui-design)
and rules P6 (remote only) and P7 (no Brave branding) apply on top of it.

## 1. Philosophy

**Sober and trustworthy, with character.** tvshields is a privacy tool used from a couch. It
should feel like a well-made instrument: calm, precise, quiet when everything is fine, clear when
something needs attention.

**Sovereignty** is the idea behind the brand: *you decide what reaches your screen*. tvshields
is not a cold wall against threats; it gives the viewer control over their own space. Purple, once
reserved for those who ruled themselves, is the brand color that carries that idea. The same
idea applies to the interface: the viewer picks how it looks (§2).

Principles, in priority order:

1. **The web comes first.** The page is the content; our UI is a frame around it. Chrome stays
   compact and gets out of the way when the user is reading or watching.
2. **The remote is the only input.** Every element is reachable with the arrows, and the focused
   element is always obvious from 3 m away (P6). If something cannot be focused, it is not
   interactive.
3. **Calm by default, clear when it matters.** Shields working normally is a quiet state, not a
   celebration. Color and motion are reserved for focus, state changes and warnings.
4. **Honest about protection.** The UI never overstates what is blocked. Numbers are real counts,
   and limitations (e.g. ads inside a video stream, rules C7) are stated plainly.
5. **Light on the TV.** No decorative effects, blur or heavy animation (T7). System font, vector
   icons, flat surfaces.

## 2. Appearance

The viewer chooses two independent settings in **Settings → Appearance**. Both are stored and
applied immediately, and give four combinations with the same structure.

| Setting | Options | Default |
|---|---|---|
| **Mode** | Dark, Light | Dark: suits dim living rooms and avoids glare |
| **Accent** | Purple, Emerald | Purple: the brand color |

- Web pages are not recolored: appearance only affects tvshields' own UI.
- The logo and launcher banner always use purple (§12); the accent setting only changes the UI.

## 3. Color

Cool neutral grays carry the UI, and **one accent** colors everything that matters: the focused
element, the primary action, selection and the "Shields on" state. There is never a second
accent on screen: two saturated hues competing look noisy and break the sober tone. Being
protected *is* being in control, so one color means both.

Each accent provides the same token set (§3.2), so components never depend on which accent is
active. Amber and red stay reserved for warnings and errors (§3.3).

All text pairs meet WCAG AA (≥ 4.5:1). Ratios were computed with the WCAG 2.x formula.

### 3.1 Neutrals

| Token | Dark | Light | Use |
|---|---|---|---|
| `background` | `#0F1217` | `#F4F6F9` | Screen background behind panels |
| `surface` | `#171B22` | `#FFFFFF` | Bars, panels, list rows |
| `surface-raised` | `#20262F` | `#E9EDF2` | Inputs, focused rows, nested surfaces |
| `border` | `#323A46` | `#C9D0DA` | Dividers and outlines (decorative) |
| `text-primary` | `#E9EDF2` | `#10151C` | Main text |
| `text-secondary` | `#A7B0BC` | `#46505D` | Supporting text, URLs, descriptions |
| `text-tertiary` | `#7E8895` | `#677180` | Metadata and disabled labels, on `background` / `surface` only |

| Pair | Dark | Light |
|---|---|---|
| `text-primary` on `background` | 15.96 | 16.92 |
| `text-secondary` on `background` | 8.56 | 7.56 |
| `text-tertiary` on `background` | 5.22 | 4.56 |
| `text-primary` on `surface-raised` | 12.95 | 15.58 |

### 3.2 Accents

| Token | Use |
|---|---|
| `accent` | Focus ring, primary button, selection, toggles on, Shields-on badge |
| `on-accent` | Text and icons on `accent` |
| `accent-container` | Selected rows, subtle highlights |
| `on-accent-container` | Text on `accent-container` |

**Purple** (default): a warm, reddish purple. Stands for sovereignty and the viewer in control.

| Token | Dark | Light |
|---|---|---|
| `accent` | `#C99BEB` | `#7E36AE` |
| `on-accent` | `#1E0B2B` | `#FFFFFF` |
| `accent-container` | `#3A2150` | `#F1E4FA` |
| `on-accent-container` | `#EBD3FA` | `#4E1A72` |

**Emerald**: reads at once as "safe" and "protected".

| Token | Dark | Light |
|---|---|---|
| `accent` | `#3FCF94` | `#0A7A4F` |
| `on-accent` | `#04170F` | `#FFFFFF` |
| `accent-container` | `#143A2C` | `#D3F2E3` |
| `on-accent-container` | `#A8F0CF` | `#064D31` |

| Pair | Purple dark | Purple light | Emerald dark | Emerald light |
|---|---|---|---|---|
| `accent` on `background` | 8.37 | 6.41 | 9.44 | 4.96 |
| `accent` on `surface` | 7.71 | 6.94 | 8.69 | 5.37 |
| `on-accent` on `accent` | 8.24 | 6.94 | 9.31 | 5.37 |
| `on-accent-container` on `accent-container` | 10.06 | 10.04 | 9.59 | 8.32 |

Rules:
- **One accent-filled element per screen** (the primary action or the Shields badge), never more.
- The accent is never used for decoration, illustrations or large backgrounds.
- No orange or Brave-like hues anywhere (P7).

### 3.3 Status

| Token | Dark | Light | Use |
|---|---|---|---|
| `warning` | `#E8B04A` | `#8A5A00` | Shields off for a site, lists outdated, WebView too old (C4) |
| `on-warning` | `#1F1400` | `#FFFFFF` | Text on `warning` |
| `danger` | `#F2786D` | `#B3261E` | Errors: no lists and no connection (C1), failed load |
| `on-danger` | `#240604` | `#FFFFFF` | Text on `danger` |

"Shields on" uses `accent`, not a status color (§3).

Status is never conveyed by color alone: it always comes with an icon and a word
(e.g. shield-off icon + "Shields off").

### 3.4 Virtual cursor

The cursor (architecture §6) floats over arbitrary web pages, so it ignores the theme: a white
arrow with a 2 dp `#0F1217` outline, readable on both light and dark pages.

## 4. Typography

**Roboto, the Android system font.** It costs nothing in APK size (T1), is designed for screens,
and its neutral tone fits the philosophy. No custom fonts are bundled.

Canvas: the TV renders the UI at 1920×1080 px with 320 dpi, i.e. a **960×540 dp** canvas.
Minimum text size is **18 sp** (architecture §12); nothing is smaller.

| Style | Size / line height | Weight | Use |
|---|---|---|---|
| `display` | 36 / 44 sp | Regular | Empty states, first-launch screen |
| `headline` | 28 / 36 sp | Regular | Screen titles (Settings, About) |
| `title` | 22 / 28 sp | Medium | Panel titles, section headers |
| `body` | 18 / 26 sp | Regular | Default text, list rows, URL in the address bar |
| `label` | 18 / 24 sp | Medium | Buttons, toggles, badges |

- Numbers that change (the blocked counter) use **tabular figures** so they do not jump.
- Sentence case everywhere. No all-caps labels.
- Long URLs are truncated in the middle only when needed; the domain is always visible.

## 5. Layout and spacing

- **Base unit: 4 dp.** Spacing scale: 4, 8, 12, 16, 24, 32, 48.
- **Overscan safe area: 48 dp left and right, 27 dp top and bottom** (5 % of the canvas). No
  interactive element or text lives outside it. Backgrounds may bleed to the edge.
- **Minimum focus target: 48×48 dp.**
- **Corner radius:** 8 dp for controls and rows, 12 dp for panels and dialogs, full for pills
  (Shields badge).
- **Elevation without shadows.** Depth is shown by surface color (`background` < `surface` <
  `surface-raised`), not by shadows or blur (T7).

### Screen structure

```
┌──────────────────────────────────────────────────────────────┐
│  (overscan 27 dp)                                            │
│  ┌ top bar ───────────────────────────────────────────────┐  │
│  │ ◀ ⟳ ⌂ │ address bar ........................ │ 🛡 12 │ ⚙ │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│                      web page (WebView)                      │
│                                                              │
│  (overscan 48 dp left/right)                                 │
└──────────────────────────────────────────────────────────────┘
```

The top bar hides while the user scrolls or plays video, and returns with Back or the Menu
key. Panels (Shields, Settings) open as a side sheet from the right, leaving the page visible.

## 6. Focus

Focus is the most important visual state on a TV (P6).

| State | Treatment |
|---|---|
| Resting | Element on its normal surface |
| **Focused** | 3 dp `accent` ring with 2 dp gap, background moves to `surface-raised`, scale 1.04 |
| Pressed (OK held) | Scale back to 1.00 |
| Selected (e.g. current search engine) | `accent-container` background + check icon, also when not focused |
| Disabled | `text-tertiary`, not focusable |

- Exactly one focused element on screen, always.
- Focus never lands on something invisible or behind a panel.
- Opening a panel moves focus into it; closing it returns focus to the element that opened it.
- Focus order follows reading order: left to right, top to bottom.

Ring contrast (≥ 3:1 needed for non-text UI): purple 6.79 on `surface-raised` (dark) and 6.94
on `surface` (light); emerald 7.66 and 5.37.

## 7. Motion

Motion explains changes; it is never decoration.

| Event | Duration | Easing |
|---|---|---|
| Focus change (ring, scale) | 150 ms | ease-out |
| Panel open / close | 220 ms | ease-out / ease-in |
| Top bar show / hide | 200 ms | ease-out |
| Counter update | none (number changes in place) | — |

- No looping or idle animations.
- When Android's "Remove animations" setting is on, durations drop to 0.

## 8. Iconography

- **Material Symbols, outlined, weight 400** (Apache-2.0), as vector drawables. Only the icons
  in use are included (T1).
- Sizes: 24 dp in bars and rows, 32 dp in empty states.
- Icons sit next to a text label wherever space allows; icon-only buttons (back, reload, home)
  get a content description for accessibility.
- The shield icon is a generic shield. No lion or any Brave mark (P7).

## 9. Components

The building blocks the mockups and Compose code are made from.

| Component | Notes |
|---|---|
| **Top bar** | Back, reload, home, address bar, Shields badge, settings. Height 64 dp |
| **Address bar** | `surface-raised` field, `body` text; shows the domain when idle and the full URL when focused. OK opens the TV keyboard |
| **Shields badge** | Pill with shield icon + blocked count. On: `accent` / `on-accent`. Off for this site: `warning` outline + shield-off icon |
| **Shields panel** | Side sheet: site name, on/off toggle (C5), counters by type (ads, trackers, scripts), "Report a broken site" later |
| **List row** | Icon, title, optional secondary line, optional trailing value or toggle. Min height 64 dp |
| **Toggle** | Track + thumb; on = `accent`. Always paired with a text state ("On" / "Off") |
| **Button** | Primary: `accent` fill (one per screen). Secondary: `surface-raised` fill. Min 48 dp high |
| **Dialog** | Centered, max 560 dp wide, 12 dp radius, title + body + up to 2 buttons |
| **Toast** | Bottom center, `surface-raised`, auto-hides after 4 s, never steals focus |
| **Progress** | Linear, `accent` on `surface-raised`; used for the first list download (C1) |
| **Virtual cursor** | See §3.4 |

## 10. Voice and copy

Short, factual, calm. The UI speaks as the product, in the second person.

- **Plain words:** "Shields on", "12 blocked on this page", not "Threats neutralized!".
- **No exclamation marks, no fear.** Protection is stated, not dramatized.
- **Verbs on buttons:** "Update lists", "Turn off for this site".
- **Errors say what happened and what to do:** "No connection. Shields have no lists yet. Connect
  to the internet or continue without protection."
- Sentence case, no ampersands, no "please", no "simply" or "just".
- UI strings live in Android string resources from day one, so the Spanish translation (and
  others) can be added without code changes.

## 11. Accessibility

- All text ≥ 18 sp and ≥ 4.5:1 contrast; focus ring ≥ 3:1.
- Never color alone for status (§3.3).
- Every focusable element has a content description for TalkBack.
- Respects Android's font scale and "Remove animations" settings.

## 12. Brand

- **Name:** tvshields, always lowercase in running text and in the launcher label.
- **Logo:** to be designed with the mockups (roadmap 2.0): a simple, original shield mark in
  purple that reads at launcher-banner size (320×180 dp). It stays purple whatever accent the
  viewer picks. No Brave lion, colors or shapes (P7).
- **Brand color:** purple (`#7E36AE`, and `#C99BEB` on dark backgrounds).

## 13. Implementation notes (Compose for TV)

- Tokens map to a `TvShieldsTheme` (colors, typography, shapes). The `ColorScheme` is built from
  the neutrals of the chosen mode plus the chosen accent (2 × 2 combinations).
  Components read tokens, never raw hex values.
- Focus styling uses Compose for TV's focus indication (`Surface` / `ClickableSurfaceDefaults`),
  configured once in the theme.
- The mockups (roadmap 2.0) use these exact token values; shadcn/ui is only the mockup component
  base and never ships in the APK (architecture §12). The prompts used in Claude Design are in
  [mockup-prompts.md](mockup-prompts.md).
