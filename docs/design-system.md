# Design-System („Ember“)

Stand des Codes in `core/designsystem`. Das Ember-Redesign ist umgesetzt. Es hat das frühere kühl-blaue Glassmorphism-Design abgelöst. Die späteren Designkonzepte Forge, Raster, Tide und Pulse sind als zusätzliche Farbschemata eingeflossen. Diese Datei beschreibt, was **jetzt** gilt, nicht den Weg dorthin.

## Farbschemata

In den Einstellungen wählbar (`ThemeScheme`). Dazu kommen Hell, Dunkel oder System (`ThemeMode`, Standard: **Dunkel**) und optional Dynamic Color ab Android 12.

| Schema | Primär (hell / dunkel) | Sekundär | Tertiär | Flächen |
|---|---|---|---|---|
| **Amber** (Standard) | `#FF6B00` / `#F58B20` | Teal `#0D9488` | Violett `#7C3AED` | hell: Creme `#FFF8F0`, dunkel: Blauschwarz `#0E131A` |
| **Deep Cyan** | `#00838F` / `#4DD0E1` | Blaugrau | Pink | neutral-kühl |
| **Neon Red** | `#D50000` / `#FF5252` | Grau | Gelb | neutral-kühl |
| **Forge** | Molten Orange `#FF7A1A` | Anthrazit `#2E2E34` | Grün `#3DFF88` | Industrial, dunkel fast Schwarz `#060606` |
| **Raster** | Kobalt `#3B82F6` | Slate `#64748B` | Smaragd `#10B981` | Swiss/Bento, dunkel `#08090A` |
| **Tide** | Mint `#00F5A0` | Cyan `#06B6D4` | Indigo `#818CF8` | Recovery, dunkel Grünschwarz `#040908` |
| **Pulse** | Rose `#FF3366` | Violett `#A855F7` | Cyan `#06B6D4` | dunkel `#0B080C` |

Die Farben liegen in `theme/Color.kt`, die Zuordnung zu Material-3-Schemata in `theme/Theme.kt`. Die Systemleisten passen sich dem gewählten Design an.

## Semantische Farben

Über `MaterialTheme.semantic` (`EmberSemanticColors` in `theme/ThemeTokens.kt`). Sie sind in allen drei Schemata gleich:

- `success` Grün, `warning` Amber, `danger` Rot
- Akzente: `rose`, `sky`, `violet`, `teal` (jeweils mit hellerer Variante)

Beispiel **RPE-Badge** im aktiven Workout: bis 7 grün, bis 8 amber, bis 9 rose, 10 rot.

## Typografie

Eine Schriftfamilie: **Figtree** (`res/font/figtree.ttf` und `figtree_italic.ttf`), definiert in `theme/Type.kt`.

Für Kennzahlen gibt es zusätzlich `AthleticHero`, `AthleticNumber` (beide mit Tabellenziffern `tnum`) und `AthleticLabel`, genutzt auf dem Dashboard und im aktiven Workout.

## Tokens

In `theme/DesignTokens.kt` und `theme/ThemeTokens.kt`:

- `Radius` (xs 6 dp … xxl 28 dp, `pill`), `IconSize`, `ButtonSize`
- `MaterialTheme.ironLogDimens`, `.ironLogMotion`, `.ironLogSurfaceRoles`
- Wer „reduzierte Animationen“ einschaltet, bekommt kürzere bzw. keine Bewegungen (`ironLogMotion`).

Neue Abstände und Größen gehören in die Tokens, nicht als feste `dp`-Werte in die Screens.

## Oberflächen

- `Modifier.glassmorphism()` (`theme/Glassmorphism.kt`): halbtransparente Karte mit Tönung in der Primärfarbe des aktiven Schemas und Glanzkante. Dazu `Modifier.glow()`.
- `IronLogSurfaceCard` mit den Tönen `ELEVATED`, `MUTED`, `ACCENT`, `COLORED`
- `StatCard` mit den Varianten `PRIMARY`, `SECONDARY`, `TERTIARY`. Kacheln einer Gruppe nutzen dieselbe Variante und gleiche Höhe (Übungsstatistik: Standardvariante in `IntrinsicSize.Min`-Zeilen).
- `IronLogScreenScaffold`: gemeinsames Gerüst für alle Screens

## Liquid Glass (im Aufbau)

