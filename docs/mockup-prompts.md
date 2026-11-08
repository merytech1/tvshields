# Mockup prompts (Claude Design)

Prompts for roadmap 2.0. Paste **§1 once** at the start of a Claude Design project, then one
screen prompt (§2–§8) at a time. Every value comes from [design.md](design.md); if the two ever
disagree, `design.md` wins.

Each screen is requested in **dark mode with the purple accent** first. Once approved, ask for the
other three combinations with the variation prompt in §9.

## 1. Shared context

```text
You are designing the UI of "tvshields", an open-source web browser for Android TV with built-in
ad and tracker blocking ("Shields"). It is used from a couch, 3 m from the screen, with a TV
remote only (arrows, OK, Back, Menu). No mouse, no touch.

Personality: sober and trustworthy, with character. Brand idea: sovereignty, "you decide what
reaches your screen". Calm, precise, flat. No gradients, glow, blur or drop shadows. No emoji.
No Brave branding: no lion, no orange.

Canvas: 1920x1080 px frame representing a 960x540 dp TV canvas (1 dp = 2 px). Keep all text and
interactive elements inside the overscan safe area: 96 px left/right, 54 px top/bottom.

Font: Roboto. Text styles (px = 2x sp):
- display 72/88 regular
- headline 56/72 regular
- title 44/56 medium
- body 36/52 regular
- label 36/48 medium
Nothing smaller than 36 px. Sentence case. Tabular figures for counters.

Spacing grid: 8 px base (4 dp). Scale in px: 8, 16, 24, 32, 48, 64, 96.
Radius: 16 px controls and rows, 24 px panels and dialogs, full for pills.
Minimum focus target 96x96 px. Depth is shown by surface color only, never shadows.

Colors, dark mode:
- background #0F1217, surface #171B22, surface-raised #20262F, border #323A46
- text-primary #E9EDF2, text-secondary #A7B0BC, text-tertiary #7E8895
- accent #C99BEB, on-accent #1E0B2B, accent-container #3A2150, on-accent-container #EBD3FA
- warning #E8B04A (on-warning #1F1400), danger #F2786D (on-danger #240604)

Colors, light mode:
- background #F4F6F9, surface #FFFFFF, surface-raised #E9EDF2, border #C9D0DA
- text-primary #10151C, text-secondary #46505D, text-tertiary #677180
- accent #7E36AE, on-accent #FFFFFF, accent-container #F1E4FA, on-accent-container #4E1A72
- warning #8A5A00 (on-warning #FFFFFF), danger #B3261E (on-danger #FFFFFF)

Alternative accent "emerald" (replaces the purple accent tokens only):
- dark: accent #3FCF94, on-accent #04170F, accent-container #143A2C, on-accent-container #A8F0CF
- light: accent #0A7A4F, on-accent #FFFFFF, accent-container #D3F2E3, on-accent-container #064D31

Accent rules: one accent only on screen. The accent marks the focused element, the primary
action, selection, toggles that are on, and the "Shields on" badge. At most one accent-filled
element per screen. Never use the accent for decoration or large backgrounds.

Focus (the most important state on a TV): exactly one focused element per screen. Focused =
6 px accent ring with a 4 px gap, background moves to surface-raised, scale 1.04. Selected but
not focused = accent-container background with a check icon.

Icons: Material Symbols Outlined, weight 400, 48 px in bars and rows. The shield icon is a generic
shield. Status never relies on color alone: always icon plus a word.

Copy: short, factual, calm. Second person. No exclamation marks, no fear ("Threats
neutralized!" is wrong; "12 blocked on this page" is right). Verbs on buttons.

Show each screen as a full 1920x1080 frame with the focus clearly visible. Use realistic
content, not lorem ipsum.
```

## 2. Browser

```text
Screen: browser, normal browsing.

Top bar inside the safe area, height 128 px, surface color, radius 16 px. Left to right:
back, reload and home icon buttons; the address bar (surface-raised field, body text) showing the
domain "elnoticiero.com" with a lock icon; the Shields badge (pill, accent fill, on-accent text,
shield-check icon and the count "12"); a settings icon button.

Below the bar, the web page fills the rest of the frame: a light news article (headline, image,
paragraphs) that is clearly website content, not tvshields UI.

Focus is on the Shields badge.

Also show a second frame of the same screen while the user scrolls: the top bar is hidden and
the page uses the whole frame; a small hint at the top says "Back to show the bar" in
text-secondary.
```

