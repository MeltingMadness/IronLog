# IronLog Redesign — Concept 1 „FORGE"

> **Status**: Proposal (kein Code)
> **Datum**: 2026-09-03
> **Umfang**: UI/UX-Audit des aktuellen „Ember"-Builds + vollständiges Designkonzept
> **Fokus**: Heavy-Lifting / Bodybuilding-Ergonomie, OLED-True-Black-Darktheme,
> taktile Gewichts-Stepper, Plate-Visualizer, mutige Typografie
> **Vorgänger**: `EMBER_REDESIGN_PLAN.md` (Ember ist implementiert; dieses Dokument ist der nächste Iterationsschritt, kein Rollback)

---

## 1. Executive Summary

**FORGE** ist ein Dark-First-Redesign, das IronLog vom „schönen Tracker" zum
„Werkzeug unter der Latz" weiterentwickelt. Die These: Während eines Satzes ist
der Nutzer erschöpft, schwitzt, trägt evtl. Handschuhe und hat ~2 Sekunden
Aufmerksamkeit zwischen den Sätzen. Jede Interaktion muss deshalb drei
Eigenschaften erfüllen:

1. **Lesbar aus 1 m Entfernung** — Hero-Daten (Gewicht, Wdh., Restzeit) in
   Schwarz auf True-Black-OLED mit ≥ 7:1 Kontrast und 24–64 sp Ziffern.
2. **Bedienbar ohne Tastatur** — Gewichtsadaption ist der häufigste Vorgang
   (+2,5 kg nach einem leichten Satz). Dafür gibt es einen taktilen
   **WeightStepper** statt Fokus → IME → Tippen → Schließen.
3. **Selbsterklärend an der Hantel** — ein **PlateVisualizer** zeigt zu jedem
   Zielgewicht die konkrete Hantelbeladung (IWF-Farbcode), damit das Rechnen
   im Kopf entfällt.

Das Konzept ist als Evolution angelegt: alle bestehenden Token-Hälse
(`MaterialTheme.semantic`, `ironLogDimens`, `ironLogMotion`, `IronLogSurfaceRoles`)
bleiben erhalten und werden mit neuen Werten bzw. neuen Token-Gruppen befüllt.
Screens können damit screen-für-screen migriert werden.

---

## 2. UI/UX-Audit — aktueller Stand (Ember)

Bewertet am Code-Stand `feature/workout`, `feature/dashboard`, `core/designsystem`
(Stand 2026-09-03). Severity: 🔴 Blocking für den Fokus / 🟠 Major / 🟡 Minor.

