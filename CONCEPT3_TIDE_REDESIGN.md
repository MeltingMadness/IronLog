# Concept 3 — "TIDE": Recovery-First Redesign

> **Status**: Design-Concept / Proposal (nicht implementiert)
> **Erstellt**: 2026-09-04
> **Umfang**: UI/UX-Audit + vollständiges Design-Konzept (Palette, Typografie, Komponenten, Screens)
> **Abgrenzung**: Concept 1 = kalt-blaues Glassmorphism (Vorgänger), Concept 2 = "Ember" (warmes Amber, implementiert). Concept 3 = **dark emerald/teal, athletic recovery & readiness, wissenschaftliche Progressions-Analytik**.

---

## 1. Executive Summary

IronLog besitzt bereits eine solide wissenschaftliche Datengrundlage: RPE/RIR-Logging,
RPE-Autoregulation, einen Deload-Detector mit Fatigue-Score, wöchentliches
Muskelvolumen, E1RM-Trends und einen Progressions-Coach. **Diese Daten sind in der UI
aber unsichtbar** — der Nutzer sieht Sätze, nicht Belastung; er sieht PRs, nicht
Erholung. Concept 3 "TIDE" macht aus IronLog eine **Recovery-Plattform**: dunkle
Smaragd-/Teal-Ästhetik, in der Daten als Licht erscheinen, Readiness-Gauges die
Zahl des Tages liefern, Strain-Meter jede Session einordnen und wissenschaftliche
Charts Progression belegen.

**Kernidee**: „Train hard, recover smart." Das Design ist ruhig, dunkel und
präzise — ein Trainings-Tagebuch, kein Social Feed. Jede Komponente hat genau eine
Frage zu beantworten:
- *ReadinessGauge* → „Bin ich heute bereit?"
- *StrainMeter* → „Wie viel Belastung war diese Session?"
- *MuscleVolumeBars* → „Kommt meine Brust diese Woche zu kurz?"
- *ProgressionTrendCard* → „Werde ich tatsächlich stärker?"

---

## 2. UI/UX-Audit (Ist-Zustand)

Basiert auf dem Stand `a0b72ea` (smart coach, RPE autoregulation, deload detector) und
dem implementierten Ember-Design (Phase 1–6 des `EMBER_REDESIGN_PLAN.md`).

### 2.1 Was bereits existiert

| Bereich | Ist-Zustand | Datei/Grundlage |
|---|---|---|
| Theme | Ember (warmes Amber) als Default, dazu DeepCyan + NeonRed, Dynamic Color, Glassmorphism, Ambient Glow | `core/designsystem/.../theme/Color.kt`, `Theme.kt`, `Glassmorphism.kt` |
| Typografie | Figtree (Black/ExtraBold/Bold, `tnum`-Feature aktiviert) | `theme/Type.kt` |
| Tokens | Radius xs–xxl + pill, IconSize sm–xxl, ButtonSize, IronLogDimens, IronLogMotion | `theme/DesignTokens.kt` |
| Dashboard | Begrüßung, Streak-Card, CommandCenter (Workout-CTA), StatCards (PRIMARY=Teal, SECONDARY=Violet, TERTIARY=Sky), MuscleHeatmap, WeeklyVolumeChart (Vico) | `feature/dashboard/` |
| Workout | RPE/RIR-Input mit Placeholder-Hint, RPE-Farb-Badges (6–10), Autoregulations-Pills (Ziel-RPE, „Zu schwer", „Nächster Satz: X kg", „Backoff-Satz"), Rest-Timer (Countdown + Haptik), Superset-Violet-Tint, Previous-Session-Panel | `feature/workout/ActiveWorkoutScreen.kt` |
| Progression | Progressions-Coach (Review-Screen), E1RM-Statistik (Epley/Brzycki), 1RM-Entwicklung, PR-Badges | `feature/progression/`, `feature/statistics/` |
| Domänen-Logik (verfügbar, aber ohne UI) | Fatigue-Score 0–100 + Deload-Signale + Empfehlungsschwelle (60), wöchentliches Muskelvolumen + VolumeStatus, RPE-Autoregulation | `core/common/.../deload/DeloadDetector.kt`, `util/MuscleVolumeCalculator.kt`, `util/RpeAutoregulation.kt` |

### 2.2 Stärken (bewahren)

1. **Konsistentes Token-System** — Radien, Icon-Größen, Motion sind zentralisiert; ein Theme-Swap (wie Ember bewiesen hat) ist risikoarm.
2. **Daten-getriebene Workout-Führung** — Autoregulations-Pills und Ziel-RPE-Placeholder sind ein echtes Differenzierungsmerkmal; sie fühlen sich „coach-like" an.
3. **Glassmorphism + Glow** erzeugen einen Premium-Look ohne Illustrationen.
4. **RPE-Farbcodierung** (grün→amber→rose) ist bereits eine implizite Strain-Sprache.

### 2.3 Schwächen (Concept 3 adressiert)

| # | Problem | Beleg/Beobachtung | Lösung in Concept 3 |
|---|---|---|---|
| A1 | **Fatigue ist unsichtbar.** Der DeloadDetector berechnet einen Score 0–100, aber der Nutzer erfährt erst über die Progression-Review, dass eine Deload ansteht — nach dem Training, nicht vorher. | `DeloadDetector.recommendThreshold=60`; Deload-Review erscheint erst nach `finishWorkout` | ReadinessGauge auf dem Dashboard + Recovery-Screen; Deload-Banner **vor** der Session |
| A2 | **Kein Belastungs-Feedback während der Session.** Der Nutzer sieht RPE-Zahlen, aber keine akkumulierte Session-Strain. „Ich war anfangs frisch, jetzt bin ich bei RPE 10" — das Design sagt nichts dazu. | ActiveWorkoutScreen zeigt je Satz RPE, aber keine Session-Aggregation | Live-StrainMeter im Workout-Kopf + RPE-Preview auf Satzebene |
| A3 | **Volumen existiert als Heatmap, aber nicht als Ziel-System.** MuscleVolumeCalculator liefert bereits `VolumeStatus` (unter/über Ziel), die UI zeigt nur Intensität. | `MuscleVolumeCalculator.evaluateStatus()` ungenutzt in der UI | MuscleVolumeBars mit Ziel-Thresholds + Statusfarben |
| A4 | **Progressionsanalytik ist statistisch, nicht wissenschaftlich.** E1RM-Charts zeigen Linien ohne Trendaussage, Konfidenz, Vergleichsband oder „wie viel besser als vor 4 Wochen". | `ExerciseStatsScreen` (Punkte-Chart + Statcards) | ProgressionTrendCard mit Regressionslinie, Band, Delta-Badges, PR-Markern |
| A5 | **Warme Amber-Ästhetik kollidiert mit dem Recovery-Thema.** Ember kommuniziert Energie/Anstrengung; Erholung, Ruhe und „System-Zustand" lesen sich in kalt-Grün glaubwürdiger (Biometrie-Apps: Whoop/Oura/Garmin = dunkel + Grün/Teal). | Marktstandard für Readiness-UIs | Dark emerald/teal Palette als eigenes ThemeScheme („TIDE"), Amber bleibt als Warnfarbe |
| A6 | **Kleine Härtungsmängel** — Zahlen nicht durchgängig tabellarisch gesetzt (nur `tnum` teilweise), Gauge/Score-Vokabular fehlt komplett, Deload-Modus-Auswahl (`DeloadMode`-Prefs) hat keinen UI-Ursprung außerhalb der Review. | `Type.kt` nutzt `tnum` nur in einigen Styles | Typografie-Spec: Datenrollen mit durchgängigem `tnum` |

