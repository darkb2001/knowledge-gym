---
version: alpha
name: Knowledge Gym — Study Commons
description: A warm, readable workspace for learning IT knowledge.
colors:
  canvas: "#f3eee4"
  surface: "#fffcf6"
  sand: "#e8dfcd"
  muted: "#ede7db"
  line: "#d5cdbf"
  control: "#7d877f"
  strong: "#273c4a"
  body: "#43525a"
  subtle: "#59635f"
  accent: "#345f73"
  accent-hover: "#284d60"
  accent-soft: "#e3edf0"
  sage: "#dce7d9"
  positive: "#386345"
  warning: "#99512e"
  danger: "#a33e35"
  on-accent: "#fffcf6"
typography:
  headline:
    fontFamily: "var(--font-geist), system-ui, sans-serif"
    fontSize: "30px"
    fontWeight: 600
    lineHeight: 1.15
    letterSpacing: "-0.035em"
  body:
    fontFamily: "var(--font-geist), system-ui, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.5
  reading:
    fontFamily: "var(--font-geist), system-ui, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.8
  label:
    fontFamily: "var(--font-geist), system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 500
  button:
    fontFamily: "var(--font-geist), system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 600
    lineHeight: "20px"
  nav:
    fontFamily: "var(--font-geist), system-ui, sans-serif"
    fontSize: "13px"
    fontWeight: 500
    lineHeight: 1.5
  mono:
    fontFamily: "var(--font-geist-mono), ui-monospace, monospace"
rounded:
  control: "8px"
  notice: "12px"
  panel: "16px"
  pill: "9999px"
spacing:
  micro: "4px"
  sm: "8px"
  md: "16px"
  panel: "20px"
  gutter: "24px"
  lg: "32px"
  xl: "40px"
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.on-accent}"
    typography: "{typography.button}"
    rounded: "{rounded.control}"
    padding: "10px 20px"
  button-primary-hover:
    backgroundColor: "{colors.accent-hover}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.strong}"
    rounded: "{rounded.control}"
    padding: "10px 16px"
    typography: "{typography.label}"
  field:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.strong}"
    rounded: "{rounded.control}"
    padding: "10px 14px"
  panel:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.panel}"
    padding: "20px"
  nav-active:
    backgroundColor: "{colors.sage}"
    textColor: "{colors.strong}"
    typography: "{typography.nav}"
    rounded: "{rounded.control}"
    padding: "10px 14px"
  topic-selected:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.on-accent}"
    rounded: "{rounded.pill}"
    padding: "10px 20px"
---

# Design System: Knowledge Gym

## Overview

**Creative North Star: "Study Commons"**

A shared study space: warm, clear and quietly encouraging. The interface feels approachable to beginners without infantilizing working developers. Typography, generous task grouping and restrained color do the work; it does not imitate physical paper or decorate a learning task with a marketing spectacle.

This is an extracted, code-led system, not an approved image comp. The owner confirmed bright beige, Vietnamese/English UI, learner and admin coverage and delegated the remaining choices. Source authority is `tailwind.config.ts`, `app/globals.css`, `app/layout.tsx`, `components/ui.tsx` and the current captures referenced by `docs/design/VERIFICATION.md`.

**Key Characteristics:**

- Warm light ground with cream reading surfaces.
- Deep-blue actions and sage selection.
- Local workhorse typography and genuine Phosphor SVG icons.
- Flat, comfortably spaced work areas with native web controls.

## Colors

The sidecar's eight-step OKLCH strips are generated preview ramps, not additional application CSS tokens.

### Primary

Deep blue **accent** identifies committed actions, links and focus. **accent-hover** deepens that action; **accent-soft** supports selected form options and informational callouts. Cream **on-accent** is the readable foreground on filled actions.

### Secondary

Pale **sage** identifies selection and progress context. Forest **positive** communicates success. Clay **warning** and brick **danger** describe actual caution/error states, not decoration.

### Neutral

Oat **canvas** is the page field; cream **surface** is the working/reading plane; pale **sand** carries quieter adjacent areas; **muted** gently groups secondary content. **strong**, **body** and **subtle** separate heading, reading and supporting text. **line** is a divider, while the stronger **control** is the actual input boundary.