| # | Befund | Evidenz | Severity |
|---|--------|---------|----------|
| F1 | **Gewichtseingabe ist rein typbasiert.** Jede Anpassung läuft über `CompactTextField` → Fokus → Decimal-IME → Tippen → IME schließen. Mit kalten/nassen Händen und unter Zeitdruck zwischen Sätzen ist das die teuerste Interaktion der App. | `SetInputRow.kt`, `ActiveWorkoutScreen.PendingSetRow` | 🔴 |
| F2 | **Keine Plate-Math.** Der Nutzer sieht „100 kg × 3 × 8“ und rechnet selbst (2 × 20 + 1 × 10 je Seite). Kein Baustein visualisiert Beladung. | Zielzeile `workout_target_with_weight` | 🔴 |
| F3 | **Dark Theme ist kein OLED-Schwarz.** `DarkBackground = #0E1117`, Karten additional `alpha = 0.68f` auf `#12161E` — Ergebnis: flächiges Grau-Gitter, dunkle Ziffern verlieren Kanten. OLED-Vorteile (echtes Schwarz, Akku, Kontrast) ungenutzt. | `Color.kt`, `IronLogSurfaceCard(alpha = 0.68f)`-Aufrufe | 🔴 |
| F4 | **Ziffern sind zu klein für den Anwendungsfall.** Geloggte Sätze: `titleMedium` = 14 sp in `LoggedSetBox`; Satzliste `bodyMedium` = 12 sp. Ziel-/Hinweiszeilen 11–12 sp. Bei einer App, deren Kerninhalt Zahlen sind, ist die Zahlen-Hierarchie flach. | `ActiveWorkoutScreen.LoggedSetRow` | 🟠 |
| F5 | **Touch-Ziele unter Minimum.** Log-Button 40 dp (`ButtonSize.iconButton`) neben 36 dp-high Textfeldern in dichter Reihe; Warmup-Toggle-Icon 18 dp. |
| F6 | **Kontrast-Drift durch Alpha-Stapelung.** Outline `alpha 0.45f`, Karten `alpha 0.68f`, Glassmorphism `alpha 0.76f`, Akzente mit `copy(alpha = 0.28f)` — die effektiven Kontraste sind unvorhersehbar und teils unter WCAG-AA für Text. | mehrere Screens | 🟠 |
| F7 | **Rest-Timer ist ein Chip.** Wichtigster Zustand zwischen Sätzen („wann gehe ich wieder ans Eisen?") rendert als kleiner FlowRow-Chip mit Standard-Typografie. | `RestTimer.kt`, FlowRow im Workout-Screen | 🟠 |
| F8 | **Schemata/Pläne datenlos.** `Schema: Linear` ohne sichtbare Konsequenz („+2,5 kg wenn alle Sätze im Ziel"). Progression wird verbal statt numerisch kommuniziert. | `progression_review_scheme`-Zeile | 🟡 |

**Was Ember schon richtig macht** (Behalten): `tnum`-Tabellenziffern überall,
spring-basierte `pressScale`-Interaktion, Haptik-Helfer (`tick/confirm/reject`),
Superset-Farbcodierung, kompakte Dichte, RPE/RIR-Logik, Progression-Engine.

---

## 3. Konzept: Die drei Säulen

### 3.1 „Pit Dark" — OLED-True-Black mit Licht-Layern

Echte Schwarzbasis `#000000`; Hierarchie entsteht durch **Licht statt Grau**:
Content hebt sich über 1 dp-Konturen und gezielte Akzentleuchten ab, nicht über
immer hellere Oberflächen. Ergebnis: maximaler Ziffernkontrast, kein
Grau-Schleier, Akkugewinn auf OLED-Panels.

### 3.2 „Iron Numbers" — mutige Typo, Daten sind Helden

Neue Data-Rolle in der Typo-Rampe (40–64 sp, Black, `tnum`) ausschließlich für
Gewicht / Wdh. / Restzeit / Volumen. Labels werden klein, versal und getrackt —
das klassische Kraftsport-Dash-Board-Gefühl (Uhr, Anlage, Scoreboard).

### 3.3 „Hands Full" — taktile Kontrolle

Stepper + Plate-Visualizer + großflächige Log-Aktion. Alle Satz-Interaktionen
in der unteren 60 % des Screens (Daumenzone), ohne IME erreichbar. Haptik als
Bestätigungskanal: jeder Schritt tickt, Erfolg bestätigt, Fehler rejected.

---

## 4. Farbsystem „Pit Dark"

### 4.1 Neutrale Skala (OLED-Layer)

| Token | Hex | Verwendung | Kontrast zu Schwarz |
|-------|-----|-----------|--------------------|
| `pit0` | `#000000` | App-Hintergrund (True Black) | — |
| `pit1` | `#0A0A0C` | Erste Ebene: Karten, Listen-Hintergrund | 1.05 : 1 (als Fläche, nicht Text) |
| `pit2` | `#141416` | Erhöhte Elemente: Stepper, Chips, Sheets | — |
| `pit3` | `#1D1E21` | Höchste Ebene: Dialoge, Menüs, Fokus-Flächen | — |
| `stroke` | `#26272B` | 1 dp-Konturen aller Karten/Stepper | — |
| `strokeStrong` | `#3A3B40` | Aktive Konturen (fokussierter Stepper) | — |
| `ink` | `#FFFFFF` | **Hero-Daten** (Gewicht, Wdh., Timer) | **21 : 1 (AAA)** |
| `inkHigh` | `#E6E8EC` | Fließtext, Titel | 17.4 : 1 (AAA) |
| `inkMid` | `#9BA0A8` | Sekundärtext, Meta | 7.0 : 1 (AAA für Normaltext) |
| `inkLow` | `#6A6E76` | Disabled, Platzhalter (nicht für Inhaltstext) | 3.6 : 1 (nur ≥ 18 sp / Icons) |

Regeln:
- **Keine Alpha-Stapelung mehr** auf Text/Konturen: Tokens sind final gemischt;
  `copy(alpha = …)` ist nur noch für Scrim/Press-States erlaubt.
- Karten bekommen **1 dp `stroke` + `pit1`** statt `alpha = 0.68f`.

### 4.2 Marken- & Aktionsfarbe „Molten"

| Token | Hex | Verwendung | Kontrast |
|-------|-----|-----------|----------|
| `molten` | `#FF7A1A` | Primär-Aktion, aktiver Zustand, Auswahl | 7.4 : 1 auf `#000` (AA/AAA large) |
| `moltenBright` | `#FF9E3D` | Pressed/Highlight, Verlaufsende | 10.9 : 1 |
| `onMolten` | `#140800` | Text/Icon auf Molten | 7.9 : 1 (AA) |
| `moltenGradient` | `#FF5A1F → #FF9E3D` (135°) | Nur Log-Button + aktiver Tab-Indikator | — |

Molten ersetzt `EmberPrimary`/`DarkPrimary`. Orange bleibt die Marke, geht aber
zwei Stufen heißer und wird **ausschließlich** für Aktion + Live-Zustand
verwendet — niemals für Datenwerte (Daten sind weiß).

### 4.3 Daten- & Statusfarben

| Token | Hex | Verwendung | Kontrast auf `#000` |
|-------|-----|-----------|--------------------|
| `prGreen` | `#3DFF88` | PR, Ziel erreicht, Bestätigung | 15.9 : 1 |
| `warnAmber` | `#FFB020` | Warnung, RPE > 9.5, Restdate | 11.3 : 1 |
| `dangerRed` | `#FF4D5E` | Fehler, Verwerfen, Stop | 6.5 : 1 |
| `infoSky` | `#4DC3FF` | Info, Hinweis, History-Verweis | 10.6 : 1 |
| `violetGlow` | `#B69CFF` | Superset-Gruppe A | 9.8 : 1 |
| `skyGlow` | `#4DC3FF` | Superset-Gruppe B (identisch infoSky) | — |
| `roseGlow` | `#FF7A9E` | Superset-Gruppe C | 8.9 : 1 |

Alle Statusfarben erfüllen ≥ 4.5 : 1 auf `pit0`/`pit1` und dürfen damit auch
für 11 sp-Labels verwendet werden (fixes F6).

### 4.4 Plate-Farbtokens (IWF-Code, OLED-getunt)

Für den PlateVisualizer. IWF-Kodierung wird erkennbar übernommen, aber für
True-Black aufgehellt (reines IWF-Blau `#1E63D0` ertrinkt auf Schwarz).

| Token | Hex | Gewicht (kg) | IWF-Referenz |
|-------|-----|-------------|--------------|
| `plate25` | `#FF453A` | 25 | Rot |
| `plate20` | `#3D7BFF` | 20 | Blau |
| `plate15` | `#FFD60A` | 15 | Gelb |
| `plate10` | `#30D158` | 10 | Grün |
| `plate5` | `#F2F2F7` | 5 | Weiß |
| `plate2_5` | `#BF5AF2` | 2,5 | (Violett statt Schwarz — Schwarz kollabiert auf OLED) |
| `plate1_25` | `#C7C7CC` | 1,25 | Chrom |
| `barSteel` | `#8E8E93` | Hantelstange | — |

Diese Farben sind **semantisch belegt** (Gewichtsklassen) und dürfen sonst
nirgends verwendet werden — der Wiederkennungswert „Farbe = Platte" ist der
Punkt.

### 4.5 Mapping auf den bestehenden Code

| Neues Token | Alte Stelle (`Color.kt` / Theme) |
|-------------|-------------------------------|
| `pit0..pit3` | `DarkBackground`, `DarkSurface`, `DarkSurfaceElevated`, `DarkSurfaceMuted` |
| `ink/inkHigh/inkMid/inkLow` | `DarkOnBackground`/`DarkOnSurface`/`DarkOnSurfaceVariant` |
| `molten`/`moltenBright`/`onMolten` | `Primary`/`DarkPrimary`/`OnPrimary` |
| Statusfarben | `EmberSuccess/Danger/Warning` + `EmberSemanticColors` (Werte ersetzen, Struktur bleibt) |
| Plate-Tokens | **neu** — `PlateColors`-Objekt in `DesignTokens.kt` |
| `ThemeScheme`-Enum | neuer Eintrag `FORGE` (dark-only-Konzept; Light-Variante siehe 4.6) |

### 4.6 Light-Theme-Position

Concept 1 ist **Dark-First**: FORGE wird als fünftes `ThemeScheme` mit
`DarkTheme = true` geführt. Eine Light-Variante („Forge Paper": `#F4F2EF`
Papier, Tinte `#1A120C`, identische Akzente mit Deep-Varianten wie heute bei
`EmberSuccessDeep`) wird definiert, aber bewusst als sekundär behandelt —
kein Screen-Migrationsschritt darf Light-First entwerfen.

---

## 5. Typografie „Iron Numbers"

Font bleibt **Figtree** (bereits gebündelt mit Black-Schnitt + Italic,
variable Gewichte via `FontFamily`-Mapping). Es ändert sich die Rollenverteilung.

### 5.1 Neue Data-Rolle

| Stil | Größe / Zeile | Weight | Tracking | Verwendung |
|------|--------------|--------|----------|------------|
| `dataHero` | **64 / 64 sp** | Black | −2.0 sp | Restzeit groß, Screen-Titel Workout (Live-Timer) |
| `dataXL` | **48 / 50 sp** | Black | −1.5 sp | Stepper-Wert (Gewicht), Dashboard-Volumen |
| `dataL` | **32 / 34 sp** | ExtraBold | −1.0 sp | Stepper-Wert kompakt, Satz-Zahlen in Fokus |
| `dataM` | **24 / 26 sp** | Bold | −0.5 sp | Geloggte Satz-Werte, Stat-Karten-Kern |
| `dataS` | **16 / 18 sp** | SemiBold | 0 | Inline-Zahlen im Fließtext |

Alle Data-Stile: `fontFeatureSettings = "tnum"` (bereits Standard im Projekt),
`lineHeight` = Größe + 2 sp, nie kursiv, nie `inkMid`.

### 5.2 Label-Rolle (Scoreboard-Labels)

| Stil | Größe / Zeile | Weight | Tracking | Verwendung |
|------|--------------|--------|----------|------------|
| `labelOverline` | 11 / 14 sp | Bold | +8 % | Versal-Labels: „GEWICHT", „WIEDERHOLUNGEN", „PAUSE" |
| `labelChip` | 11 / 14 sp | SemiBold | +4 % | Chips: Set-Nummern, RPE, Status |
| `labelMono` | 12 / 16 sp | Medium | +2 % | Metadaten, IDs, Zeitstempel |

Versal-Labels via `Text(text.uppercase(), style = labelOverline)`; Farbe immer
`inkMid`, nie `inkLow` (F6).

### 5.3 Rampen-Pflege (Bestand)

`displayLarge/Medium`, `headline*`, `title*`, `body*` bleiben aus `Type.kt`
erhalten; `bodySmall` (11 sp) bleibt für Rechts-Review-Feintext, verliert aber
die Erlaubnis, Ziffern zu tragen. Satz-Daten wandern konsequent in Data-Stile.

### 5.4 Dynamische Typo

`dataHero/dataXL` skalieren mit `LocalDensity.fontScale` bis max. 1.3×, danach
Klemmung mit Hinweis-„"— der Stepper-Wert darf nie umbrechen; bei 1.3×+ weicht
der Stepper auf `dataL` aus.

---

## 6. Komponenten

### 6.1 `WeightStepper` — das Kernstück

**Zweck:** Gewicht (und optional Wdh.) ohne IME verändern. Ersetzt
`CompactTextField` für Gewicht in `PendingSetRow` und `ExtraSetInput`.

```
┌──────────────────────────────────────────────────┐
│  ┌─────────┐   GEWICHT (kg)        ┌─────────┐  │  Höhe 72 dp
│  │    −    │      102.5            │    +    │  │  Radius: pill
│  └─────────┘   ─────────           └─────────┘  │  Fläche pit2, 1dp stroke
│                                                  │
│   [1.25] [2.5] [5]  ◉ Plan-Schritt               │  Schrittweiten-Chips 28 dp
└──────────────────────────────────────────────────┘
```

**Anatomie & Maße**
- Container: 72 dp hoch, `Radius.pill`, `pit2`, `stroke`; fokussiert/aktiv
  `strokeStrong` + `molten`-Text.
- −/+-Zonen: je 88 × 56 dp (≫ 48 dp Minimum, mit Handschuh bedienbar),
  Icon 28 dp, `pressScale(0.92f)`.
- Wert: `dataXL` (48 sp) `tnum` Bold, `ink`, zentriert; suffix „kg"/„lb" als
  `labelOverline` darüber.
- Schrittweiten-Chips: `1.25 / 2.5 / 5 / Plan` — „Plan" liest
  `ProgressionConfig.configuredStep()` (bereits im `ProgressionReviewItemUi`-Modell
  vorhanden) als Default. Aktiver Chip: `molten`-Kontur.

**Interaktionsspezifikation**
| Geste | Wirkung | Feedback |
|-------|---------|----------|
| Tap −/+ | ± aktive Schrittweite | `haptic.tick()`, Wert springt (kein Tween — Ziffer wechseln sofort), Fläche blitzt `molten` 8 % |
| Press-and-hold −/+ | Auto-Repeat: Start nach 400 ms, 120 ms-Intervall, ab 10 Schritten 60 ms | `tick()` pro Schritt |
| Tap auf Wert | Sheet „Gewicht eintippen" (numpad-Layout, 56 dp-Tasten) | `confirm()` |
| Long-Press (≈ 350 ms) auf Wert | **PlateVisualizer-Popover** (6.2) | `tick()` |
| Swipe horizontal über Wert | ± aktive Schrittweite (Skala-Schnippen) | `tick()` |
| Wert erreicht Plateau (z. B. 2.5er-Schritt an 200 kg-Limit) | kein Inkrement | `reject()` + kurzes Schütteln (2 dp, 120 ms) |

**A11y:** `semantics { stateDescription = "102,5 Kilogramm" }`,
`customAccessibilityActions` für Inkrement/Dekrement/Schrittweite; Stepper als
eine fokussierbare Einheit (nicht 4 Tabs). Mindestens 48 dp überall.

**Staat** — `WeightStepperState(valueKg, stepKg, unitSystem, bounds)` als
reine Klasse → unit-testbar ohne Compose (Vorbild: `targetWeightHint`-Test).

### 6.2 `PlateVisualizer`

**Zweck:** Zielgewicht ↔ Hantelbeladung. Beantwortet „Was lege ich auf?".

**Datenmodell** (rein, testbar — `PlateMath`):
```kotlin
data class PlateLoadout(
    val barKg: Double,                    // 20 kg Standard; 15 kg Damennorm optionell
    val perSide: List<Double>,            // sortiert absteigend, inkl. Mehrfachanzahl
    val remainderKg: Double               // > 0 → „Rest"-Chip
)
fun computeLoadout(targetKg: Double, available: List<Double> = IWF_SET, barKg: Double = 20.0): PlateLoadout
```
- `IWF_SET = [25, 20, 15, 10, 5, 2.5, 1.25]`, Greedy je Seite, Rest > 0 möglich
  (z. B. 102.5 kg → je Seite 25 + 15 + 2.5 + … konkret: (102.5 − 20)/2 = 41.25 →
  25 + 15 + 1.25). Unit-System: bei `IMPERIAL` interne kg-Rechnung, Anzeige lb +
  lb-Platten-Set (45/35/25/10/5/2.5 lb).

**Rendering**
```
GEWICHT 102.5 kg                       ← labelOverline + dataM
┃███▓▓▓░░ 20 ┃ 20 ░░▓▓▓███┃            ← Stange barSteel, Spiegel-Symmetrie
   25 15 1.25    1.25 15 25            ← Unter-Labels nur ≥ 8 dp Breite
```
- Höhen: `compact` 28 dp (in Karten-Zeile), `full` 44 dp (Popover, Detail).
- Plattenbreite ∝ √(kg) (25 kg ≈ 2.2 × 1.25 kg-Breite), Höhe konstant —
  reale Hantel-Proportion, keine Charts.
- Farbe exklusiv aus Plate-Tokens (4.4); Kragen = 4 dp `barSteel`-Ring.
- `remainderKg > 0` → Chip „+ 1.25 kg Rest" in `warnAmber`.
- Ziel unter Stangengewicht → Stepper zeigts, Visualizer zeigt nur Stange +
  `infoSky`-Hinweis „unter Stangengewicht".

**Einsatzorte:** (a) Stepper-Popover, (b) Workout-Karte beim Ziel (compact),
(c) ProgressionReview-Änderungszeile (compact alt/neu nebeneinander),
(d) Workout-Detail (History) beim Satz (compact).

### 6.3 `SetRow` v2 — Satzzeile

Ersetzt `LoggedSetRow`/`PendingSetRow`-Visual im Workout-Screen.

**Geloggter Satz**
```
 S3   102.5 kg      8 Wdh      RPE 8    ✓      ← dataM / dataM / labelChip
```
- Set-Nummer als `labelChip` in 32 dp-Kreis (`pit2`, aktiver Satz: `molten`-Kontur).
- Werte `dataM` (24 sp) `ink`; Einheiten weg (Spalten-Kontext), RPE als Chip.
- Ziel erreicht → `prGreen`-Häkchen; Zeile voll deckend statt `alpha 0.68f`.
- Edit: Tap öffnet Inline-Edit mit Stepper statt IME-Feldern (F1).

**Ausstehender Satz**
```
 S4  ┌──────────── WeightStepper ┌─────────┐  LOGGEN →
     │  −   102.5 kg            + │         │  56 dp, moltenGradient
     └────────────────────────────┘─────────┘
```
- Stepper (6.1) ersetzt das Gewichtsfeld; Wdh. + RPE bleiben kompakte Felder
  (Zahlen kleiner, Tastatur ok) ODER zweite Stepper-Zeile bei Bedarf.
- **Log-Button**: 56 dp hoch, volle Breite rechts oder unter der Reihe,
  `moltenGradient`, Label „LOGGEN" + Ziel-Echo „102.5 × 8" — Echo verhindert
  Fehllogs (heute Blindflug nach Feld-Verwirrung).
- Ziel-Erledigt-Status (3/3) → Log-Bereich kollabiert zu „+ Extrasatz"-Zeile.

### 6.4 `RestTimer` v2 — vom Chip zur Bühne

- Standardzustand: bleib kompakter Chip in der FlowRow (F7 mild), aber:
  24 sp `dataM` `tnum`, `pit2`-Fläche, Fortschritts-Balken 2 dp unten.
- **Expanded** (Tap oder letzter Satz einer Übung): Full-Width-Banner über der
  Übungsliste — `dataHero` (64 sp) Countdown, Übungsname, Plus/Minus-Stepper
  (± 15 s, 48 dp), „Überspringen". Auto-Collapse bei Ablauf + `confirm()`-Haptik
  + Vibration-Loop optional (Einstellung existiert: `timerKeepScreenOn`-Muster).

### 6.5 Weitere Bausteine

| Komponente | Änderung ggü. Ember |
|------------|--------------------|
| `IronLogSurfaceCard` | Dark: `pit1` + 1 dp `stroke`, `alpha`-Parameter deprecated in FORGE-Kontext |
| BottomNavBar | 4 Tabs bleiben; aktiver Indikator = 3 dp `molten`-Balken über dem Icon statt Pill-Indikator; Icon + Label 12 sp versal |
| PR-Badge | `prGreen` Kontur + „▲ 2.5" Differenz-Chip statt Stern-Icon allein |
| Stat-Karte (Dashboard) | Wert `dataXL`, Label `labelOverline`, Delta-Chip (`prGreen`/`dangerRed`) |
| Warmup-Toggle | „W" als 28 dp-Chip am Satz (statt Icon-Versteck), Warmup-Zeilen kursiv + `inkMid` |
| Übungspicker | Suchfeld bleibt; jede Zeile zeigt letzten Satz als `dataS`-Echo |

---

## 7. Screen-Layouts

### 7.1 Active Workout (Kern-Screen)

```
┌────────────────────────────────────────────────────┐
│ PUSH DAY · TAG 3                          ⏱ 42:18  │  Titel labelOverline,
│                                                    │  Timer dataM tnum
│ ┌──────────────────────────────────────────────────┐│
│ │ ⏸ PAUSE  01:24                        ▓▓▓▓░░ +15s││  RestBanner (6.4)
│ └──────────────────────────────────────────────────┘│
│                                                    │
│ ┌──────────────────────────────────────────────────┐│
│ │ BANKDRÜCKEN                    SO: 3×8 @ 102.5   ││  labelOverline / dataS
│ │ ▐█████▓▓▓▓░░░░ 102.5 kg                          ││  PlateVisualizer compact
│ │ ✓ S1 100.0×8  ✓ S2 102.5×8  ▸ S3 —               ││  SetRow-Chips
│ │ ┌──────────────────────────────────────────────┐ ││
│ │ │  −    GEWICHT  102.5 kg              +       │ ││  WeightStepper
│ │ └──────────────────────────────────────────────┘ ││
│ │ WDH [ 8 ]  RPE [ 8 ]      ┌────────────────────┐ ││
│ │                           │  LOGGEN  102.5×8   │ ││  56 dp molten
│ │                           └────────────────────┘ ││
│ └──────────────────────────────────────────────────┘│
│                                                    │
│ ┌──────────────────────────────────────────────────┐│
│ │ SCHRÄGBANK-VD ...                                ││  nächste Karte identisch
│ └──────────────────────────────────────────────────┘│
│  ▐ Dashboard │ Verlauf │ Übungen │ Pläne ▐          │  BottomNav FORGE-Indikator
└────────────────────────────────────────────────────┘
```

Ergonomie-Notizen:
- Primäre Aktion (Loggen) endet in der unteren Bildschirmhälfte der aktiven
  Karte — Daumenzone; Listenscroll verschiebt die aktive Karte ans Ende der
  natürlichen Scroll-Position.
- „Aktiver Satz" = erste ausstehende Reihe; die Karte scrollt per
  `LaunchedEffect` automatisch in den sichtbaren Bereich (heute: manuell suchen).
- Der Rest-Banner überlappt **nicht** die Tastatur mehr — es gibt keine Tastatur
  mehr im Normalpfad (F1 gelöst).

### 7.2 Dashboard

```
┌──────────────────────────────────────┐
│ GUTEN MORGEN              ▐ EINST. ▐ │
│                                      │
│  TRAINING STARTEN                    │  64 dp moltenGradient, primär
│  Push Day · letztes Mal vor 2 Tagen  │
│                                      │
│ ┌────────────┐  ┌────────────┐       │
│ │ WOCHEN-VOL.│  │ SESSIONS   │       │  Stat-Karten: dataXL-Wert,
│ │ 34 250 kg  │  │ 4 / 5      │       │  Delta-Chips
│ │ ▲ 2 100    │  │ ● ● ● ● ○  │       │
│ └────────────┘  └────────────┘       │
│ ┌──────────────────────────────────┐ │
│ │ MUSKEL-HEATMAP  (Wochen)         │ │  bleibt, Farben auf Glow-Tokens
│ └──────────────────────────────────┘ │
│ ┌──────────────────────────────────┐ │
│ │ PROGRESSIONS-COACH  2 offen      │ │  Einstieg Review (bestehend)
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
```

### 7.3 Progression Review

- Karten wie heute (Name, Schema, Begründung), aber:
  - Begründung bleibt Fließtext (`inkHigh`).
  - Neu: `PlateVisualizer`-Paar „alt → neu" bei Gewichtsänderungen.
  - Akzeptieren/Verwerfen: 48 dp, Akzeptieren `molten`, Verwerfen `dangerRed`-Kontur.
  - „Alle sicheren übernehmen" prominent oben, 52 dp.
- Reine Informations-Sessions (KEEP_TARGET, aus Aufgabe 2/3): statt leerer
  Status-Zeilen ein kurzes Zusammenfassungs-Banner „Coach: 3× Ziel gehalten,
  1× zu wenig Daten" — Zahlen in `dataM`.

### 7.4 Workout-Detail (History)

- Satzzeilen: `dataS`-Werte statt 12 sp; PlateVisualizer compact neben jedem
  Arbeitssatz-Gewicht (aus Task 2 fortgeführt).
- Progressions-Coach-Sektion (bereits implementiert) bleibt, bekommt
  Status-Farbchips nach 4.3.

---

## 8. Motion & Haptik

| Ereignis | Motion | Haptik |
|----------|--------|--------|
| Stepper-Schritt | kein Tween — Sofortwechsel; Fläche 8 % `molten`-Blitz (90 ms) | `tick()` |
| Stepper-Halte-Ende | — | `tick()` leichter |
| Loggen | Button springt (pressScale), Reihe morph zu LoggedSet (`animateContentSize`, 220 ms `ironLogMotion.normal`) | `confirm()` |
| PR | Konfetti-Burst dezent (6 Partikel, 400 ms), PR-Badge pulsierend 1× | `confirm()` + `tick()`-Doppel |
| Rest abgelaufen | Banner expandiert `expandVertically(spring())` | `confirm()` |
| Fehler (Reject/Plateau) | 2 dp Shake 120 ms | `reject()` |
| Tab-Wechsel | bestehende Nav-Animation; Indikator-Balken gleitet 180 ms | — |

`reducedMotion`-Schalter (existiert: `AppPreferences.reducedMotion`) deaktiviert
Shake/Puls/Burst; Stepper bleibt funktional identisch.

---

## 9. Umsetzung im Code (Mapping & Phasen)

**Neu/erweitert ohne Screen-Bruch:**
1. `DesignTokens.kt`: `PitColors`, `PlateColors`, `MoltenColors`, Data-Typo-Hilfen.
2. `Color.kt`: FORGE-Farbblock (neue vals, alte bleiben — keine Migration).
3. `Theme.kt`: `ThemeScheme.FORGE` → `darkColorScheme(pit0..)`, Semantics auf Glow-Werte.
4. `Type.kt`: `dataHero..dataS`, `labelOverline/Chip/Mono` als eigene vals +
   `LocalForgeTypography` (CompositionLocal analog `EmberSemanticColors`).
5. Neue Komponenten in `core/designsystem/presentation/common`:
   `WeightStepper.kt`, `PlateVisualizer.kt`, `PlateMath.kt` (rein testbar),
   `RestTimerBanner.kt`.
6. Screens: Workout → Dashboard → Review/Detail (je ein PR).

**Explizit unverändert** (Lernen aus Ember-Plan): Database, Repositories,
ViewModels (außer Stepper-State-Expositions-Additiva), Navigation-Graf.

**Schätzungen:** Foundation S · Stepper+PlateMath M · PlateVisualizer-UI M ·
Workout-Screen M · Rest/Dashboard/Review je S · A11y-Pass S.

**Risiken:**
- `ThemeScheme`-Enum-Erweiterung → Prefs-Migration (safeValueOf-Muster vorhanden).
- Stepper-Double-Submit-Gefahr: Log-Button-Debounce wie bestehende
  `submissionId`-Logik weiterverwenden.
- Figtree Black bei 64 sp auf 360 dp-Breite: „102.5" passt (5 Zeichen × ~0.55 em),
  bei `IMPERIAL`-„212.5 lb" Layout-Check nötig → `dataHero` nur für Timer.

---

## 10. A11y-Checkliste

- [ ] Alle Text-Kontraste ≥ 4.5 : 1 (Normal) bzw. ≥ 3 : 1 (≥ 18 sp Bold) — 4.1–4.3 erfüllt
- [ ] Stepper: `customAccessibilityActions` (erhöhen/senken/Schrittweite), StateDescription
- [ ] PlateVisualizer: `contentDescription` = „je Seite 25, 15 und 1,25 Kilogramm"
- [ ] Touch-Ziele ≥ 48 dp (F5: Log 56 dp, Stepper-Zonen 56 dp, Chips 28 dp nur visuell + umgebende 48 dp-Hitbox)
- [ ] TalkBack-Reihenfolge: Karte → Stepper → Wdh. → RPE → Loggen
- [ ] `reducedMotion` respektiert (F8-Liste oben)
- [ ] Dynamische Typo bis 1.3× ohne Wert-Abschneidung (5.4)
- [ ] Farbcodierte Platten nie allein Informationsträger (kg-Label immer mit)

---

## 11. Offene Fragen

1. Stangengewicht: 20 kg fix (männer) / 15 kg (Damennorm) als Übung-Attribut? → Model-Frage,
   v1: 20 kg global, Einstellung folgt.
2. lb-Platten-Set: 45-lb-Norm ok? (Annahme: ja, US-Gym-Standard.)
3. Sollen „DROP_SET/FAILURE" (neues `SetType`) im Visualizer als Platten-Deltas
   erscheinen? → v1: nein, nur Arbeitssätze.
4. Rest-Banner vs. bestehende FlowRow-Chips: ersetzen oder koexistieren? → Vorschlag: ersetzen.
5. Overload-Warnung bei fehlenden Platten im echten Gym (Bestand nicht bekannt) —
   v1 ohne Inventar, `available`-Parameter für später.
