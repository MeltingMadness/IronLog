# IronLog Concept 2 — "RASTER" · Swiss Rational Redesign

> **Type**: UI/UX audit + design proposal (Concept 2)
> **Status**: Proposal — ready for review
> **Scope**: `core:designsystem`, `feature:dashboard`, `feature:statistics`, `feature:workout`, `app` (presentation layer only; no domain/data changes)
> **Relation**: Successor/alternative to `EMBER_REDESIGN_PLAN.md` (Concept 1, implemented)

---

## 1. Executive Summary

Concept 1 ("Ember") gave IronLog a warm, energetic identity: amber primary, five accent
hues, glassmorphism surfaces, glow effects and a rounded Figtree type ramp. It is coherent
and polished, but it pays for that warmth with **visual noise**: up to four hues per screen,
translucent glass behind small text, 18–28 dp corner radii that read "consumer app" rather
than "precision training tool", and a dashboard that is a vertical stack with no hierarchical
structure.

**Concept 2 — "RASTER"** is the rational counter-proposal: a monochromatic warm-gray system
(paper/ink), **one electric accent** used exclusively as a signal, hairline-bordered tiles on
a strict asymmetric **4-column bento grid**, **circular micro-metrics** for glanceable
numbers, and a neutral grotesque type ramp (Inter) with mono numerals for every figure.
The goal: the dashboard answers "how am I doing?" in ≤ 3 seconds, at a glance, without
reading a single sentence.

This document contains: (2) the audit, (3) design principles, (4) color palette,
(5) typography specs, (6) grid system, (7) component designs, (8) screen layouts,
(9) data-density rules, (10) motion, (11) accessibility, (12) implementation mapping,
(13) verification plan, (14) open decisions.

---

## 2. UI/UX Audit — Current State ("Ember", as implemented)

### 2.1 Method & reviewed surface