### 2.4 Opportunity-Mapping (Bestehende Logik → Neue UI)

| Vorhandene Domänen-Logik | Neue Concept-3-Oberfläche |
|---|---|
| `DeloadAssessment.fatigueScore` (0–100, ↑ = müde) | `ReadinessGauge` (Readiness = 100 − fatigue), Zonen 0–39/40–64/65–100 |
| `DeloadAssessment.signals` (E1RM_DROP 60, STAGNATION 25, RPE_CREEP 25, FAILURE_RATE 25) | `FatigueSignalCard` mit Signal-Chips und Score-Beiträgen |
| `DeloadAssessment.recommended` + `DeloadMode`-Prefs | `DeloadBanner` mit Modus-Auswahl (HALVE_SET_VOLUME / REDUCE_INTENSITY_BY_15_PERCENT) |
| `MuscleVolumeCalculator.aggregateByMuscleGroup` + `evaluateStatus` | `MuscleVolumeBars` mit Ziel-Statusfarben |
| `RpeAutoregulation` (Pills) | Beibehalten, in TIDE-Farben; erweitert um Strain-Preview |
| `WorkoutCalculations.calculate1RM` | `ProgressionTrendCard` (E1RM mit PR-Markern) |
| WorkoutSets (RPE × kg × reps je Satz) | **NEU**: `SessionLoadCalculator` (sRPE-gewichtete Tonnage → Strain) — siehe §8 |

---

## 3. Konzept-Identität & Design-Prinzipien

**Name**: TIDE (Arbeitstitel) — Ebbe und Flut von Belastung und Erholung.
**Story**: Ein dunkles, ruhiges „Mission Control" für den eigenen Körper. Smaragdgrün
steht für Erholung und Datenlebendigkeit; Wärme (Amber/Rose) erscheint nur noch als
Warnsystem. Die App sagt nie „mehr", sie sagt „richtig".

1. **Data is light** — Dunkle Flächen, leuchtende Daten. Je wichtiger eine Zahl, desto mehr Licht bekommt sie. CI zeigt Primärdaten, nie Deko.
2. **Eine Frage pro Komponente** — Jede Card beantwortet genau eine Trainingsfrage (siehe §1).
3. **Zonen statt Ziele** — Nutzer denken in Bereichen („moderat hart", „übertrieben"), nicht in exakten Schwellwerten. Alle Meter nutzen durchgängig 3-Zonen-Farbcodierung (emerald/amber/rose).
4. **Recovery zuerst** — Fatigue, Readiness und Deload-Empfehlungen sind First-Class-Citizens (Dashboard-Hero), nicht versteckte Statistiken.
5. **Wissenschaftlicher Ton** — Charts mit Achsenbeschriftung, Trendlinien, Deltas und „n=…"-Minimeta; Begriffe wie *Strain*, *Load*, *Trend* statt nur *Stats*.
6. **Ruhe in der Bewegung** — Motion ist gedämpft und nur Daten-Transport (Gauch-Sweep, Zahl-Count-up), kein Deko-Bounce. `IronLogMotion.reduced` respektieren.
7. **Kontinuität** — Ember-Token-Struktur (Radius/IconSize/ButtonSize/IronLogDimens), Figtree, Glassmorphism und die komplette Domänenlogik bleiben erhalten; der Wechsel ist ein Theme- und Komponenten-Schicht-Update wie bei Ember.

