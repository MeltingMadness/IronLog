# IronLog Concept 4 — „Pulse": Fluid Motion & kinästhetisches Workout-Redesign

> **Status**: Design-Proposal (Konzept, keine Implementierung)
> **Erstellt**: 2026-09-05
> **Umfang**: UI/UX-Audit des Ist-Zustands + vollständiges Design-System-Konzept (Farben, Typografie, Motion-Spring-Physik, Komponenten, Screen-Layouts)
> **Fokus**: Fluid Motion, Spring-Physik, morphing Set Cards, celebratory PR-Trigger, Bottom-Sheet-Quick-Steppers, Swipe-to-Log-Gesten, dynamisches intensitätsbasiertes Color Theming

---

## 1. Executive Summary

IronLogs aktuelles „Ember"-Design (warmer Amber-Look, Figtree-Typografie, Glassmorphism-Reste) ist farblich bereits stark, fühlt sich in der Interaktion aber **statisch** an: Screen-Wechsel sind harte Schnitte, gesetzte Sätze „erscheinen" statt zu „morphen", neue Rekorde enden in einer Snackbar, und die Log-Geste ist auf Tippen reduziert. Concept 4 **„Pulse"** macht Bewegung zur Designsprache: Jede Interaktion bekommt eine **Spring-Physik-Kurve**, Set-Cards **morphieren** zwischen Eingabe- und Logged-Zustand, PRs lösen eine **kurze Feier** aus, Werte werden über **Bottom-Sheet-Stepper** justiert, Sätze per **Swipe** geloggt, und Farben reichen sich **dynamisch nach Intensität (RPE) und Satztyp**.

Das Konzept baut auf den bestehenden Ember-Tokens auf (kein kompletter Neuentwurf), erweitert sie um einen **Motion- und Intensitäts-Layer** und definiert Komponenten so, dass die Implementierung rein in Compose (Animation APIs, Gesten, Haptik) bleibt — DB, Models, ViewModels bleiben unangetastet.

---

## 2. UI/UX-Audit des Ist-Zustands

### 2.1 Stärken (bleiben erhalten)

| Bereich | Befund |
|---|---|
| Farbidentität | Warmes Amber-Theme (Light `#FF6B00`, Dark `#FF9500`), semantische Akzente (Rose, Sky, Violet, Teal, Success, Danger) — differenziert und markant. |
| Zahlen-Darstellung | Figtree mit `tnum` (Tabellenziffern) app-weit; Gewichte/Wiederholungen bleiben spaltenstabil (z. B. `LoggedSetBox`-Grid). |
| Dichte | Kompakte Zeilenhöhen (Button 48dp, Boxen 40dp, bodyMedium 12sp) erlauben viele Sätze pro Screen — gut für Workout-Flow. |
| Set-Typen | `SetType` (NORMAL/WARMUP/DROP_SET/FAILURE) seit kurzem modelliert; W/D/F-Präfixe in der Set-Nummer. |
| Haptik | `HapticFeedbackHelper` (tick/confirm/reject) existiert und ist bei Log/Delete/PR verdrahtet. |
| Bestehende Sheets | `PlanSelectionSheet`, `ExercisePickerSheet`, `ProgressionReview` nutzen `ModalBottomSheet` — Sheet-Pattern ist etabliert. |

### 2.2 Schwächen / Lücken (Audit-Befund, Grundlage für Concept 4)