## 3. Address bar and keyboard

```text
Screen: the address bar is being edited.

The top bar stays visible. The address bar is focused and expanded: it shows the full text the
user typed, "tvshields github", with a text cursor. Below it, a suggestions list on surface with
two rows: "Search Brave Search for 'tvshields github'" (focused) and the URL
"github.com/merytech1/tvshields". No live web suggestions.

The lower half of the frame shows the Android TV system keyboard as a neutral placeholder block
(it is drawn by the system, not by us). The page behind is dimmed with background at 80 %
opacity.
```

## 4. Shields panel

```text
Screen: Shields panel open over the browser.

A side sheet slides in from the right, width 36 % of the frame, full height inside the safe area,
surface color, radius 24 px. The page stays visible on the left.

Panel content, top to bottom:
- Header: shield-check icon in accent, title "Shields on", site "elnoticiero.com" in
  text-secondary.
- A row "Shields on this site" with a toggle (on, accent) and the word "On". This row is focused.
- Text "12 blocked on this page".
- Three list rows with icon, label and a right-aligned count: "Ads" 8, "Trackers" 3,
  "Scripts replaced" 1.
- A secondary button at the bottom: "Shields settings".

Also show a second frame with Shields turned off for this site: header icon shield-off in warning
color, title "Shields off for this site", toggle off with the word "Off", counters hidden, and in
the top bar the badge becomes a warning outline pill with shield-off icon.
```

## 5. Settings

```text
Screen: Settings.

Full screen, background color. Headline "Settings" at the top left of the safe area. Two
columns: a left list of sections (surface rows): "Appearance" (selected, accent-container),
"Shields", "Filter lists", "Search engine", "About". The right column shows the selected section.

Appearance section content:
- "Mode": two options as large selectable cards, "Dark" (selected, with check) and "Light", each
  with a small preview of the UI in that mode.
- "Accent": two options as large selectable cards, "Purple" (selected) and "Emerald", each with a
  swatch.
- A note in text-secondary: "Changes apply right away."

Focus is on the "Light" card.
```

## 6. Search engine picker

```text
Screen: Settings, "Search engine" section selected in the left column.

Right column: title "Search engine", one-line description "Used when what you type is not a web
address." Then four list rows, each with the engine name and its domain in text-secondary:
- Brave Search, search.brave.com (selected: accent-container background and a check icon)
- DuckDuckGo, duckduckgo.com
- Startpage, startpage.com
- Google, google.com

Focus is on the DuckDuckGo row. Use plain text names only, no third-party logos.
```

## 7. First launch

```text
Screen: first launch, downloading filter lists.

Full screen, background color, content centered inside the safe area:
- The tvshields logo placeholder (generic purple shield mark) and the name "tvshields" in
  display style.
- Text: "Getting Shields ready" and below it, in text-secondary, "Downloading filter lists. This
  happens once and takes a few seconds."
- A linear progress bar (accent on surface-raised) at 60 %, with "3 of 5 lists" under it.

Second frame, no connection:
- Icon wifi-off in danger color, title "No connection".
- Text: "Shields have no lists yet. Connect to the internet, or continue without protection."
- Two buttons: "Try again" (primary, accent fill, focused) and "Continue without protection"
  (secondary, surface-raised).
```

## 8. Logo and launcher banner

```text
Design the tvshields logo.

Concept: an original, simple shield mark that expresses sovereignty (the viewer in control of
their own screen), not a wall or a weapon. Geometric, calm, readable at small sizes. No lion, no
animal, no orange, nothing resembling Brave's logo.

Color: purple #7E36AE on light backgrounds and #C99BEB on dark backgrounds; also a one-color
white version.

Deliver:
1. The mark alone, large.
2. The Android TV launcher banner, 320x180 dp (shown at 640x360 px): mark plus the word
   "tvshields" in lowercase Roboto Medium, on #0F1217.
3. The app icon in a square and in a circle, at 512 px and at 48 px to check legibility.
4. Three alternative directions side by side before refining one.
```

## 9. Variations

After a screen is approved in dark + purple:

```text
Show the same screen in the other three combinations, keeping layout, content and focus
identical: light + purple, dark + emerald, light + emerald. Only the color tokens change.
```

## 10. Bringing the mockups back

For each approved screen, export a PNG of each frame (dark + purple at least) and share them in
the session. They are added under `docs/mockups/` and referenced from [design.md](design.md),
which closes roadmap 2.0.