Static audit of the implemented design system and all presentation screens
(`core:designsystem`, `feature:dashboard`, `feature:statistics`, `feature:workout`,
`feature/plans`, `feature/history`, `feature/exercises`, `app` navigation), checked
against the stated goal of EMBER_REDESIGN_PLAN.md ("Premium im Portfolio + täglicher
Gebrauch"). Reviewed: `Color.kt`, `DesignTokens.kt`, `Type.kt`, `Glassmorphism.kt`,
`IronLogSurfaceCard.kt`, `StatCard.kt`, `DashboardScreen.kt`, `WeeklyVolumeCard.kt`,
`MuscleHeatmapCard.kt`, `ExerciseStatsScreen.kt`, `BottomNavBar.kt`, plus exercise,
workout and plan screens. Findings are grounded in the current token values.

### 2.2 What is strong (keep)

| # | Strength | Evidence |
|---|----------|----------|
| S1 | Coherent warm identity | Ember palette (`#0C0806`…`#2E2018` surfaces, `#FF6B00` primary, cream `#FFF8F0` light) is consistent app-wide. |
| S2 | Real motion system | `IronLogMotion` 130/220/300 ms, staggered entrance, specular highlights → app feels alive but controlled. |
| S3 | Semantic color tokens | `EmberSemanticColors` (success/danger/warning + rose/sky/violet/teal) is a clean extension point. |
| S4 | Numerals are already tabular | `fontFeatureSettings = "tnum"` on every `Type.kt` style prevents jitter in timers/weights. |
| S5 | Chart infrastructure | Vico (`CartesianChartHost`) already used for volume trend and per-exercise metrics — reusable. |
| S6 | Component economy | `IronLogSurfaceCard` tones (ELEVATED/MUTED/ACCENT/COLORED) + `StatCard` variants cover most surfaces with one primitive. |
| S7 | Data features exist | Weekly volume per muscle (MEV/MAV/MRV), E1RM progression, records, heatmap — the *content* for a glanceable design is already there. |

### 2.3 Findings (ordered by severity)

**F1 — High: Multi-hue overload competes with data.**
`StatCardVariant` renders PRIMARY (teal), SECONDARY (violet), TERTIARY (sky) side by side;
the exercise-stats screen shows two rows of differently colored stat cards, plus amber
primary, plus success/warning/danger. Perceived as a "rainbow", the palette stops
communicating hierarchy: every color is equally important, so none is.
*Rule violated: color must encode status, not decoration.*

**F2 — High: Glass + glow reduce legibility on small type.**
`glassmorphism()` composes background alphas (0.56–0.82) with gradient borders and a
specular overlay; `IronLogSurfaceCard` then adds a second translucent tint
(`muted 0.66 alpha`). Body text (12 sp) and micro-labels (9–11 sp) sit on layered
transparency — a WCAG AA contrast regression risk in both themes, worst on light
`#FFF8F0` with amber highlights at 0.07–0.16 alpha. Crisp Swiss design is the opposite:
opaque surfaces, hairline borders, contrast by value difference.

**F3 — High: No glanceable hierarchy on the dashboard.**
Dashboard is a `LazyColumn` of full-width cards in a fixed order (stats row →
volume line chart → heatmap → deload/progression). Every card is the same width and
similar height ⇒ the eye cannot pick out "today's most important signal". Asymmetric
bento sizing exists precisely to encode priority spatially.

**F4 — Medium: Redundant weekly metrics.**
`WeeklyVolumeCard` (tonnage trend) and `MuscleHeatmapCard` (sets per muscle) show the
same week's work twice — as a line and as chips. A single bento region pairing one
sparkline with one weighted ring would communicate both with less vertical space.

**F5 — Medium: Corner radii contradict "precision".**
Radius ramp 6/10/14/18/24/28 dp + glass radius 20 dp reads friendly/playful. For a
training instrument, 8–16 dp max with 2 dp hairline borders reads "instrument".

**F6 — Medium: Typography is round and wide.**
Figtree Black/ExtraBold display (44 sp, −1.5...−0.5 tracking) is a rounded-humanist
voice. It is warm but not neutral; huge bold numerals on stat cards (`displaySmall`
26 sp) are the loudest element even when the number is only "3 reps". Swiss direction:
neutral grotesque + mono numerals, hierarchy by size/weight only in one family axis.

**F7 — Medium: Numbers lack "delta-at-a-glance".**
No component encodes *change* (▲/▼ vs. previous week) next to metrics; users must
remember last week's values. Deltas are the cheapest glanceable information.

**F8 — Low: Micro-labels under 12 sp everywhere.**
`labelSmall` 9 sp / `bodySmall` 11 sp are used for important hints; on glass surfaces
they degrade. Target: never below 11 sp for informational text; 10 sp uppercase tracked
labels only for non-critical captions.

**F9 — Low: Color-only status in new weekly-volume card.**
The MEV/MAV/MRV status currently uses color + text label (good), but the heatmap uses
color intensity only. Add mono numeric values + icon marks.

**F10 — Low: Loading skeletons / empty states are inconsistent** (some screens show
`CircularProgressIndicator`, some `DashboardSkeleton`). Standardize.

### 2.4 Scorecard

| Area | Score (1–5) | Comment |
|---|---|---|
| Brand coherence | 4 | Warm Ember world is consistent |
| Visual hierarchy | 2 | Same-width stack, rainbow accents |
| Legibility/contrast | 3 | Opaque would be better; glass regresses |
| Typographic craft | 3 | Figtree ok, over-rounded, no numeral voice |
| Data density & glanceability | 2 | Data exists; presentation is chatty |
| Motion polish | 4 | Best-in-class for the genre |
| Accessibility basis | 3 | Focus states/contrast need hard pass |

→ Concept 2 targets: hierarchy 5, glanceability 5, legibility 5, typographic craft 5,
motion 4 (kept), coherence 4 (new, colder identity).

---

## 3. Concept 2 — Design Principles

1. **Monochrome first, accent as signal.** Every screen renders in paper/ink; the single
   electric accent appears only where the user must act or where a number is *dominant*
   (current PR, active set, MEV shortfall). If a screen has more than one accent hue, it
   is wrong.
2. **The grid is the hierarchy.** Tile size = importance. Hero tile 2×2, supporting
   metrics 1×1, trends 2×1. Nothing floats; everything snaps.
3. **Numbers are the interface.** Every key metric is a large tabular or mono numeral
   with a 10 sp uppercase label and an optional delta line. No prose where a numeral
   works. The 3-second scan: "12 / 22 Sätze, +3, Streak 5, PR heute."
4. **One set of borders: the hairline.** 1 dp, onSurface at 6–10 % alpha. No glass,
   no glow, no gradients-as-decoration. Depth comes from value contrast and the grid,
   not from blur.
5. **Density with air.** Tiles are dense inside (numbers first) but the grid gutter
   (12 dp) and generous outer margin (20 dp) give the Swiss "engineered" calm.
6. **Motion informs.** Animations only for: number count-up (300 ms), ring fill
   (400 ms), tile focus. No infinite pulses; `reducedMotion` respected (already in
   `IronLogMotion`).

---

## 4. Color Palette ("Papier & Ink" + one Electric)

### 4.1 Philosophy

Two neutral ramps (warm-gray, bridging the current Ember warm neutrals) + one electric
accent. Status hues exist but are *reserved* (rare, purposeful). Target WCAG AA 4.5:1
for text, 3:1 for UI borders/indicators in both themes.

### 4.2 Dark theme — "Ink"

| Token | Hex | Use |
|---|---|---|
| `ink.background` | `#0E0E0C` | Page background (slightly warm-black) |
| `ink.surface` | `#161613` | Bento tiles |
| `ink.surfaceRaised` | `#1D1D19` | Elevated tile, dialogs |
| `ink.inset` | `#23231E` | Inputs, chart plot area |
| `ink.hairline` | `#FFFFFF @ 8 %` | 1 dp borders |
| `ink.text` | `#EDEDE7` | Primary text |
| `ink.textMuted` | `#A3A39B` | Secondary text |
| `ink.textFaint` | `#6E6E67` | Captions, disabled |
| **`electric`** | **`#00D9F2`** | Accent: primary action, dominant numbers, active chip. Luminance ≈ 0.55→ 4.6:1 vs `#0E0E0C` (AA for large/UI) |
| `electricHover` | `#4FE4F7` | Pressed/hover tint |
| `onElectric` | `#001A1F` | Text on electric (AA 12:1) |
| `success` | `#4ADE80` | Only for completed/live-positive states |
| `warning` | `#F5B400` | RPE 9+, warnings |
| `danger` | `#FF5C5C` | Delete, failure sets |
| `series2` | `#A3A39B` | Secondary data series (mono-gray, never hue) |

### 4.3 Light theme — "Papier"

| Token | Hex | Use |
|---|---|---|
| `paper.background` | `#F6F4EF` | Warm paper page background |
| `paper.surface` | `#FFFFFF` | Tiles |
| `paper.surfaceRaised` | `#FBFAF7` | Elevated |
| `paper.inset` | `#EFEDE7` | Inputs, plot area |
| `paper.hairline` | `#1A1A17 @ 10 %` | 1 dp borders |
| `paper.text` | `#1C1C19` | Primary text |
| `paper.textMuted` | `#5C5B55` | Secondary |
| `paper.textFaint` | `#8B8A82` | Captions |
| **`electric`** | **`#0055C7`** | Deep electric blue — AA 8:1 on paper (cyan is unusable on light) |
| `electricHover` | `#2269D8` | |
| `onElectric` | `#FFFFFF` | |
| `success` | `#15803D` | |
| `warning` | `#B45309` | |
| `danger` | `#B91C1C` | |
| `series2` | `#8B8A82` | |

### 4.4 Rules

- **One accent per screen.** The electric hue appears on: primary CTA, active state,
  current-value marker, dominant number. Everything else monochrome.
- Data series: series1 = ink, series2 = gray; the electric accent may annotate the
  "current" point only. 3+ series → use dashes + mono values instead of more hues.
- Charts never use multi-hue legends; the legend is the mono label row under the chart.
- Keep `ThemeScheme` (AMBER/DEEP_CYAN/NEON_RED) plumbed but re-map: scheme choice now
  shifts only the **electric accent** (cyan/amber/red), not the whole palette.

### 4.5 Token → code mapping

| Current (`Color.kt`/`DesignTokens.kt`) | New |
|---|---|
| `EmberSurfaceDark0..3`, `EmberSurfaceLight0..2` | `ink.*` / `paper.*` |
| `EmberPrimary`, `EmberPrimaryLight/Dark` | `electric` + `electricHover` |
| `EmberTeal/Violet/Sky/Rose` (+ `semantic.*`) | delete from the **surface** system; only `success/warning/danger/series2` remain in `EmberSemanticColors` (renamed `RasterSemanticColors`) |
| `EmberSuccess/Danger/Warning` | kept, re-tuned as above |
| `glassmorphism()` / `glow()` | deprecated; replaced by `hairlineCard()` modifier (border + opaque bg) |

---

## 5. Typography Spec ("Neutral grotesque + mono numerals")

### 5.1 Families

| Role | Family | Source |
|---|---|---|
| UI + display | **Inter** (variable, weights 400/500/600/700) | Google Fonts, bundled `R.font` (4 files) |
| Numerals, timestamps, tags | **IBM Plex Mono** (400/600) | Google Fonts, bundled (2 files) |
| Numeric feature settings (all styles) | `tnum` + `zero` (slashed/plain per style) | Compose `fontFeatureSettings` — already the pattern in `Type.kt` |

Rationale: Inter is the neutral grotesque default for instrument-like UIs; mono numerals
give timers/weights the technical "console" voice and make columns alignable. Figtree is
dropped (rounded terminals contradict the concept); replacing it is a 1-file change in
`Type.kt` + font resources.

### 5.2 Type scale (replaces `Type.kt`)

| Style | Font | Weight | Size / Line | Tracking | Use |
|---|---|---|---|---|---|
| `metricHero` | Inter | 600 | 56 / 60 sp | −0.02 em | Dominant glanceable: ring center, today's PR |
| `metricLg` | Plex Mono | 600 | 32 / 36 sp | 0 | Tile headline number |
| `metricMd` | Plex Mono | 500 | 20 / 24 sp | 0 | Secondary metric, deltas |
| `metricSm` | Plex Mono | 400 | 13 / 16 sp | 0 | Table figures, timestamps |
| `h1` | Inter | 600 | 28 / 34 sp | −0.01 em | Screen titles (was display/headline) |
| `h2` | Inter | 600 | 20 / 26 sp | −0.005 em | Tile titles |
| `h3` | Inter | 600 | 15 / 20 sp | 0 | Card section heads |
| `body` | Inter | 400 | 14 / 20 sp | 0 | Body copy |
| `bodySm` | Inter | 400 | 12 / 16 sp | 0 | Secondary copy (min for info) |
| `microLabel` | Inter | 600 | 10 / 14 sp | +0.08 em, UPPERCASE | Tile labels, nav labels, chips |
| `tabular` | Plex Mono | 400 | 12 / 16 sp | 0 | Any aligned numbers (charts, logs) |

Notes:
- `microLabel` is always uppercase + tracked — the Swiss "signage" voice. It replaces
  the current `labelSmall` 9 sp usage; caps compensate the size drop perceptually.
- Never render a number in a rounded/proportional face; deltas get `▲/▼` + mono value.
- Map to Material roles in one switch inside `Type.kt`; screens keep using
  `MaterialTheme.typography.*` (renamed mapping, e.g. `displaySmall → metricLg`).

---

## 6. Grid & Bento System

### 6.1 Base grid

| Param | Value |
|---|---|
| Columns (mobile) | 4 (fixed), width = (screen − 40 − 3×12) / 4 ≈ 68 dp @ 360 dp |
| Gutter | 12 dp |
| Outer margin | 20 dp |
| Tile radius | 12 dp (insets 8 dp, chips pill) |
| Hairline | 1 dp, `hairline` token |

### 6.2 Spans (asymmetric vocabulary)

| Span | Size | Content type |
|---|---|---|
| 4×2 | Full-width banner | Rare: promotion, deload warning |
| 2×2 | Hero | Primary CTA, dominant metric + ring |
| 1×1 | Standard metric tile | Ring gauge or numeral + delta |
| 2×1 | Wide metric | Sparkline, heatmap strip, trend |
| 1×2 | Tall tile | Stacked micro-list (next sets, feed) |
| `2×2 + 1×1 + 1×1` row | "Bento signature" | One hero + two satellites |

Compose: `LazyVerticalGrid(GridCells.Fixed(4))` with `GridItemSpan(2)` / `(3)`; tiles
as `span(count)` extension — no layout dependency changes.

---

## 7. Component Designs

### 7.1 Hairline Card (replaces `IronLogSurfaceCard` + glass)

```
┌─────────────────────────────── 1dp hairline ───┐
│  MICRO LABEL (10sp caps, muted)                │
│  12 000   (metricLg/hero)     ▲ +3   (delta)   │
│  ▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔ (hairline divider)│
│  sparkline / content                            │
└─────────────────────────────────────────────────┘
```
- Background: opaque `surface`; border: 1 dp `hairline` token; radius 12 dp.
- Hover/pressed: border → electric 40 % (no scale/glow).
- Only `ACCENT` variant survives: electric border at 50 % for the hero tile.
- Deprecate `ironLogSurfaceRoles` glass tints; keep the API shape so screens don't
  change call sites (tone semantics: ELEVATED→raised surface, MUTED→surface,
  ACCENT→electric hairline, COLORED→removed).

### 7.2 Circular Micro-Metric (ring gauge) — *new core component*

```
       ┌─────────┐
       │   12    │  Plex Mono 600, metricHero (56sp) at center
       │   /22   │  Plex Mono 400, 13sp muted
       │  SÄTZE  │  microLabel caps
       └─────────┘  arc: track = inset color, stroke 8dp
                    fill: electric, strokeCap butt, start at 12 o'clock,
                    gap 12° at bottom (Swiss gauge)
                    tick at MEV/MAV (2dp, ink) — reuse MuscleVolumeCalculator
                    progress = volume/MRV (already computed as `progress`)
```

Spec:
- Diameter: 96 dp (1×1 tile), 120 dp (2×2).
- Stroke 8 dp; arc start −210°, sweep = 240° × progress; gap 120° bottom.
- `animateFloatAsState` 400 ms ease-out; respects `reducedMotion`.
- Center: value (mono), denominator, microLabel. Delta tick below: `▲ +3 / Woche`.
- Use cases: weekly volume vs MRV (stats + dashboard), consistency %, streak,
  plan completion. Implement once in `core:designsystem` (`RingGauge.kt`) with
  `progress: Float`, `value/denominator: String`, `label: String`, `accent: Boolean`.

### 7.3 Metric Tile (replaces `StatCard` variants)

- No color variants. Value = `metricMd`/`metricLg` (mono), label = `microLabel` caps.
- Optional third line: delta `▲ 2,5 kg` (electric when positive-dominant, ink otherwise).
- 1×1 tile content order: label (top), number (hero), delta (bottom).
- `StatCardVariant` collapses to `Variant.DEFAULT` + `Variant.ACCENT` (electric border
  + electric number for the *single* dominant metric on screen).

### 7.4 Chips (filter / status)

| State | Spec |
|---|---|
| Default | hairline 1 dp, ink text, radius pill |
| Selected | tile: electric 12 % bg; text: electric; hairline: electric 40 % |
| Status chip | icon + mono value + caps label; colors only `success/warning/danger` |

Remove gradient/glass chips (e.g., heatmap chips → gray ramp: 3 intensity steps via
ink alpha 12/25/40 %, electric only above MRV).

### 7.5 Buttons

| Kind | Spec |
|---|---|
| Primary | Ink-filled (dark: `#EDEDE7` bg, ink text) — the "paper button" Swiss signature; electric reserved for the *one* dominant CTA on dashboard hero |
| Secondary | Hairline 1 dp, ink text |
| Tertiary | `microLabel` caps text-only |
| Heights | 48 / 36 / 28 dp (keep `ButtonSize`) |

### 7.6 Charts (Vico, restyled)

- Line: 1.5 dp stroke ink; last point = electric dot (10 dp ring, 4 dp fill);
  no area fill; no specular.
- Bars: 6 dp wide, ink; today's bar electric.
- Axis labels: `tabular` 11 sp faint; grid lines: hairline 1 dp, 8 % alpha.
- Sparkline (tile): 1.25 dp, no axes, delta label at right.

### 7.7 Navigation (bottom bar)

- Icons mono (Material defaults fine, rendered in ink), labels `microLabel` caps.
- Active item: icon + label in ink with a 4 dp electric square under-line
  (positional + color, not color-only); inactive: `textFaint`.
- No pill/glass background.

### 7.8 Progress bars (keep the stats weekly-volume bars)

Re-skin existing `WeeklyMuscleVolumeCard` bars: track `inset`, fill electric for
OPTIMAL, gray for LOW, danger for HIGH (color + MEV/MAV ticks + mono numbers, as today).

---

## 8. Screen Layouts

### 8.1 Dashboard — "Bento" (4-col, asymmetric)

```
┌────────────────────────────────────────────┐
│ d. 04.09  ·  KW 36          STRÄNGE ▸ ⚙    │   header: date mono, caps labels
├───────────────┬────────────┬───────────────┤
│ 2×2 HERO      │ 1×1 RING   │ 1×1 METRIC    │
│ HEUTE: BEIN   │ Wochen-    │ Streak        │
│ 16 SÄTZE      │ volumen    │ 5 ▸ ▲ 2       │
│ [TRAINING     │ 12/22 ⇄    │               │
│  STARTEN]     │ MRV        │               │
├───────────────┴──────┬─────┴───────────────┤
│ 2×1 METRIC           │ 1×1 METRIC          │
│ Letztes Workout      │ Bester 1RM          │
│ 82,5 kg ▲ +2,5       │ 107,5 kg (Brust)    │
├───────────┬──────────┴────────────────────┤
│ 1×1 RING  │ 2×1 TREND (sparkline)         │
│ Volumen   │ Tonnage 8 Wochen              │
│ je Muskel │ „“““““““““““““““““““““““““““ │
├───────────┴───────────────────────────────┤
│ 4×1 heatmap strip: BRUST▮▮▮ TRI▮ RUECK▮…   │
│ (ink alpha steps, electric past MRV)      │
└────────────────────────────────────────────┘
```

Reading order enforced by size: hero (act) → ring (weekly state) → streak/PR (identity)
→ trend (context) → heatmap (detail). All numbers ≤ 1 glance; zero prose.

- Replaces: the current equal-width stack (`StatCard` row + `WeeklyVolumeCard` +
  `MuscleHeatmapCard` as separate full-width cards). Data sources unchanged
  (`DashboardViewModel` state).
- Deload recommendation: 4×2 banner tile (hairline danger 40 %), only when
  `deload.recommended`.

### 8.2 Exercise Stats — metric + ring + progression

```
┌────────────────────────────────────────────┐
│ ← Bankdrücken          BRUST · LANGHANTEL  │
├──────────────────────┬─────────────────────┤
│ 1×1 RING             │ 1×1 METRIC          │
│ Woche: 12/22 Sätze   │ E1RM 107,5          │
│ (MEV tick, status)   │ ▲ +8,5 (14 %)       │
├──────────┬───────────┴─────────────────────┤
│ 1×1      │ 2×1: 1RM-Verlauf sparkline      │
│ Max      │ „““““““““““““““““““““““““““““ │
│ Gewicht  │ chip ▸ GEWICHT | 1RM | VOLUMEN  │
├──────────┴───────────┬─────────────────────┤
│ 2×1 Muskel-Wochen-   │ 1×1 METRIC          │
│ volumen (Progress-   │ Max Volumen 2.400kg │
│ balken + MEV/MAV)    │                     │
└──────────────────────┴─────────────────────┘
```
- Weekly-muscle card (from the volume task) becomes ring + one compact progress row
  list (bars, mono numbers) — same `MuscleVolume` data.
- Chart selection chips: mono chips, electric active.

### 8.3 Active Workout — numeral console

```
┌────────────────────────────────────────────┐
│ ← Kniebeuge        SATZ 3/5   RPE ▸        │
│                                            │
│   ▐▐▐▐░░░░░  (set progress: ink/inset)     │
│                                            │
│   82,5  kg       8  wdh    [Monospace 40sp]│
│   ▲ +2,5 ggü. letztem Satz                  │
│                                            │
│   [  −  ]  [ + ]-step        [✓ LOG]        │
│   Timer: 02:30 (mono, electric when <10s)  │
└────────────────────────────────────────────┘
```
- Keep all interaction logic; only the presentation of weight/reps/timer becomes the
  mono console. RPE via chips (mono numbers 6–10).

### 8.4 History & Plans (consistency pass)

- History rows: date (mono), session title (h3), volume + sets (mono, right-aligned —
  tabular alignment), chevron.
- Plan list: 1×1 tiles in a 2-col grid (was full-width rows): plan name, next session
  date, ring mini (completion).
- Editors (plan/meta-plan): keep form logic; re-skin inputs with `inset` backgrounds
  and hairline focus states (electric border 40 % when focused).

---

## 9. Glanceability & Data Density Rules

1. **3-second scan test**: every screen must answer its core question from its top-left
   quadrant + one ring. Dashboard: "train? how much this week?"; stats: "progress?"
   ; workout: "what's next?"
2. **Number hierarchy**: hero 56 sp → tile 32 sp → row 20 sp → caption 13 sp. If two
   numbers on one tile share a size, they are not hierarchical — fix.
3. **Delta always**: any metric that changes weekly shows `▲/▼` + mono value + "vs.
   Vorwoche" (or explicit fixed baseline). No delta = user must remember.
4. **Label economy**: one `microLabel` per number; drop "kg"/"Wdh" into the label
   ("GEWICHT (KG)") instead of repeating units.
5. **Zero decoration**: no illustration, no gradient, no glow. Visual interest comes
   from grid rhythm, hairlines and the one electric signal.
6. Units follow Swiss style: space before unit (`82,5 kg`), decimal comma for German
   locale (existing `Locale` handling in `WeightFormatting` stays).

---

## 10. Motion & Interaction

| Trigger | Motion | Duration |
|---|---|---|
| Number change (count-up) | Ease-out, mono numerals | 300 ms (`emphasisMillis`) |
| Ring fill | 400 ms arc-sweep ease-out | new token `ringMillis` |
| Tile focus/select | Border color 130 ms (`fastMillis`) | — |
| Screen transition | Keep staggered entrance, but opacity + 4 dp slide only | current |

Remove: `glow()` pulse, infiniteRepeatable attention animations (replace RPE-9 warning
with a one-shot flash of the danger chip). Haptics: short click on set-log (audit note:
add `LocalHapticFeedback` on ring completion — no API change needed).

---

## 11. Accessibility

- Contrast targets: text ≥ 4.5:1 (all `ink/paper text` pairs pass by construction —
  table in §4); UI borders ≥ 3:1; electric-on-ink 4.6:1 (AA for large + UI).
- Not color-only: status always = icon/text + color (`▲`, `✓`, `!` glyphs or mono
  values). Heatmap uses alpha steps + set counts.
- Touch targets ≥ 44 dp (keep `ButtonSize` 48/36; chips add 4 dp padding).
- Focus indication: 2 dp electric outline (keyboard/TV navigation).
- `reducedMotion`: skip count-up/ring animations, keep instant state (existing
  `IronLogMotion.reduced` wiring is reused).
- Type: modal/text scaling follows system font scale (Inter variable scales cleanly;
  avoid fixed `sp` overflow in ring center — cap hero at 56 sp, allow ellipsis).

---

## 12. Implementation Mapping & Phases

**Kept unchanged**: domain/data layers, all ViewModels (data in state is already
sufficient), navigation graph, Vico dependency, `IronLogMotion`, `ButtonSize`,
`IconSize`, German strings (labels only restyled, few new strings).

| Phase | Scope | Files |
|---|---|---|
| **P1 — Foundation** | Token swap: `Color.kt` ramps, `DesignTokens.kt` radius/`hairline`, `Type.kt` Inter/Plex Mono scale, `Theme.kt` schemes (accent-only scheme variance), deprecate `glassmorphism`/`glow`, add `RingGauge.kt` | `core:designsystem` theme package |
| **P2 — Components** | `HairlineCard` (re-skin `IronLogSurfaceCard`), metric tile (re-skin `StatCard`), chips, buttons, chart style | `core:designsystem` common package |
| **P3 — Dashboard bento** | `DashboardScreen` → `LazyVerticalGrid` bento; hero tile CTA; ring (weekly volume vs MRV); metric tiles with deltas; trend sparkline; heatmap strip | `feature:dashboard` |
| **P4 — Stats** | `ExerciseStatsScreen` + ring + compact muscle-volume rows; chip re-skin | `feature:statistics` |
| **P5 — Workout console** | numeral console, timer mono, RPE chips, bar | `feature:workout` |
| **P6 — Consistency** | history rows, plans grid, editors, settings, shelves/empty states | `feature/history`, `feature/plans`, `feature/settings`, `app` |

**Dependencies**: P1 → P2 → P3–P6 (parallel after P2). Same shape as
EMBER_REDESIGN_PLAN.md phasing; expected effort roughly equal per phase (components
simplify: fewer variants).

---

## 13. Verification Plan

- Unit: ring progress arithmetic (0/edge/clamp), delta formatting, contrast lookup
  table tests (`/core/common` pattern).
- Compose screenshots: one golden per screen (dark+light) comparing hairlines/type;
  use existing CI emulator runner to pixel-diff P3 landing.
- Manual acceptance checklist per screen: 3-second scan test, one-accent rule,
  mono-numeral consistency, AA pairs, `reducedMotion`.
- Quality gates unchanged: `test`, `lintDebug`, `assembleDebug`,
  `connectedDebugAndroidTest`.

---

## 14. Open Decisions (for coordinator/product)

1. **Accent choice**: Electric Cyan `#00D9F2` (technical) vs Volt `#D9FF3D` (sporty).
   Proposal: cyan default, theme scheme can offer volt as `DEEP_CYAN`-style variant.
2. **Type family**: Inter + IBM Plex Mono (proposal) vs keeping Figtree for display
   only (hybrid, cheaper). Proposal is full swap — audit favors full swap.
3. **Light theme**: keep warm paper `#F6F4EF` (proposal) vs pure neutral `#F5F5F3`.
4. **Scope of P5 workout console**: pure restyle (proposal) vs also re-layouting the
   input steppers (optional follow-up).
5. Keep or drop the dark-only default? Proposal: dark default stays; light is a
   first-class citizen with its own electric variant.

---

*Proposal prepared from a code-grounded audit (tokens, type scale, component
primitives). All values are implementation-ready Kotlin/Compose tokens; no domain or
data changes required.*