---

## 4. Farbpalette

### 4.1 Design-Intent

- Dunkle **Smaragd-Schwarz**-Flächen mit kühlem Grünstich (statt warmem Braunschwarz wie Ember).
- Primärfarbe **Emerald/Teal** — assoziiert Erholung, Biometrie, „System online".
- **Amber/Gold** bleibt als Warn- und Höchstzonenfarbe (RPE 9/10, Strain-Hochzone) — semantische Brücke zum Ember-Erbe.
- **Rose** nur für kritische Zustände (Deload-fällig, Readiness 0–39).
- **Strain-Rampe** (Emerald → Lime → Amber → Orange → Rose) für Volumen/Heatmap/Strain; sie ist die einzige „bunte" Verlaufsquelle.

### 4.2 Dark-Mode-Tokens (Primär-Ziel)

```kotlin
// === TIDE SURFACES (kühl-stumpfes Smaragd-Schwarz) ===
val TideSurface0 = Color(0xFF030A07)   // Page-Background (tiefstes Schwarz, Grünstich)
val TideSurface1 = Color(0xFF081410)   // Standard-Karten (Glass-Infill)
val TideSurface2 = Color(0xFF0E1F18)   // Erhöhte Surfaces, Bottom-Sheets
val TideSurface3 = Color(0xFF14291F)   // Overlays, Dialoge, Hover

// === TIDE PRIMARY — Emerald ===
val TidePrimary       = Color(0xFF34D399)   // CTA, aktive Chips, Gauge-High-Zone
val TidePrimaryBright  = Color(0xFF5EEAD4)  // Glow, Gradient-Spitzen, Nummern-Highlight
val TideOnPrimary      = Color(0xFF062A20)  // Text auf Primary (dunkel, hoher Kontrast)
val TidePrimaryDim     = Color(0xFF10A37F)  // gedrückter Zustand / Secondary-CTA

// === TIDE ACCENTS ===
val TideTeal     = Color(0xFF14B8A6)  // Sekundär: Zonen/Charts neutral
val TideLime     = Color(0xFFA3E635)  // Volumen-Mittelzone, „moderat steigend"
val TideSky      = Color(0xFF38BDF8)  // Info: Rest-Timer, Hinweise
val TideViolet   = Color(0xFFA78BFA)  // PR-/Rekord-Marker (Übergang von Ember)
val TideAmber    = Color(0xFFFBBF24)  // Warnzone, RPE 9, Strain-Hoch
val TideOrange   = Color(0xFFFB923C)  // Strain-Spitze (RPE 10)
val TideRose     = Color(0xFFFB7185)  // Kritisch: Deload, Readiness-Tief, Fehlversuch

// === TIDE TEXT ===
val TideOnSurface       = Color(0xFFE4F3ED)   // Primärtext (Mint-Weiß)
val TideOnSurfaceMuted  = Color(0xFF93B3A4)   // Sekundärtext
val TideOnSurfaceFaint  = Color(0xFF4F6B5E)   // Tertiärtext, Placeholder
val TideGlassBorder     = Color(0xFF34D399).copy(alpha = 0.10f) // Karten-Rand
val TideGlassGlow       = Color(0xFF5EEAD4).copy(alpha = 0.06f) // Ambient Glow

// === TIDE SEMANTIC (ersetzt Ember-Zuordnung) ===
// success  = TidePrimary         (grün – Erfolg, „bereit", geloggte Sätze)
// warning  = TideAmber           (gelb – RPE 9, mittlere Strain, gärender Readiness)
// danger   = TideRose            (rot-rosa – Delete, Deload-fällig, RPE 10-Spitze)
// rose     = TideRose            (Akzent-Erbe)
// sky      = TideSky             (Info: Timer, Hinweise)
// violet   = TideViolet          (PR-Marker, Supersets bleiben violet)
```

### 4.3 Light-Mode-Tokens (Sekundär, „Klinik-Hell")

```kotlin
val TideLightBg        = Color(0xFFF2F8F4)  // helles Grün-Weiß
val TideLightCard      = Color(0xFFFFFFFF)
val TideLightPrimary   = Color(0xFF0E8F72)  // dunkleres Emerald für AA auf Weiß
val TideOnPrimaryLight = Color(0xFFFFFFFF)
val TideLightText      = Color(0xFF0B211A)
val TideLightMuted     = Color(0xFF5B7468)
```

### 4.4 Kontrast- und Zonen-Spezifikation