Zweite Darstellung neben Ember, wählbar unter Einstellungen → Darstellung. Standard bleibt Ember, bis alle Screens umgestellt sind (Plan: [`plans/2026-09-26-liquid-glass.md`](plans/2026-09-26-liquid-glass.md)).

- Glasstufen: `GlassLevel.STANDARD`, `STRONG`, `TINT` (Android, `theme/LiquidGlass.kt`, `Modifier.liquidGlass`) bzw. `IronLogGlassLevel` (iOS, `Design/IronLogLiquidGlass.swift`, `.liquidGlass(_:in:)`).
- Nur `STRONG` weichzeichnet den Hintergrund: Android über Haze ab Android 12, iOS über `.ultraThinMaterial`. Darunter, bei reduzierten Animationen und bei „Transparenz reduzieren“ (iOS) gibt es eine fast deckende Tönung.
- Der farbige Hintergrund (`LiquidBackground` / `IronLogLiquidBackground`) besteht aus radialen Verläufen in der Akzentfarbe, Türkis und Violett und wird einmal hinter der App gezeichnet (Android: `LiquidGlassHost` in `MainActivity`).
- `IronLogSurfaceCard`, `Modifier.glassmorphism()` und `IronLogCard` schalten automatisch um; Ember-Karten bleiben unverändert.
- Navigation: Android zeigt statt der `NavigationBar` eine schwebende Glas-Pille (`BottomNavBar`, Stufe `STRONG`); der aktive Tab ist ein heller Chip mit Icon und Text, die übrigen Tabs zeigen nur das Icon. iOS behält die System-Tableiste, auf iOS 26 ist sie selbst Liquid Glass.
- iOS-Screens setzen ihren Hintergrund über `.ironLogScreenBackground(ember:emberNavigationBar:)` innerhalb des obersten `NavigationStack`. In Liquid Glass blendet der Modifier die System-Hintergründe von Listen und Formularen aus, zeichnet `IronLogLiquidBackground` und macht Navigations- und Tableiste zu Material; in Ember bleiben die bisherigen Farben.

## Zahlen und Texte

Gilt für Android und iOS gleich:

- Deutsches Zahlenformat: „82,5 kg“, „12.305 kg“, nie „80.0“. Android: `WeightFormatting` (Eingabefelder ohne Tausenderpunkt über `formatInputNumber`). iOS: `iosWorkoutDecimal`, `IOSNumber.format`, `ilWeightText`.
- Sätze einheitlich als „82,5 kg × 8 Wdh“. 0 kg wird als „Körpergewicht“ angezeigt.
- Mengen mit passender Einzahl/Mehrzahl („1 Training“, „3 Trainings“). Android: `plurals`-Ressourcen. iOS: `ilCount(_:_:_:)`.
- Umlaute in allen Anzeigenamen, keine zusätzlichen Häkchen oder Pfeile in Button-Texten. Unbekannte Werte (z. B. Satzabsicht „Nicht angegeben“) werden nicht als Daten angezeigt.
- Links unter Kartentext stehen bündig mit dem Text (`TextButton` um die Innenpolsterung versetzt).

## Gemeinsame Komponenten (`presentation/common`)

`SetInputRow`, `CompactTextField`, `IronLogTextField`, `RestTimer`, `WorkoutTimer`, `PlateVisualizer`, `WeeklyMuscleVolumeCard`, `EmptyStateScreen`, `LoadingScreen`, `ShimmerSkeleton`, `SharedTransition`, `HapticFeedback`, `ErrorUiMapper`.

**Scheibenfarben:** `PlateColors` in `theme/Color.kt` folgt der üblichen Kodierung (25 kg rot, 20 kg blau, 15 kg gelb, 10 kg grün, 5 kg weiß, kleinere Scheiben grau/silber).

## App-Hülle

- Splash-Screen (`Theme.IronLog.Splash`): Hintergrund `#0C0806`, Icon-Hintergrund `#FF6B00`. Er bleibt sichtbar, bis die Design-Einstellungen geladen sind. So gibt es keinen weißen Blitz.
- Adaptives App-Icon mit monochromer Variante (`app/src/main/res/drawable/ic_launcher_*`).

## Texte

UI-Texte gehören in `core/designsystem/src/main/res/values/strings.xml` (Deutsch) oder in die `res/values/*_strings.xml` des jeweiligen Feature-Moduls, nicht als Literal in Composables. Ausnahme bisher: `WorkoutCompletionScreen` enthält noch Literale.