**The Boundary Rule.** Use control for editable-field borders; line is intentionally too quiet to substitute for that boundary.

## Typography

Local Geist is both the heading and body face; Geist Mono is for code and technical material. Interface text uses sentence case and modest negative tracking on headings, not uppercase ornamental labels.

Task headlines are (30px) on mobile and (36px) from the small breakpoint. Their weight and spacing follow headline above. Reading text follows reading; ordinary interface body follows body. Supporting labels use label, while navigation deliberately uses the smaller nav role. The large auth-side message is a surface-specific (48px), medium-weight exception, not a required hero on every screen.

**The Workhorse Rule.** Preserve the same locally bundled interface family across learner and admin screens; do not add a decorative display font merely to make a new screen feel different.

## Layout

The application has a (232px) desktop rail from (1024px), with an independently scrollable rail on short screens. The main area is fluid, capped at (1320px); horizontal padding is (20px), then (32px), then (48px) at the wider breakpoint. On mobile the rail becomes a disclosure menu under a compact header. Do not substitute a fixed bottom dock that consumes the reading viewport.

Related controls share a flat group, using the observed spacing scale rather than equal-sized nested cards. A reading container caps at (78ch) including its padding, which narrows the actual text measure. Its padding is (20px), then (40px). Module selection keeps the next action near its source; responsive reflow may move related controls into the selected row rather than beneath a long list.

## Elevation & Depth

No structural box-shadow is used. Depth comes from canvas/sand/surface contrast, whitespace and quiet boundaries. Selection is a tonal change, not a floating card. Focus rings are interaction signals rather than elevation.

**The Flat-at-Rest Rule.** Keep working planes flat; do not introduce glass, gradients, heavy shadows or fake paper texture.

## Shapes

Controls and navigation have gently curved corners; notices and working panels use the larger radii in frontmatter. Topic filters are pills. Shared module rows form a continuous directory, with interior dividers rather than separately elevated cards. Native checkboxes/radios remain recognizable.

## Components

### Buttons

Filled deep-blue primary actions pair with cream text; secondary controls use cream, warm-dark text and a quiet border. Minimum interaction height is (44px); states use background-color changes. Disabled controls reduce opacity and retain native disabled semantics. Links/buttons use a visible (2px) focus outline with (4px) offset.

### Inputs / Fields

Cream fields use the stronger control boundary and (44px) minimum height. Focus changes the border to accent and adds a (2px), 15%-accent ring. Labels stay visible above the field; placeholders never replace labels. Error text remains actionable and preserves the user's input.

### Navigation

The active row is sage with strong text and a filled Phosphor icon; inactive rows use ordinary icons and quieter text. Icons are actual package SVGs with text labels, not Unicode glyphs. Main navigation exposes learning work; admin links appear only for the application's recognized admin role. Mobile navigation closes on route choice.

### Chips

Topic filters are native buttons: filled accent when selected, cream with a quiet border otherwise. Content tags are smaller metadata labels, not action substitutes. State is also communicated by text/native pressed semantics, not color alone.

### Cards / Containers

A cream panel uses a quiet border and panel radius. Padding grows from (20px) to (24px). Notices use smaller radius and restrained semantic tones. Avoid arbitrary cards-inside-cards; only meaningful task boundaries warrant a panel.

### Module directory

Selection is the signature: a sage row, a real module title/description/count and context-local practice links. On desktop the action panel can sit beside the directory; on mobile those same actions sit inside the selected row. This is a learning workflow component, not a site-wide mandatory composition.

### Motion

Color changes use the Tailwind standard (150ms) transition. Existing small entrance motion is (240ms), with cubic-bezier(0.16,1,0.3,1). Reduced motion collapses animation/transitions and disables smooth scrolling. No continuous pulse, scroll hijack or animated study statistics.

## Do's and Don'ts

- Do reuse semantic tokens and the existing shared controls.
- Do preserve clear labels, keyboard focus and native disabled states.
- Do keep actual learning content in its source language while localizing interface controls.
- Do test real desktop, small-laptop and narrow-mobile viewports.
- Don't invent progress, achievements, capabilities or translated course content.
- Don't turn the light-only identity into a dark-mode default.
- Don't use gradients, fake texture, glass or ornamental analytics to manufacture personality.
- Don't hide practice actions far from the selected module on a small screen.