| # | Problem | Beleg | Konzept-Antwort |
|---|---|---|---|
| A1 | **Keine Screen-Transitionen**: `NavHost` wechselt Screens ohne jede Animation; der Sprung Dashboard → Workout fühlt sich hart an. | `app/.../navigation/NavHost.kt` (keine Animations-APIs) | Spring-basierte Push/Fade-Transitionen + Shared-Element für Session-/Rekord-Übergänge |
| A2 | **Set-Zustände sind statisch**: `PendingSetRow` (Input-Boxen) wird durch `LoggedSetRow` (statische Boxen) ersetzt — ohne Morph, ohne Erfolgs-Feedback; der Checkmark „poppt" nicht. | `ActiveWorkoutScreen.kt` (`LoggedSetRow`, `PendingSetRow`) | **Morphing Set Card** (§6.1): Eingabe → Bestätigung → Logged in einem durchgängigen Card-Zustand |
| A3 | **PR-Feier fehlt**: Neuer Rekord = Snackbar-Text. Kein Moment, keine Freude, kein Wiedersehen im Feed. | `WorkoutEvent.NewRecord` → Snackbar in `ActiveWorkoutScreen` | **PR-Celebration-Overlay** (§6.4): Scale-Spring-Burst + Partikel + Triple-Haptic + Card-Zustand „PR!" |
| A4 | **Keine Wert-Stepper**: Reps/Gewicht/RPE werden nur per Tastatur eingegeben; keine Schnellschritte (±2.5kg, +1 Rep), kein Hold-to-Repeat. | `CompactTextField`-basierte Eingabe | **Bottom-Sheet-Quick-Stepper** (§6.2) mit Spring-Feedback und Suggest-Werten aus dem Plan |
| A5 | **Keine Gesten**: Loggen ist Tippen-auf-Check; keine Swipes trotz verticalem List-UI. | `Row.clickable` in `LoggedSetRow` | **Swipe-to-Log** (§6.3): rechts = Log, links = Fehlversuch, Drag mit Rubber-Band & Haptik |
| A6 | **Intensitätsfarben nur punktuell**: `rpeColor()` färbt nur die Intensitäts-Box; Card, Chip-Rahmen und Set-Typen bleiben neutral; keine durchgängige „Hitze"-Sprache. | `rpeColor()` in `ActiveWorkoutScreen.kt` | **Intensity Theming Layer** (§4): RPE-Rampen (Teal→Amber→Rose→Red), SetType-Farbcode, Card-Tint nach Session-Intensität |
| A7 | **Motion-Tokens rudimentär**: `IronLogMotion` kennt nur 130/220/300ms (tween-artig); keine Spring-Parameter, keine Choreographie-Regeln, `staggeredEntrance` nur auf Dashboard/History. | `ThemeTokens.kt` (`IronLogMotion`) | **Spring-Token-System** (§5): Stiffness/Damping je Rollen (Press, Confirm, Morph, Sheet, Celebrate) + Timing-Choreographie |
| A8 | **Loading-Feedback schlicht**: Shimmer-Skeleton vorhanden; keine Übergänge Daten→Inhalt (fade/slide). | `ShimmerSkeleton.kt` | Content-Entrance-Choreographie (skeleton → stagger fade) |

### 2.3 Gap-Check gegen Focus-Themen

| Focus-Thema | Status heute | Konzept |
|---|---|---|
| Fluid motion / Spring physics | 3× `AnimatedVisibility` mit Default-`spring()`, 2 Pulse-Loops | Vollständiges Spring-Token-System + Screen-Transitionen + State-Morphs |
| Morphing set cards | Nicht vorhanden | Kernkomponente §6.1 |
| Celebratory PR triggers | Snackbar | §6.4 Overlay |
| Bottom-sheet quick steppers | Nicht vorhanden | §6.2 |
| Swipe-to-log gestures | Nicht vorhanden | §6.3 |
| Dynamic intensity-based color theming | RPE-Farbe auf einem Box-Wert | §4.3 Layer + §6.1 Card-Tint |

---

## 3. Design-Principals