| Paar | Ratio (Richtwert) | Zweck |
|---|---|---|
| `TideOnSurface` auf `TideSurface1` | ≈ 12:1 (AA/AAA) | Primärtext |
| `TideOnSurfaceMuted` auf `TideSurface1` | ≈ 6,5:1 (AA) | Sekundärtext |
| `TideOnPrimary` (#062A20) auf `TidePrimary` | ≈ 9:1 (AA) | Text auf Emerald-CTA |
| `TideAmber` auf `TideSurface1` | ≈ 8:1 (AA) | Warnzonen-Text (Badges immer mit Text, nie nur Farbe) |
| Zonen-Farben | nie als einzige Codierung | **immer** zusätzlich Label (Bereit/Moderat/Erschöpft) — A11y |

Readiness-Zonen (Score 0–100): **0–39 rose „Erschöpft"**, **40–64 amber „Moderat"**,
**65–100 emerald „Bereit"**. Deload-fällig (`assessment.recommended`) schaltet den
Gauge-Ring auf Rose-Impuls.

Strain-Zonen (Session-Load relativ zum 28-Tage-Schnitt, s. §8):
**< 0,8× = „leicht"**, **0,8–1,2× = „optimal"**, **> 1,2× = „hoch"**, **> 1,5× = „Spitze (Deload prüfen)"**.

### 4.5 Theme-Integration

- Neues `ThemeScheme.TIDE` (Default-Vorschlag) neben `AMBER`, `DEEP_CYAN`, `NEON_RED`; `Theme.kt` erhält `tideDarkColorScheme`/`tideLightColorScheme` analog `ember*ColorScheme`.
- `Glassmorphism.kt`: Tint `TidePrimaryBright @ 4%`, Border `TideGlassBorder`, Specular nur obere Kante (`0x5EEAD4 @ 8%`).
- `MainActivity` Ambient Glow → `TideGlassGlow`.
- StatCard-Mapping bleibt (PRIMARY=Teal/Emerald, SECONDARY=Violet, TERTIARY=Sky), Farben tauschen auf TIDE-Werte.

---

## 5. Typografie-Spezifikation

**Familie**: Figtree (bereits gebunden, Black/ExtraBold/Bold/Medium/Normal + Italic). Kein neuer Font-Download; **Datencharakter entsteht durch Rollen und `tnum`**.

### 5.1 Rollen-System (neu definiert)

| Rolle | Style-Basis | Größe/Zeilen-Höhe | Gewicht | Einsatz |
|---|---|---|---|---|
| `MetricHero` | `displayMedium` | 32/36, `tnum`, −1.0 tracking | Black | Readiness-Score, Session-Strain gesamt |
| `MetricLarge` | `displaySmall` | 26/32, `tnum`, −0.5 | ExtraBold | StatCards-Werte, Gauge-Nebenwerte |
| `MetricBody` | `titleLarge` | 20/26, `tnum` | Bold | Chart-Tooltips, Skalen-Werte |
| `MetricCaption` | `labelLarge` | 14/18, `tnum`, +0.0 | SemiBold | Achsen, Zonen-Labels, Deltas |
| `MetricMicro` | `labelSmall` | 11/14, `tnum`, +0.4, UPPERCASE | SemiBold | Einheiten, „n=…"-Minimeta, Tab-Header („STRAIN", „VOLUMEN") |
| `HeadlineScreen` | `headlineLarge` | 24/30, −0.25 | ExtraBold | Screen-Titel |
| `Body` / `BodySmall` | M3-Standard unverändert | — | — | Fließtext, Beschreibungen |

**Global**: Alle numerischen Werte (Gewichte, RPE, Scores, Datumsziffern) verwenden
durchgängig `fontFeatureSettings = "tnum"` (Tabellenziffern, kein Springen bei
Count-ups). `Type.kt` erhält dafür einen `DataTypography`-Block oder ein
`MetricTextStyle`-Helper, damit kein Component eigene Werte dupliziert.

### 5.2 Schreibweisen

- Dezimaltrenner: Punkt (Locale-ROOT, bisherige Konvention).
- Einheiten klein: `kg`, `t`, `Wdh`, `min` — immer als `MetricMicro` im Muted-Ton.
- Zonen-Labels immer ausgeschrieben: **Bereit / Moderat / Erschöpft**, nie nur Farben.
- Delta-Format: `+2,5 %` / `−1,8 %` mit `WeightFormatting.formatWeightDelta`-Muster.

---

## 6. Komponenten-Design (Spezifikationen)

Alle Komponenten bauen auf den bestehenden Tokens (Radius, IconSize, IronLogDimens,
IronLogMotion) auf und folgen dem Ember-Glassmorphism (TIDE-getönt).

### 6.1 `ReadinessGauge` (Kern-Komponente)

```
┌────────────────────────────────────────┐
│  BEREIT                                   │ ← MetricMicro, muted
│              ╭───╮                        │
│           ╭──╯ 72 ╰──╮                    │ ← MetricHero 32/36 Black tnum
│          ╭╯   BEREIT  ╰╮                  │ ← Label unter der Zahl
│         ╭╯            ╰╮                  │
│        ╭╯  172°-Bogen   ╰╮                │ ← Bänder: rose/amber/emerald
│  ╰╮   ╭╯                ╰╮   ╭╮            │
│  ▐░▒▒▒▒▒▒▒▒▌  spk 7 Tage   ▐▌ PR           │ ← Mini-Sparkline + Delta-Chip
└────────────────────────────────────────┘
```

- **Form**: 172°-Arc (Canvas), Track = `TideOnSurface @ 6%`, Bänder-Segmente mit Zone 65–100 emerald / 40–64 amber / 0–39 rose; Zeiger (Needle) schlank auf `TidePrimaryBright`.
- **Wert**: `readiness = 100 − fatigueScore` (aus `DeloadAssessment`); bei fehlenden Daten (Sessioncount < `minSessions`) Show-State: gestrichelter Bogen, Wert „—", Label „Genug Daten? 3 Einheiten nötig" (MetricMicro) — **kein Fake-0**.
- **Delta**: Chip oben rechts (`MetricMicro`): „▲ 8 seit gestern" / „▼ 12" — Trend aus letztem Assessment (4-Wochen-Fenster; bei 1 Tag: „seit letzter Woche").
- **Deload-Zustand**: `recommended == true` → Ring auf Rose + dezenter Puls (600 ms, nur wenn !reducedMotion), unter dem Gauge `DeloadBanner` (6.9).
- **Animation**: Sweep 700 ms `FastOutSlowInEasing` (Ember-Konvention), Zahl-Count-up 500 ms; bei `IronLogMotion.reduced` = 0 ms.
- **A11y**: `contentDescription = "Bereitschaft 72 von 100, Zone bereit"` + Semantik-Rolle ProgressBar; Zonen-Aufteilung zusätzlich als Textzeile.

### 6.2 `StrainMeter` (Live-Session + Wochenzyklus)

- **Session**: horizontale `ZoneBar` (6.5) im Workout-Kopf; Füllwert = akkumulierte Session-Load (s. §8), Zonen relativ zum 28-Tage-Schnitt; rechts `MetricLarge`-Wert in Tonnage (`4,2 t`).
- **Woche**: Balken-Chart (Vico, bereits Dependenz) mit 7 Balken; jeder Balken = Session-Strain, Zonen-Hintergrundbänder als Rechtecke hinter den Balken (leicht, Alpha 4%); aktuelle Session blinkt nicht, sondern bekommt Outline `TidePrimary`.
- **Farbe**: Balken = Zone des Werts (emerald/amber/rose), nicht Datenserie.

### 6.3 `MuscleVolumeBars`

- Gruppe von horizontalen Balken (je Muskelgruppe): Soll-Option Rendering = Ziel-Punkte aus `VolumeThresholds` als zartes „|"-Tick; Ist-Balken = gesetzte Volumen-Einheit; darunter `MetricCaption`: „Brust 14/20 Sätze" + Status-Chip („im Ziel" emerald, „unter Ziel" sky, „über Ziel" amber aus `VolumeStatus`).
- Ersetzt die reine Intensitäts-Farbrampe der Heatmap nicht, sondern ergänzt sie: Heatmap bleibt „wo", VolumeBars wird „wie viel vs. Ziel".
- Gruppe aufklappbar (animateContentSize) für 7 Hauptgruppen; „n=…"-Minimeta je Gruppe.

### 6.4 `FatigueSignalCard` (Breakdown des Deload-Detectors)

- Vier horizontale Signal-Zeilen, je mit Icon, Titel, Score-Beitrag (`MetricCaption`):
  - E1RM-Abfall (60 Pkt., rose) · E1RM-Stagnation (25, amber) · RPE-Creep (25, amber) · Fehlversuchsquote ↑ (bis 25, amber)
- Nur aktive Signale gefüllt; inaktive gedimmt (`Faint`, „ok").
- Footer: „Fenster: 4 Wochen · n=12 Einheiten · Schwellwert 60".

### 6.5 `ZoneBar` (generisch)

- Parameter: `value`, `zones: List<Zone(start, end, color, label)>`, `min`/`max`; Rendering: runde Track-Bar (Radius.pill, Höhe 10 dp), Füllung mit Gradient der erreichten Zonen, zarter Grenz-Tick je Zonenwechsel; Wert-Label rechts (`MetricBody`, tnum).
- Verwendet in: StrainMeter, WeeklyStrain, Readiness-Delta, Deload-Fortschritt.

### 6.6 `RpeStrainPreview` (neuer Chip direkt am Set-Input)

- Beim Eintippen einer RPE (bzw. RIR) im `PendingSetRow` erscheint unter dem Input ein Preview-Pill (Stil der bestehenden Autoregulations-Pills):
  - `„RPE 9 → +0,6 t Session-Strain (4,2 t gesamt)“`
  - Farbe nach resultierender Zone (emerald/amber/rose); bei „hoch"-Prognose zusätzlich Hinweis-Label „Strain hoch — Zielerfüllung reicht".
- Datenquelle: `SessionLoadCalculator.preview(sets + hypotheticalSet)` (§8) — pure Funktion, direkt testbar.

### 6.7 `AutoregulationPill` (Restyle + Kontext)

- Beibehalten: „Ziel-RPE 8", „Zu schwer (RPE 9,5)", „Nächster Satz: 82,5 kg (−2,5 kg)", „Backoff-Satz: 72 kg".
- TIDE-Farben: Ziel-RPE-Pill emerald-Dim, Overshoot-Pill rose, Load-Pill `TidePrimaryBright`; Einheiten immer `MetricMicro`.
- Neu: Pill erhält optional „Grund"-Zeile (bei Overshoot): „−3,75 % wegen RPE-Delta −1,5" — als Tooltip/expandable, nicht immer sichtbar (Ruhe-Prinzip).

### 6.8 `RestTimer` (Restyle)

- Ring in `TideSky`, Track `@ 20%`; Füllung zeigt verbleibende Pause; **letzte 10 s: Track wechselt auf TideAmber** (Ember-Regel „Timer-Warnung" übernehmen); bei 0: Haptik (`HapticFeedbackHelper.confirm()`, existiert) + Glow-Impuls.
- Zusatz „Recovery hint" nur im Ready-Zustand (Readiness ≥ 65): unter dem Timer-Chip Mikrotext „Erholung im grünen Bereich" (`MetricMicro`, emerald) — **keine** Score-Anzeige während der Pause.

### 6.9 `DeloadBanner` (Neue Prominenz)

- Volle Breite, Tone `COLORED` mit `semanticColor = TideRose @ 10%`, Border rose @ 25%.
- Inhalt: Titel „Deload-Woche empfohlen", Score `MetricLarge` „Fatigue 68/100", Signale als kompakte Chips, zwei Aktions-Chips zur Übernahme eines Modus (aus `DeloadMode`): „Satzvolumen halbieren" / „Intensität −15 %" — setzt die bereits existierenden Prefs, ohne die Review zu umgehen.
- Platzierung: Dashboard **und** Recovery-Screen; im Workout-Flow nur als subtile Kopfzeile (kein Modal — nicht blockierend).

### 6.10 `ProgressionTrendCard` („wissenschaftlich")

- Vico-LineChart mit: E1RM-Serie (emerald, 2 dp), **Regressionslinie** (gestrichelt, Sky, 1 dp), **PR-Punkte** (Violet, gefüllt + Glow), gemäß Ember-Regel „PR-Marker violet".
- Über/unter dem Chart: Delta-Badges: „+5,2 % in 4 Wochen" (emerald), „Best: 132,5 kg am 12.08." (MetricBody), „n=8 Messungen · Epley".
- Achsen: X = Wochen (KW-Labels, `common_calendar_week`), Y = kg mit 2 Ticks; Tooltip (Tap) zeigt Datum, kg, RPE-Schnitt.

### 6.11 `StatCard`-Mapping (Restyle)

- PRIMARY = `TidePrimary` (Workouts, Volumen), SECONDARY = `TideViolet` (PRs, Streak), TERTIARY = `TideSky` (Schnitte, „Ø RPE"). Unverändertes Layout, nur Farbtokens.

### 6.12 `ReadinessBadge` (Kopfzeilen-Chip)

- Kleiner Pill im Dashboard-Header und History-Detail: „Bereit 72" (emerald) / „Moderat 48" (amber) / „Erschöpft 31" (rose); Track = `TideOnSurface @ 8%`, Text `MetricCaption` mit Zonenfarbe.

### 6.13 Empty & Fehldaten-State

- „Keine Daten": gestrichelter Gauge mit Erklär-Mikrotext, nie Score 0; Aktions-Chip „Erstes Training loggen".

---

## 7. Screen-Layouts

### 7.1 Dashboard (Home) — Layout-Skizze

```
┌─────────────────────────────────────────┐
│ ⌂ IronLog           [Bereit 72]  ⚙      │  ReadinessBadge im Header
├─────────────────────────────────────────┤
│ ┌─ ReadinessGauge-Hero (ACCENT) ──────┐ │  §6.1 + DeloadBanner darunter (bedingt)
│ │           72 · BEREIT                │ │
│ │      ──────↗────── 7-Tage-Sparkline  │ │
│ └──────────────────────────────────────┘ │
│ [▶ Training starten]                      │  CommandCenter (Ember-Stil, TidePrimary)
│ ┌ Workouts │ Volumen ┐┌ Strähne │ PRs ┐   │  StatCards 2×2
│ │ 5 · Diese Woche 4,2t│ 9 · 12. Aug 132kg│
│ └──────────┴──────────┘└─────────┴─────┘  │
│ ┌─ WeeklyStrainTimeline ──────────────┐  │  §6.2 Balken + Zonen
│ │  ▁▃▅▂▄▆▃   „3 t diese Woche“         │  │
│ └──────────────────────────────────────┘  │
│ ┌─ Muskelvolumen ─────────────────────┐  │  §6.3 Bars + Status
│ │ Brust ██████░░ |14/20| im Ziel       │  │
│ │ Rücken ████░░░░ |9/18| unter Ziel    │  │
│ └──────────────────────────────────────┘  │
│ ┌─ MuscleHeatmap (TIDE-Rampe) ────────┐  │  grün→lime→amber→orange→rose
│ │          (Body-Heatmap)              │  │
│ └──────────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

### 7.2 Active Workout — Layout-Skizze

```
┌─────────────────────────────────────────┐
│ ← Bankdrücken            [▣ Beenden]    │
│ 00:24:10   Strain ▓▓▓░ 4,2 t  [optimal] │  WorkoutTimer + StrainMeter live
│ [Pause 0:47]     (RestTimer, TideSky)   │
├─────────────────────────────────────────┤
│ ┌─ Bankdrücken ───────────────────────┐ │
│ │ Ziel-RPE 8 · Nächster Satz: 82,5 kg │ │  Autoregulations-Pills (TIDE)
│ │ −2,5 kg      (Overshoot-Pill rose)  │ │
│ │ Sᴀᴛᴢ 2  ▸ 8 Wdh  82,5 kg  RPE [8.5] │ │
│ │          [RPE 9 → +0,6 t (4,2 t)]   │ │  RpeStrainPreview §6.6
│ │ ✓ 8 Wdh · 80 kg · RPE 8    (Satz 1) │ │
│ └──────────────────────────────────────┘ │
│ [＋ Übung hinzufügen]                    │
└─────────────────────────────────────────┘
```

Workflow-Ergänzungen:
- **Strain-Überschreitung**: überschreitet die Session-Strain die Hochzone, erscheint unter dem Meter ein statischer Hinweis („Strain hoch — Zielerfüllung reicht; Backoff-Satz anbieten"), nicht modaler Dialog.
- **Deload aktiv**: ist ein Deload-Modus aktiv (Prefs), zeigen die Plan-Ziele die reduzierten Sollwerte (die Logik existiert bereits im Deload-Modus des Repos) — UI zeigt Badge „Deload-Woche" am Kopf.

### 7.3 Recovery-Screen (NEU, eigener Tab „Recovery")

```
┌─────────────────────────────────────────┐
│ ← Recovery           [Letzte 4 Wochen]  │
│ ┌─ ReadinessGauge (groß) ────────────┐ │
│ │       72 · BEREIT                  │ │
│ │  ▲ 8 seit letzter Woche            │ │
│ └────────────────────────────────────┘ │
│ ┌─ DeloadBanner ──────────────────┐    │  (bedingt)
│ └─────────────────────────────────┘    │
│ ┌─ Fatigue-Signale ─────────────────┐ │  §6.4 vier Signal-Zeilen
│ │ E1RM-Abfall        —    60 Pkt.  │ │
│ │ RPE-Creep          ▸    25 Pkt.  │ │
│ │ Fehlversuche       —    0 Pkt.   │ │
│ └──────────────────────────────────┘ │
│ ┌─ Wochenzyklus Strain ─────────────┐ │  28-Tage-Balken + Schnitt-Linie
│ │ ▁▃▅▂▄▆▃▂▃▅▄▅▂▄   Ø 3,1 t/Woche    │ │
│ └──────────────────────────────────┘ │
│ ┌─ Volumen nach Muskelgruppe ───────┐ │  §6.3 voll
│ └──────────────────────────────────┘ │
│ [Integrations-Hinweis: Schlaf/HRV]     │  Platzhalter-Karte (dezent)
└─────────────────────────────────────┘
```

Hinweis: Schlaf-/HRV-Integration ist **bewusst als Platzhalter** skizziert („In Zukunft:
Schlaf & HRV als Readiness-Faktoren") — die Architektur (Readiness aus mehreren
Quellen kombinierbar, `ReadinessSource`-Interface) wird im Proposal vorbereitet, aber
nicht gebaut.

### 7.4 Statistik/Progression („Scientific Analytics")

- ExerciseStatsScreen wird um die **ProgressionTrendCard** erweitert (6.10) und behält E1RM-Metriken („Erstes 1RM", „Aktuelles 1RM", „Steigerung" — existieren).
- Neue Sektion „Trainingslast" — `StrainMeter` Wochenansicht + „Load-Verteilung" (Anteil leicht/mittel/hart als gestapelter Balken), „sRPE-Schnitt" (Ø RPE je Session).
- Progression-Review (Coach) bleibt inhaltlich unverändert; nur Tone auf TIDE-Palette.

### 7.5 History-Detail

- Kopf: `ReadinessBadge` der Session (aus Assessment-Fenster), Session-Strain `MetricLarge`, Volumen; PR-Badges violet (bestehende Ember-Logik, Farbtausch).

### 7.6 Navigation & Shell

- BottomNav: 4 Tabs **Übersicht · Recovery · Verlauf · Mehr** (Recovery ersetzt den Einstiegsort der Statistikgruppe; Statistik bleibt unter Verlauf/Übung erreichbar). Farbwechsel zu TidePrimary @ 15% für aktiven Indikator.
- Splash/Icon: Emerald-Variante des bestehenden Icons (Phase 3.3/3.4 von Ember analog).

---

## 8. Daten- & Rechen-Hooks

| Funktion | Existiert | Verwendung |
|---|---|---|
| `DeloadDetector.assess() → DeloadAssessment(fatigueScore, signals, recommended, …)` | ✅ | ReadinessGauge, FatigueSignalCard, DeloadBanner |
| `MuscleVolumeCalculator.aggregateByMuscleGroup()/evaluateStatus()` | ✅ | MuscleVolumeBars, Volumen-Sektionen |
| `RpeAutoregulation.recommendNextSetWeightKg()/backoffSetWeightKg()` | ✅ | AutoregulationPill (Restyle + Preview) |
| `WorkoutCalculations.calculate1RM()` (Epley/Brzycki) | ✅ | ProgressionTrendCard |
| `DeloadMode`-Prefs + Deload-Repository (modify targets) | ✅ | DeloadBanner-Aktionen |
| **`SessionLoadCalculator` (NEU, core:common)** | ❌ | StrainMeter, RpeStrainPreview, Wochenzyklus |

**`SessionLoadCalculator`-Spezifikation (pure, testbar):**

```kotlin
object SessionLoadCalculator {
    /** RPE-gewichtete Tonnage: Σ (rpe/10 × kg × reps) je Arbeitssatz. */
    fun sessionStrainKg(sets: List<WorkoutSet>): Double
    /** Strain-Einheit fürs UI: kg → Tonnen (÷ 1000), 1 Dezimalstelle. */
    fun strainTons(kg: Double): String
    /** Zone relativ zum 28-Tage-Schnitt: LEICHT/OPTIMAL/HOCH/SPITZE. */
    fun zone(sessionStrainKg: Double, baseline28d: Double): StrainZone
    /** Preview: Strain inkl. hypothetischem Satz (für RpeStrainPreview). */
    fun previewStrainKg(sets: List<WorkoutSet>, hypothetical: WorkoutSet): Double
    /** 28-Tage-Baseline: Ø Session-Strain der letzten 4 Wochen. */
    fun rollingBaselineKg(sessions: List<List<WorkoutSet>>): Double
}
```

Nur SetTypes NORMAL/FAILURE zählen (Warmup/Drop ausgenommen — Konvention aus
`countedWorkSets` und `MuscleVolumeCalculator.isCountedSet`). RPE-fehlende Sätze
werden mit dem Sessionschnitt implantiert oder (bei komplett fehlender RPE) als
„ohne Strain" markiert — nie geraten.

---

## 9. Motion & Accessibility

- **Motion**: Gauge-Sweep 700 ms, Chart-Fade 220 ms, Count-up 500 ms; alles über `IronLogMotion`, komplett deaktivierbar via `reducedMotion` (existiert).
- **A11y-Spezifika**:
  - Farbe nie alleinige Information (Zonen-Labels immer, §4.4) — WCAG 1.4.1.
  - Kontraste nach §4.4 (AA).
  - Touch-Targets ≥ 40 dp (ButtonSize.iconButton ist Minimum).
  - Gauges/ZoneBars mit ProgressBar-Semantik + ContentDescription.
  - Charts: Werte zusätzlich als Textliste (nicht nur Canvas) erreichbar (TalkBack).
  - `tnum` verhindert Layout-Springen beim Count-up (SC 2.2.2-freundlich).

---

## 10. Implementierungs-Phasen (Reihenfolge wie Ember)

| Phase | Inhalt | Modul |
|---|---|---|
| P1 | `ThemeScheme.TIDE` + Color/Theme/Glassmorphism/Glow/Icon; alle Screens rendern sofort in TIDE | `core/designsystem`, `app` |
| P2 | `SessionLoadCalculator` + Unit-Tests; `MetricTextStyle`/`DataTypography` | `core:common`, `core:designsystem` |
| P3 | Komponenten: ZoneBar, ReadinessGauge, StrainMeter, MuscleVolumeBars, FatigueSignalCard, DeloadBanner, RpeStrainPreview, ProgressionTrendCard | `core:designsystem`, später feature-spezifisch |
| P4 | Dashboard-Umbau (Hero-Gauge, Strain-Timeline, VolumeBars, Banner) | `feature/dashboard` |
| P5 | Workout: StrainMeter live, RpeStrainPreview, RestTimer-Restyle, Deload-Badge | `feature/workout` |
| P6 | **Recovery-Screen** (neuer Tab + Navigation) | neu `feature/recovery` |
| P7 | Statistik/History: ProgressionTrendCard, Load-Sektion, ReadinessBadge | `feature/statistics`, `feature/history` |
| P8 | Verifikation: alle Tests, lint, assembleDebug, WCAG-Checkliste, Reduced-Motion-Matrix | — |

**Verifikationsliste (pro Phase)**: `./gradlew test`, `lintDebug`, `assembleDebug`, alle
3 ThemeSchemes + Light/Dark, TalkBack-Smoke-Test der Gauges, Reduced-Motion-Matrix.

---

## 11. Risiken & offene Fragen

| Risiko/Frage | Impact | Mitigation |
|---|---|---|
| Fatigue-Score aus 4-Wochen-Fenster ist träge; „Readiness von heute" leitet sich nur aus alten Daten ab | Mittel | Score-UI ehrlich beschriften („4-Wochen-Trend"), Delta-Chip zeigt Richtung; HRV/Schlaf als spätere Live-Quellen |
| SessionStrain-Formel (RPE × kg × reps) ist eine Vereinfachung gegenüber TRIMP/sRPE | Mittel | Formel als V1-Definition dokumentieren; Evaluierung mit Coach-Feedback; Pure-Funktion macht Austausch testbar |
| Neuer 5. Tab überlastet BottomNav | Niedrig | Recovery ersetzt Statistik-Einstieg (Statistik bleibt unter „Mehr"/Verlauf) |
| Zonen-Farbbindung (Emerald=alles) reduziert Unterscheidbarkeit zu Ember | Niedrig | TIDE-Default, Nutzer können zurück auf AMBER (ThemeScheme existiert) |
| Vico-Library-Grenzen (Bänder hinter Balken, Tooltips) | Mittel | Vor P3 API-Smoke; Fallback: eigene Canvas-ZoneBar |

---

## 12. Datei-Landkarte (Zielzustand)

```
core/designsystem/.../theme/
  Color.kt            ← + Tide* Farben, ThemeScheme.TIDE
  Theme.kt            ← + tideDark/LightColorScheme
  Glassmorphism.kt    ← TIDE-Tint
  Type.kt             ← + DataTypography / MetricTextStyle (tnum global)
core/designsystem/.../components/
  ZoneBar.kt, ReadinessGauge.kt, StrainMeter.kt, DeloadBanner.kt  (neu)
core/common/.../domain/util/
  SessionLoadCalculator.kt  (neu) + Test
feature/recovery/...        (neu – Screen, ViewModel, Cards)
feature/dashboard/...       (Umbau)
feature/workout/...         (StrainMeter, RpeStrainPreview, RestTimer-Restyle)
feature/statistics, feature/history (Erweiterungen)
```

---

## 13. Fazit

Concept 3 „TIDE" nutzt die bereits vorhandene, starke Trainingswissenschaft von
IronLog (Fatigue-Score, Volumen-Thresholds, RPE-Autoregulation, E1RM-Trends) und
macht sie zur visuellen Hauptsprache: dunkles Smaragd, leuchtende Daten, Zonen statt
Zielvorgaben, eine Recovery-Ansicht als neue Heimat für Erholungsentscheidungen.
Wie bei Ember bleibt der technische Unterbau unberührt — P1 (Token/Theme) rendert die
komplette App bereits in TIDE, alle weiteren Phasen sind additive Komponenten- und
Screen-Arbeit. Das Ergebnis ist ein Portfolio-starkes, wissenschaftlich argumentierendes
Trainingstagebuch, das sich von klassischen Workout-Apps durch „systemische" Lesbarkeit
abhebt.