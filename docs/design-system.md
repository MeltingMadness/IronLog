# Design-System („Ember“)

Stand des Codes in `core/designsystem`. Das Ember-Redesign ist umgesetzt. Es hat das frühere kühl-blaue Glassmorphism-Design abgelöst. Diese Datei beschreibt, was **jetzt** gilt, nicht den Weg dorthin.

## Farbschemata

In den Einstellungen wählbar (`ThemeScheme`). Dazu kommen Hell, Dunkel oder System (`ThemeMode`, Standard: **Dunkel**) und optional Dynamic Color ab Android 12.

| Schema | Primär (hell / dunkel) | Sekundär | Tertiär | Flächen |
|---|---|---|---|---|
| **Amber** (Standard) | `#FF6B00` / `#FF9500` | Teal `#0D9488` | Violett `#7C3AED` | warm: Creme `#FFF8F0` (hell), warmes Schwarz `#0C0806` (dunkel) |
| **Deep Cyan** | `#00838F` / `#4DD0E1` | Blaugrau | Pink | neutral-kühl |
| **Neon Red** | `#D50000` / `#FF5252` | Grau | Gelb | neutral-kühl |

Die Farben liegen in `theme/Color.kt`, die Zuordnung zu Material-3-Schemata in `theme/Theme.kt`. Die Systemleisten passen sich dem gewählten Design an.

## Semantische Farben

Über `MaterialTheme.semantic` (`EmberSemanticColors` in `theme/ThemeTokens.kt`). Sie sind in allen drei Schemata gleich:

- `success` Grün, `warning` Amber, `danger` Rot
- Akzente: `rose`, `sky`, `violet`, `teal` (jeweils mit hellerer Variante)

Beispiel **RPE-Badge** im aktiven Workout: bis 7 grün, bis 8 amber, bis 9 rose, 10 rot.

## Typografie

Eine Schriftfamilie: **Figtree** (`res/font/figtree.ttf` und `figtree_italic.ttf`), definiert in `theme/Type.kt`.

## Tokens

In `theme/DesignTokens.kt` und `theme/ThemeTokens.kt`:

- `Radius` (xs 6 dp … xxl 28 dp, `pill`), `IconSize`, `ButtonSize`
- `MaterialTheme.ironLogDimens`, `.ironLogMotion`, `.ironLogSurfaceRoles`
- Wer „reduzierte Animationen“ einschaltet, bekommt kürzere bzw. keine Bewegungen (`ironLogMotion`).

Neue Abstände und Größen gehören in die Tokens, nicht als feste `dp`-Werte in die Screens.

## Oberflächen

- `Modifier.glassmorphism()` (`theme/Glassmorphism.kt`): halbtransparente Karte mit Tönung in der Primärfarbe des aktiven Schemas und Glanzkante. Dazu `Modifier.glow()`.
- `IronLogSurfaceCard` mit den Tönen `ELEVATED`, `MUTED`, `ACCENT`, `COLORED`
- `StatCard` mit den Varianten `PRIMARY`, `SECONDARY`, `TERTIARY`
- `IronLogScreenScaffold`: gemeinsames Gerüst für alle Screens

## Gemeinsame Komponenten (`presentation/common`)

`SetInputRow`, `CompactTextField`, `IronLogTextField`, `RestTimer`, `WorkoutTimer`, `EmptyStateScreen`, `LoadingScreen`, `ShimmerSkeleton`, `SharedTransition`, `HapticFeedback`, `ErrorUiMapper`.

## App-Hülle

- Splash-Screen (`Theme.IronLog.Splash`): Hintergrund `#0C0806`, Icon-Hintergrund `#FF6B00`. Er bleibt sichtbar, bis die Design-Einstellungen geladen sind. So gibt es keinen weißen Blitz.
- Adaptives App-Icon mit monochromer Variante (`app/src/main/res/drawable/ic_launcher_*`).

## Texte

UI-Texte gehören in `core/designsystem/src/main/res/values/strings.xml` (Deutsch), nicht als Literal in Composables.