1. **„Das Gewicht ist das Ereignis"** — Die Workout-Session ist Bühne: Loggen ist eine sichtbare, spürbare Handlung, kein Formular-Absenden.
2. **Springs statt Tweens** — Federnde Übergänge für Zustandswechsel („Morph"), gedämpfte Kurven für Layout-Änderungen, ~0ms für Tastendruck-Feedback (sofortige Antwort, dann Feder).
3. **Moment halten, nie blockieren** — PR-Feier kurz (≤ 900ms), non-blocking, überspringbar; Animationen nie länger als die Pause zwischen Sätzen.
4. **Farbe = Zustand** — Intensität (RPE), Satztyp und Ermüdung werden über einen konsistenten Farbcode gesprochen; Rot ist Fehlversuch, nie Deko.
5. **Daumen-zentriert** — Primäraktionen im unteren Drittel, Stepper per Bottom-Sheet, Swipes im natürlichen Daumenradius.
6. **Reduced Motion ist Respekt** — Alle Spring- und Partikeleffekte haben einen `reduced`-Pfad (sofortige Zustandswechsel, nur Opazität).

---

## 4. Farbsystem / Color Palette

### 4.1 Base (Ember, unverändert übernommen)

| Token | Light | Dark | Verwendung |
|---|---|---|---|
| `Primary` | `#FF6B00` | `#FF9500` | Primäraktionen, aktive Chips, Log-Button |
| `PrimaryContainer` | `#FFD8B0` | `#5A2600` | Card-Highlight, Planziel-Boxen |
| `Secondary` (Teal) | `#0D9488` | `#14B8A6` | Erfolgskontext, „Ziel erreicht" |
| `Tertiary` (Violet) | `#7C3AED` | `#A78BFA` | Drop-Set, Meta-Plan-Akzent, Superset |
| `Background` / `Surface` | `#FFF8F0` / `#FFFFFF` | `#0C0806` / `#1A110C` | Flächen (Ember-Serie) |
| Semantik | Success `#166534`, Warning `#92400E`, Danger `#B3261E` | `#34D399`, `#FBBF24`, `#F87171` | Fortschritt, Hinweis, Fehler |
| Akzente | Rose `#E11D48`, Sky `#0284C7` | `#FB7185`, `#38BDF8` | Superset-Tint, Secondary-Muskel, Links |

### 4.2 Elevation & Glass (Ember-Stimmung)

| Token | Light | Dark | Nutzung |
|---|---|---|---|
| `GlassSurface` | Weiß @ 72 % + Blur 24dp + 1dp Border `#FFFFFF66` | Surface @ 60 % + Blur | Floating Cards, Sheet-Köpfe, Rest-Timer |
| `CardMuted` | `#FFF3E5` | `#22170F` | ExerciseCard-Hintergrund |
| `CardElevated` | `#FFFCF7` | `#2E2018` | ModalSheet, Stepper |

### 4.3 NEU: Intensity Theming Layer (dynamisch)

Kern von Concept 4: **Die Card „glüht" mit der Intensität der Session.** Ein `IntensityRamp`-Token-Satz ersetzt die punktuelle `rpeColor`-Färbung:

| RPE / RIR | Rolle | Color (Light) | Color (Dark) | Card-Tint Faktor |
|---|---|---|---|---|
| ≤ 6 / RIR≥4 | Zone 1 – Aufwärmen/Pendel | `EmberTeal` `#0D9488` | `#14B8A6` | 0 % (neutral) |
| 7 / RIR 3 | Zone 2 – Steady | `EmberSky` `#0284C7` | `#38BDF8` | Container-Tint 6 % |
| 8 / RIR 2 | Zone 3 – Hart | `EmberWarning` `#92400E` | `#FBBF24` | Container-Tint 10 % |
| 9 / RIR 1 | Zone 4 – Spitz | `EmberRose` `#E11D48` | `#FB7185` | Container-Tint 14 % |
| 10 / RIR 0 | Zone 5 – Am Limit | `EmberDanger` `#B3261E` | `#F87171` | Container-Tint 18 % + Glow-Border |

Regeln:
- **Border/Label** tragen die Zone-Farbe; der Card-Container bekommt nur einen **leichten Tint** (≤ 18 % Alpha) — Kontrast der Fläche bleibt erhalten.
- Die **Session-Intensität** = gewichteter Ø-RPE der NORMAL-Sätze; sie färbt den Fortschrittsbalken der Card und das „Hitze"-Dreieck am Card-Kopf.
- **SetType-Farbcode** (fester, nicht-intensitätsgebundener Layer): NORMAL = Primary, WARMUP = Muted/Grau (kursiv), DROP_SET = `EmberViolet`, FAILURE = `EmberDanger` — der Chip-Rahmen der geöffneten Eingabezeile trägt diesen Code.
- Kontrast: alle Zone-Farben erreichen WCAG AA ≥ 4.5:1 auf `EmberSurface` (Light) bzw. `EmberDarkSurface` (Dark); Faktoren oben sind reine Container-Tints (> 3:1 gegen Text #1A0F00 bestätigt).

---

## 5. Typografie-Specs

**Font:** Figtree (bleibt). **Ziffern:** `fontFeatureSettings = "tnum"` app-weit (bleibt). **Neu:** Numeric-Display-Stile für Score/PR/Metriken und eine leicht größere Body-Stufe für die Workout-Karte.

| Style | Font | Weight | Size / Line | Tracking | Nutzung |
|---|---|---|---|---|---|
| `displayLarge` | Figtree | Black | 44 / 48 | −1.5 | Score-Screen, PR-Celebration „102.5 kg" |
| `displayMedium` | Figtree | Black | 32 / 36 | −1.0 | Dashboard Kopfzahlen, PR-Badge |
| `headlineLarge` | Figtree | ExtraBold | 24 / 30 | −0.25 | Screen-Titel (Workout, History) |
| `headlineMedium` | Figtree | Bold | 20 / 26 | 0 | Card-Titel (ExerciseCard) |
| `titleLarge` | Figtree | Bold | 18 / 24 | 0 | Sheet-Titel, Stepper-Header |
| `titleMedium` | Figtree | SemiBold | 14 / 20 | 0 | Card-Subtitel, Chip-Labels — **neu: Stepper-Wert** |
| `bodyLarge` | Figtree | Normal | 14 / 20 | 0 | Set-Werte (Weight/Reps in der Card) |
| `bodyMedium` | Figtree | Normal | 12 / 16 | 0 | Statistik, Hinweise |
| `bodySmall` | Figtree | Normal | 11 / 14 | +0.1 | Timestamps, Sekundärinfos |
| `labelLarge` | Figtree | Medium | 12 / 16 | 0 | Buttons, Targets „3 × 8 × 100 kg" |
| `labelSmall` | Figtree | Medium | 9 / 12 | +0.2 | SetType-Chips, Unit-Suffixe |

**Zusätzliche Specs:**
- **Stepper-Ziffern:** `displaySmall` (26/32 Black, tnum) für den aktiven Wert im Stepper-Sheet — Zahl als Held, Units als `labelMedium`.
- **PR-Badge:** `labelLarge` Bold auf `displaySmall`-Wert — Kombination „PR!" + Wert in `displayMedium`.
- **Hit-Targets:** kompakte Zeilen bleiben (40dp Boxhöhe), aber interaktive Flächen (Swipe-Fläche, Stepper-Buttons) mindestens 48dp.
- **Locale:** `Locale.ROOT`-Formatierung für Zahlen im Animations-/Celebration-Kontext (bestehende Konvention bleibt).

---

## 6. Komponenten-Designs

### 6.1 Morphing Set Card (Kernkomponente)

**Zustände** einer Satzzeile in einer einzigen Card:

```
┌──────────────────────────────────────────┐
│  [1]  [ Chip: Normal|Aufwärmen|Drop|Fail ]  │  ← Kopf: SetNumber + SetType-Chip (farbcodiert)
│  ┌────────┬────────┬────────┐              │
│  │ 100 kg │  8    │ RPE 8  │  ⤴ Suggest   │  ← PENDING: Eingabe-Boxen (wie heute)
│  └────────┴────────┴────────┘              │
│       [ Loggen ]  (Primär-Button/Swipe)    │
└──────────────────────────────────────────┘
        │ Morph (Spring 1)
        ▼
┌──────────────────────────────────────────┐
│  [1] ✓   100 kg × 8  @RPE 8   Zone 3      │  ← LOGGED: Boxen kollabieren zu Wert-Zeile,
│  ────────── Fortschritt ▮▮▮▮▯ ──────────  │    Progress-Bar zur Ziel-Satzanzahl,
│  (Tipp = Editier-Modus, Swipe = Aktionen) │    Card-Tint nach Session-Intensität
└──────────────────────────────────────────┘
```

**Morph-Spezifikation** (`AnimatedContent` auf einem `SetCardState`-Enum):
| Phase | Property | Animation |
|---|---|---|
| Pending → Logged | Höhe der Eingabezeile | `animateDpAsState` spring `stiffness=500, damping=32` |
| — | Check-Symbol Zeichnen | `Animatable(0f→1f)` scale + drawPath stroke (120ms, `FastOutSlowIn`) |
| — | Boxen → Wert | `AnimatedVisibility` shrinkVertically + fade, 180ms, spring 400/30 |
| — | Fortschrittsbar | `animateFloatAsState` 0→Fertigung, spring 300/40 + `tick`-Haptik pro gefülltem Balken |
| Logged → Pending (Edit) | Umgekehrter Morph | gleiche Kurven, Richtung invertiert, `reject`-Haptik vermeiden (nur tick) |
| Neu-Log (2. Satz) | Card-Entrance | Y-Slide 24dp→0 + fade, `spring(stiffness=400, damping=28)`, 60ms Delay pro Insert |

**Ziel-Fortschritt:** Morphiert die „3 × 8"-Anzeige in einen **Segment-Balken** (3 Segmente); gefüllte Segmente = NORMAL-Sätze. WARMUP/DROP/FAILURE füllen keine Segmente (Progression zählt nur NORMAL — bestehende Semantik bleibt).

### 6.2 Bottom-Sheet Quick-Stepper

Ausgelöst durch Tipp auf einen Wert in der Pending-Card (oder Long-Press auf einen Logged-Wert).

```
┌──────────────────────────────────────────┐
│  Kniebeuge — Ziel 3 × 8 × 100 kg    [×] │  ← Sheet-Kopf (Glass, 24dp Radius oben)
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  │
│  │ Gewicht │  │  Reps   │  │  RPE    │  │  ← Segmente: eines aktiv, andere 40 % Alpha
│  │ ─ ▲ ─   │  │ ─ ▲ ─   │  │ ─ ▲ ─   │  │
│  │ 102.5   │  │   8     │  │  8.5    │  │  ← displaySmall Black, tnum
│  │ ─ ▼ ─   │  │ ─ ▼ ─   │  │ ─ ▼ ─   │  │
│  │[+2.5][-2.5]│ [ +1 ][-1] │ [0.5][-0.5]│  ← Step-Chips (Plan-Increment als Default)
│  └─────────┘  └─────────┘  └─────────┘  │
│        [ Übernehmen ]    [ Abbrechen ]   │
└──────────────────────────────────────────┘
```

**Motion/Haptik:** Step-Buttons `pressScale` (spring stiffness 1500, damping 18, 1.0→0.94→1.0) + `tick` je Schritt; Wert-Ziffer springt beim Ändern 1.15× (spring 1200/20) — „die Zahl atmet". Hold-to-Repeat ab 350ms (alle 90ms). Übernehmen = `confirm`-Haptik + Sheet-Extraktion über `ModalBottomSheet`-Spring; Werte kommen als Vorschläge aus dem Planziel (Reps, Gewicht, RPE-Target) und aus dem letzten Satz (historiebasiert).

**Änderungen vor Speichern sind lokal** — Stepper schreibt erst bei „Übernehmen" in den Input-State der Card (bestehende Logik unberührt).

### 6.3 Swipe-to-Log Gesten

Auf der Pending- und Logged-Card (horizontaler Drag, vertikal bleibt Scrollen der Liste):

| Geste | Aktion | Verhalten |
|---|---|---|
| Swipe rechts (≥ 96dp) | **Satz loggen** (NORMAL) | Card folgt Finger mit **Rubber-Band** (0.5× über Threshold), bei Release: Spring-Reset + Morph-Pending→Logged + `confirm`-Haptik |
| Swipe links (≥ 96dp) | **Fehlversuch loggen** (FAILURE) | gleiche Mechanik; Card-Tint wechselt zu Danger, SetType-Chip = Fehlversuch |
| Long-Press + Drag nach rechts | **Drop-Set** loggen | nach 350ms Long-Press wechselt der Chip zu Drop-Set (violett), Drag-Verhalten wie Swipe; minimale Verwechslungsgefahr durch Chip-Feedback |
| Kurzer Swipe (< Threshold) | Abort | Card federt zurück (`spring(600/22)`), kein Log, kein Haptic |
| Tipp auf Logged-Card | Editieren | Morph Logged → Pending (wie heute) |

**Technische Mapping:** `pointerInput` + `detectHorizontalDragGestures`; Drag-Offset in `Animatable` (nicht State-Fluss), damit 60fps ohne Recompose erreicht werden; Threshold-Breite = 96dp = Standard-Material-Swipe; Icon-Hint („⌄ zum Loggen") erscheint bei 24dp Drag mit Fade.

### 6.4 Celebratory PR-Trigger

**Auslöser:** `WorkoutEvent.NewRecord` (existiert bereits im ViewModel) mit RecordType (MAX_WEIGHT / MAX_REPS / MAX_VOLUME / MAX_E1RM).

**Verhalten (non-blocking, ≤ 900ms):**
```
  ┌─────────────────────────────┐
  │        ⭐ Burst (0–350ms)    │  6–8 Partikel (Zonenfarben nach RPE),
  │    „Neuer Rekord!"           │  radial aus der Card-Mitte, 24dp Radius,
  │      102.5 kg  (displayMed)  │  spring(stiffness=200, damping=14), dann Fade
  │                             └─ Glass-Card, scale 0.9→1.0 (spring 900/16)
  │  [ Teilen ]  [ Weiter ]      │  optional: Share-Intent im Marketing-Layout
  └─────────────────────────────┘
```
- **Timing:** Overlay erscheint 120ms nach Card-Morph, hält 700ms, verschwindet mit Fade 180ms; „Weiter" überspringt sofort. Rest-Timer startet erst nach Overlay-Ende (kein Doppel-Feedback).
- **Haptik:** Triple-Sequenz `confirm` (3× 60ms versetzt) — spürbar, aber diskret.
- **Kombination mit Deload-Fatigue:** Wenn der Deload-Assessment (`DeloadRepository`) eine Empfehlung liefert und ein PR trotzdem fällt, erscheint eine **„Doppelmeldung"**: Rekord-Badge + dezenter Hinweis „Trotzdem: Deload-Woche empfohlen" (keine zweite Feier).
- **Reduced Motion:** nur Badge-Scale (spring → 150ms tween), keine Partikel.

### 6.5 Intensitätsgeführte Flächen (Theming-Anwendung)

| Komponente | Intensity-Layer |
|---|---|
| ExerciseCard | Kopf-Dreieck („Hitze") = Session-Ø-RPE Zone; Container-Tint = Zone-Faktor (≤18 %) |
| Zieltext „3 × 8 × 100 kg" | Primary, es sei denn Ziel erreicht → Secondary (Teal) |
| LoggedSetRow-Werte | Box-Border = Zone-Farbe der RPE; FAILURE-Zeile: Danger-Tint + durchgestrichene Reps |
| Rest-Timer | Ring-Farbe = Zone des letzten Satzes; Countdown-Endpuls (spring 1.0→1.06) + tick in den letzten 3 s |
| Wochen-/Volumen-Charts | Balken färben pro Woche nach Ø-Intensität (Verlauf Teal→Amber→Rose) |
| Deload-Card | Warning/Amber (bestehend) + „Ermüdungswert" als animierter Ring (animateFloatAsState, spring 400/30) |

### 6.6 StatCards, Buttons, Chips (Motion-Delta)

- **StatCard:** Wert zählt hoch (`animateIntAsState` mit spring 500/26, 400ms), Entrance im Dashboard-Stagger (bestehend `staggeredEntrance` bleibt, auf 6ms/Index).
- **Primär-Button:** `pressScale` (0.96×, stiffness 1500/18); **Chips:** Auswahl-Spring (Inhalt scale 1.1→1.0, stiffness 1000/24) + tick.
- **Sheet (alle):** Entrance = Slide + Fade mit `spring(stiffness=400, damping=30)` statt Default-Tween; Drag-Handle großzügig (44dp).

---

## 7. Screen-Layouts

### 7.1 Dashboard („Der Morgen-Blick")

```
┌──────────────────────────────────┐
│ Willkommen zurück        [⚙]     │  TopBar
│ ─────────────────────────────── │
│ [▶ Jetzt trainieren]            │  CommandCenterCard, Pulse nur für Erstnutzer (wie heute)
│ [Deload-Card?]                  │  optional, Ring-Ermüdungswert (spring)
│ [2 Progressions-Vorschläge →]   │  bestehend
│ ┌──────────┐ ┌──────────┐       │
│ │ Diese Wo │ │ Dieser M │       │  StatCards mit Count-up
│ └──────────┘ └──────────┘       │
│ [ Muskel-Heatmap ]              │  ✓ bestehend
│ Rekorde:  ▸ ▸ ▸ (LazyRow)       │  RecordCards mit PR-Tint (Zone-Farbe des Rekords)
│ [ Volumen KW ▮▮▮▮▯ ]            │  Balken färben nach Ø-Intensität
└──────────────────────────────────┘
```
**Motion:** Screen-Transition Fade+Slide (24dp, spring 400/30, 60ms); Inhalts-Stagger 6ms/Index; Charts-Werte animieren beim Erscheinen.

### 7.2 Active Workout („Die Bühne")

```
┌──────────────────────────────────┐
│  Push Day            ⏱ 24:13   × │  TopBar + WorkoutTimer
│ [Rest-Timer Glow]               │  Ring in Zone-Farbe
│ [ + Übung hinzufügen ]          │
│ ┌─ Kniebeuge  ▰▰▰▯ 3/4  🔥Z3 ─┐ │  ExerciseCard: Hitze-Dreieck + Segmentbar
│ │ [1] 100kg 8 RPE8  [Loggen]   │ │  Pending (Chip Normal) — Swipe/Hotkeys
│ │ [2] ✓ 100kg×8 @RPE8    ✎ ✕  │ │  Logged, Zone-3-Tint
│ │ [3] ✓ 102.5kg×8 @RPE8   ✎ ✕  │ │
│ │ [ W2 80kg×5 ]                │ │  WARMUP-Zeile muted/kursiv
│ │ [ D3 60kg×12 ]               │ │  DROP_SET violett
│ │ [ ⟳ Extra-Satz ]             │ │  SetType-Chips in der Extra-Zeile
│ └──────────────────────────────┘ │
└──────────────────────────────────┘
```
**Interaktionen:** Tipp auf Wert → Stepper-Sheet; Swipe rechts = Log, links = Fehlversuch; Morph-Animationen bei jedem Zustandswechsel; „Ziel erreicht"-Teal-Puls auf Segmentbar.

### 7.3 History & Workout Detail

- History-Liste: Card-Entrance vom bestehenden Stagger (bleibt); Pull-to-refresh mit Spring-Feder; Zeilen-Swipe nicht aktiv (bewusst, Konflikt mit Listennavigation) — stattdessen Schnellaktion „Wiederholen" per Icon.
- Detail-Screen: Session-Zusammenfassung als **Score-Card** (Volumen/E1RM/Ø-RPE in displayMedium mit Count-up); Set-Liste nutzt dieselbe Morphing-Card im Read-only-Zustand; PR-Sets tragen dauerhaft einen kleinen Rekord-Stern.

### 7.4 Statistics & Plans

- ExerciseStats: Datenpunkte treten mit Stagger + spring auf; Linie zeichnet sich (drawPath 400ms, spring 500/30); Achsenwerte in tnum.
- Plans: Card-Morph beim Umschalten „Manuell ↔ Automatisch" (bestehende Sheet-Flow bleibt); Plan-Karten nutzen Chip-Springs.

---

## 8. Implementierungs-Mapping (was wo anfasst)

| Konzept | Compose-APIs | Dateien (Ist) |
|---|---|---|
| Spring-Tokens | `SpringSpec`-Konstanten in `IronLogMotion` (erweitern), `animate*AsState`, `Animatable` | `core/designsystem/.../theme/ThemeTokens.kt`, `Interactions.kt` |
| Screen-Transitionen | `NavHost` + `AnimatedContent`/`enterTransition` (fade+spring slide), SharedElement via `SharedTransitionLayout` | `app/.../navigation/NavHost.kt` |
| Morphing Set Card | `AnimatedContent` (State-Enum), `animateDpAsState`, `drawWithCache` Check-Haken | `feature/workout/.../ActiveWorkoutScreen.kt` (`PendingSetRow`/`LoggedSetRow` → `SetCard`) |
| Stepper-Sheet | `ModalBottomSheet` + `Slider`-Alternativen, Hold-to-Repeat (`LaunchedEffect` + delay) | neues `feature/workout/.../SetValueStepperSheet.kt` |
| Swipe-to-Log | `detectHorizontalDragGestures`, `Animatable` Drag-Offset, Rubber-Band-Formel | `ActiveWorkoutScreen.kt` (Card-Modifier) |
| PR-Celebration | Overlay-`Box` + `Canvas`-Partikel, `spring`, Haptic-Triple | `ActiveWorkoutScreen.kt` + `workout_new_record_message`-Ersatz; ViewModel `WorkoutEvent` bereits vorhanden |
| Intensity Layer | `@Composable fun rpeZone(rpe): IntensityZone` (Token), Card-Tint-Desugars | `core/designsystem/.../theme/Color.kt` (neue Rampen), `ActiveWorkoutScreen.kt` |
| Deload-Ring | `animateFloatAsState` | `feature/dashboard/.../DashboardScreen.kt` (`DeloadCard`) |

**Nicht angefasst:** `core:database`, `core:model` (außer ggf. Lesen), `data`, ViewModels (nur falls Events erweitert werden, z. B. Share-Intent), Test-Erwartungen an Snackbar-Texten (PR-Test in `ActiveWorkoutViewModelTest` prüft Events, nicht UI — unkritisch).

**Rollout-Phasen (Vorschlag):**
1. **P0 Foundation:** Spring-Tokens + Reduced-Motion-Pfade + Intensity-Ramp (reine Token-Erweiterung, kein Verhalten).
2. **P1 Workout:** Morphing Set Card + Swipe-to-Log + SetType-Chips-Farben (Kernnutzen sofort spürbar).
3. **P2 Sheets & PR:** Stepper-Sheet + PR-Celebration + Haptik-Sequenzen.
4. **P3 Shell & Screens:** NavHost-Transitionen, Dashboard/Stats-Entrances, Deload-Ring.

---

## 9. Accessibility & Reduced Motion

- `IronLogMotion.reduced` (bestehend) wird Pflicht-Check für: Partikel, Count-up, Card-Pulse, Stagger (> 60ms), Stepper-Ziffern-Spring; Ersatz: ≤ 150ms-Transparenz-Wechsel.
- Alle Gesten haben Tipp-Äquivalente (Swipe-Rechts = Loggen-Button bleibt sichtbar; Long-Press-Drop = Chip-Auswahl).
- Kontrast: Zone-Farben AA auf Card-Flächen; Card-Tints ≤ 18 % Alpha; Text nie in Zone-Farbe unter 4.5:1.
- Haptik nur mit `HapticFeedbackHelper` (Android-Standard), nicht für reine Dekoration; „Tick"-Volumen nicht mit „Confirm" kombinieren (Reizüberflutung).
- Touch-Targets: Stepper-Buttons ≥ 48dp; Swipe-Threshold 96dp (Material-Konvention); keine Gesten, die Scrollen blockieren (vertikale Erkennung gewinnt bei > 12dp vertikalem Offset).

---

## 10. Akzeptanzkriterien (Checkliste für die Umsetzung)

- [ ] Jeder Zustandswechsel einer Set-Card verwendet eine Spring-Kurve aus dem Token-Set; kein `tween` > 300ms ohne Token.
- [ ] PR-Celebration erscheint ≤ 900ms, blockiert das Loggen nicht, ist bei `reducedMotion` ein reiner Badge.
- [ ] Swipe-to-Log funktioniert ohne Scroll-Konflikt; fehlgeschlagene Swipes federn zurück ohne Log.
- [ ] Stepper übernehmen Plan-Increment (z. B. +2.5 kg) als Default-Schritt; Werte sind vor „Übernehmen" lokal.
- [ ] Farbcode: FAILURE = Danger, DROP_SET = Violet, WARMUP = Muted/Ikursiv, NORMAL = Primary — konsistent auf Cards, Chips und History.
- [ ] NavHost hat Transitionen; alle Screens respektieren `reducedMotion`.
- [ ] Bestehende Unit-Tests bleiben grün (Event- und State-Logik unverändert); neue Tests: Spring-Token-Referenzen, Intensity-Zone-Mapping, Swipe-Threshold-Logik (reine Funktion), Stepper-Schrittberechnung.

---

## 11. Offene Fragen (für Review)

1. **PR-Teilung:** Share-Intent im Celebration-Overlay erwünscht (Achtung: Datenschutz — Session-Daten)? Default: kein Teilen, nur Anzeige.
2. **Swipe + Editier-Altlast:** Long-Press-Drag für Drop-Set kann mit dem bestehenden „Tipp = Editieren" kollidieren — soll Drop-Set stattdessen nur über den Chip wählbar sein (Swipe links bleibt FAILURE-Schnellweg)?
3. **Zehn-Finger-Regel:** Sollen die Stepper ein Drittes Segment „RIR" (statt RPE) anbieten, wenn IntensitySystem.RIR aktiv ist? (Vorschlag: Ja, Segment-Label folgt dem aktiven System.)
4. **Ziel-Segmentbalken bei Deload-Modus:** Bei aktivem Deload (`HALVE_SET_VOLUME`) zeigt der Balken 2 von 2 Segmenten — Soll der Balken das reduzierte Ziel (ja) oder das Planziel (nein) spiegeln? (Vorschlag: reduziertes Ziel, da es die Anzeige-Ebene ist.)