# IronLog "Ember" Frontend Redesign — Vollständiger Implementierungsplan

> **Status**: Bereit zur Implementierung
> **Erstellt**: 2026-03-22
> **Fortschritt**: Phase 1–6 noch nicht begonnen

---

## Kontext

IronLog ist eine Workout-Tracking Android App (Kotlin, Jetpack Compose, Material 3). Das aktuelle Design verwendet ein kalt-blaues Glassmorphism-Theme. Ziel ist eine **visuelle Transformation** hin zu einem warmen, energetischen "Ember"-Look — ohne den technischen Unterbau (Database, Models, Repositories, ViewModels) zu verändern.

**Ziel**: App soll sowohl im Portfolio/App Store beeindrucken als auch im täglichen Gebrauch ein Premium-Erlebnis bieten.

### Design-Entscheidungen (User-approved)
- Warme Amber/Orange-Primärfarbe mit Teal, Violet, Rose, Sky als Akzentfarben
- Glassmorphism **bleibt**, wird aber warm getönt
- Typografie (Plus Jakarta Sans + DM Sans) bleibt unverändert
- RPE-Logging und Previous-Session-Daten bleiben erhalten
- Mockups: `.superpowers/brainstorm/1874-1774205146/` (ember-palette-v2, ember-components-v2, ember-screens, ember-workout-v2)

---

## Abhängigkeiten zwischen Phasen

```
Phase 1 (Foundation) ─── muss ZUERST fertig sein
    │
    ├── Phase 2 (Components) ─── braucht Phase 1
    │       │
    │       ├── Phase 4 (Dashboard) ─── braucht Phase 2
    │       ├── Phase 5 (Workout) ─── braucht Phase 2
    │       └── Phase 6 (Rest) ─── braucht Phase 2
    │
    └── Phase 3 (Nav + Shell) ─── braucht nur Phase 1, PARALLEL zu Phase 2 möglich
```

---

## Nicht verändert (bestätigt)

- `core/database/` — komplett unberührt
- `core/model/` — komplett (ThemeScheme Enum bleibt)
- `data/` — komplett unberührt
- Alle `*ViewModel.kt` Dateien
- `Type.kt` — Typografie bleibt
- Test-Dateien (außer ggf. UI-Screenshot-Tests)

---

## Phase 1: Design System Foundation

**Ziel**: Alle Farben, Tokens und Theme-Infrastruktur auf Ember umstellen. Danach rendert die gesamte App bereits in Ember-Farben, auch ohne Screen-Level-Änderungen.

**Geschätzter Aufwand**: M (Medium)

---

### 1.1 — `Color.kt` aktualisieren

**Datei**: `core/designsystem/src/main/java/com/ironlog/app/presentation/theme/Color.kt`

**Amber-Primärschema** (ersetze alle bisherigen Primary/Secondary/Tertiary):

```kotlin
// === EMBER PRIMARY — Warm Amber/Orange ===
val EmberPrimary = Color(0xFFFF6B00)        // Haupt-CTA, aktive Elemente
val EmberPrimaryLight = Color(0xFFFF9500)   // Hover/Pressed-State
val EmberPrimaryDark = Color(0xFFCC5500)    // Dark-Mode-Variante
val OnEmberPrimary = Color(0xFFFFFFFF)      // Text auf Primary

// === EMBER SECONDARY — Teal ===
val EmberTeal = Color(0xFF0D9488)
val EmberTealLight = Color(0xFF14B8A6)
val OnEmberTeal = Color(0xFFFFFFFF)

// === EMBER TERTIARY — Violet ===
val EmberViolet = Color(0xFF7C3AED)
val EmberVioletLight = Color(0xFFA78BFA)
val OnEmberViolet = Color(0xFFFFFFFF)

// === EMBER ACCENT — Rose ===
val EmberRose = Color(0xFFE11D48)
val EmberRoseLight = Color(0xFFFB7185)

// === EMBER ACCENT — Sky ===
val EmberSky = Color(0xFF0284C7)
val EmberSkyLight = Color(0xFF38BDF8)

// === EMBER SEMANTIC ===
val EmberSuccess = Color(0xFF34D399)        // Geloggte Sets, Erfolg
val EmberDanger = Color(0xFFF87171)         // Delete-Aktionen
val EmberWarning = Color(0xFFFBBF24)        // Timer-Warnungen, RPE 9

// === DARK SURFACES (warm getönt, ersetzt kalt-blaue) ===
val EmberSurfaceDark0 = Color(0xFF0C0806)   // War: #0E1117 — tiefstes Schwarz
val EmberSurfaceDark1 = Color(0xFF1A110C)   // War: #12161E — Karten-Hintergrund
val EmberSurfaceDark2 = Color(0xFF251A12)   // War: #1A202A — erhöhte Surfaces
val EmberSurfaceDark3 = Color(0xFF2E2018)   // Noch höhere Elevation

// === LIGHT SURFACES (warmes Cremeweiß) ===
val EmberSurfaceLight0 = Color(0xFFFFF8F0)  // War: #F2F4F8 — Page Background
val EmberSurfaceLight1 = Color(0xFFFFFFFF)  // War: #F7F9FC — Karten
val EmberSurfaceLight2 = Color(0xFFFFF0E0)  // War: #FCFDFF — erhöhte Surfaces

// === ON-COLORS (für Kontrast auf Surfaces) ===
val OnSurfaceDark = Color(0xFFF5E6D3)       // Haupttext auf Dark (warm-weiß)
val OnSurfaceDarkMuted = Color(0xFFB89880)  // Sekundärtext auf Dark
val OnSurfaceLight = Color(0xFF1A0F00)      // Haupttext auf Light
val OnSurfaceLightMuted = Color(0xFF6B4A2A) // Sekundärtext auf Light
```

**Dark Surfaces Mapping** (alle bisherigen kalt-blauen Surface-Farben ersetzen):

| Alt (kalt-blau) | Neu (warm-dunkel) |
|-----------------|-------------------|
| `#0E1117` | `#0C0806` |
| `#12161E` | `#1A110C` |
| `#1A202A` | `#251A12` |

**Light Surfaces Mapping**:

| Alt | Neu |
|-----|-----|
| `#F2F4F8` | `#FFF8F0` |
| `#F7F9FC` | `#FFFFFF` |
| `#FCFDFF` | `#FFF0E0` |

---

### 1.2 — `DesignTokens.kt` erweitern

**Datei**: `core/designsystem/src/main/java/com/ironlog/app/presentation/theme/DesignTokens.kt`

**Neue Radien** (alle bestehenden erhöhen für wärmere Anmutung):

```kotlin
object Radius {
    val xs = 6.dp      // war: 4.dp
    val sm = 10.dp     // war: 8.dp
    val md = 14.dp     // war: 12.dp
    val lg = 18.dp     // war: 16.dp
    val xl = 24.dp     // war: 20.dp
    val xxl = 28.dp    // war: 24.dp
    val pill = 999.dp  // NEU: für Tags/Badges/Chips
}
```

**Neue Icon-Tokens** (bisher hardcoded):

```kotlin
object IconSize {
    val sm = 18.dp   // Small icons (Info, Chevron)
    val md = 22.dp   // Standard icons
    val lg = 28.dp   // Hero icons, FAB-Icons
    val xl = 36.dp   // Empty-State-Illustrationen
}
```

**Neue Button-Tokens**:

```kotlin
object ButtonSize {
    val height = 48.dp      // Standard-Button
    val heightSm = 36.dp    // Kompakte Buttons (z.B. in Listen)
    val heightXs = 28.dp    // Micro-Buttons (Tags, Chips)
    val iconButton = 40.dp  // Icon-only Buttons
    val minWidth = 120.dp   // Mindestbreite für Text-Buttons
}
```

**Neue semantische Farben als Data Class + CompositionLocal**:

```kotlin
data class EmberSemanticColors(
    val success: Color,
    val danger: Color,
    val warning: Color,
    val rose: Color,
    val roseLight: Color,
    val sky: Color,
    val skyLight: Color,
    val violet: Color,
    val violetLight: Color,
    val teal: Color,
    val tealLight: Color,
)

val LocalEmberSemanticColors = compositionLocalOf {
    EmberSemanticColors(
        success = EmberSuccess,
        danger = EmberDanger,
        warning = EmberWarning,
        rose = EmberRose,
        roseLight = EmberRoseLight,
        sky = EmberSky,
        skyLight = EmberSkyLight,
        violet = EmberViolet,
        violetLight = EmberVioletLight,
        teal = EmberTeal,
        tealLight = EmberTealLight,
    )
}
```

---

### 1.3 — `ThemeTokens.kt` + `Theme.kt`

**Datei `ThemeTokens.kt`**:

- Accessor für neue semantische Farben hinzufügen:
  ```kotlin
  val MaterialTheme.semantic: EmberSemanticColors
      @Composable
      @ReadOnlyComposable
      get() = LocalEmberSemanticColors.current
  ```

**Datei `Theme.kt`**:

- Alle 6 ColorScheme-Objekte mit neuen Ember-Werten aufbauen (Dark/Light × 3 Themes: Ember, Cool, Neutral)
- `deriveSurfaceRoles()` für warme Dynamic-Color-Ableitung anpassen:
  - Surface-Tint-Farbe von kalt-blau → warm-amber
  - `surfaceColorAtElevation()` liefert warm getönte Farben
- Statische `IronLogSurfaceRoles` mit neuen Surface-Farben
- `CompositionLocalProvider` um `LocalEmberSemanticColors` erweitern

**ColorScheme Dark (Ember-Theme)**:
```kotlin
val emberDarkColorScheme = darkColorScheme(
    primary = EmberPrimary,
    onPrimary = OnEmberPrimary,
    primaryContainer = Color(0xFF3D1A00),
    onPrimaryContainer = Color(0xFFFFD8B0),
    secondary = EmberTeal,
    onSecondary = OnEmberTeal,
    secondaryContainer = Color(0xFF003832),
    onSecondaryContainer = Color(0xFFB0F0EB),
    tertiary = EmberViolet,
    onTertiary = OnEmberViolet,
    tertiaryContainer = Color(0xFF2D0070),
    onTertiaryContainer = Color(0xFFD8BFFF),
    background = EmberSurfaceDark0,
    onBackground = OnSurfaceDark,
    surface = EmberSurfaceDark1,
    onSurface = OnSurfaceDark,
    surfaceVariant = EmberSurfaceDark2,
    onSurfaceVariant = OnSurfaceDarkMuted,
    error = EmberDanger,
    onError = Color.White,
)
```

**ColorScheme Light (Ember-Theme)**:
```kotlin
val emberLightColorScheme = lightColorScheme(
    primary = EmberPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD8B0),
    onPrimaryContainer = Color(0xFF3D1A00),
    secondary = EmberTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB0F0EB),
    onSecondaryContainer = Color(0xFF003832),
    tertiary = EmberViolet,
    onTertiary = Color.White,
    background = EmberSurfaceLight0,
    onBackground = OnSurfaceLight,
    surface = EmberSurfaceLight1,
    onSurface = OnSurfaceLight,
    surfaceVariant = EmberSurfaceLight2,
    onSurfaceVariant = OnSurfaceLightMuted,
    error = EmberRose,
    onError = Color.White,
)
```

---

### 1.4 — `Glassmorphism.kt` warm tönen

**Datei**: `core/designsystem/src/main/java/com/ironlog/app/presentation/theme/Glassmorphism.kt`

**Änderungen**:

```kotlin
// BASE TINT: War Color.White → Warm Amber
val glassTint = Color(0xFFFFB464).copy(alpha = 0.05f)

// GRADIENT: War white/black → Amber warm
val glassGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFF9500).copy(alpha = 0.04f),  // Amber oben
        Color.Transparent,                        // transparent unten
    )
)

// BORDER: War white 28% → Amber 8%
val glassBorder = Color(0xFFFFB464).copy(alpha = 0.08f)

// SPECULAR: War weißer Diagonalstrahl → warmer Top-Edge-Glow
val glassSpecular = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFD080).copy(alpha = 0.12f),  // Top-Edge warm
        Color.Transparent,
    ),
    start = Offset(0f, 0f),
    end = Offset(0f, 60f),  // Nur obere Kante, kein Diagonalstrahl
)
```

---

### 1.5 — `Interactions.kt`

**Datei**: `core/designsystem/src/main/java/com/ironlog/app/presentation/theme/Interactions.kt`

```kotlin
// pressScale: War 0.93f → weniger aggressiver Scale für Premium-Feel
val pressScale = 0.96f  // war: 0.93f
```

---

### Verifikation Phase 1

- [ ] App auf Emulator starten, alle 3 Themes (Ember, Cool, Neutral) + Light/Dark prüfen
- [ ] Warmes Cremeweiß `#FFF8F0` als Page-Background (Light-Mode) verifizieren
- [ ] Warmes Schwarz `#0C0806` als Background (Dark-Mode) verifizieren
- [ ] Glassmorphism-Karten zeigen warmem Amber-Tint (nicht mehr kalt-blau)
- [ ] WCAG AA Kontrast-Check: `OnSurfaceDark` auf `EmberSurfaceDark1` ≥ 4.5:1
- [ ] Dynamic Color auf Android 12+ Gerät/Emulator testen
- [ ] `./gradlew assembleDebug` läuft ohne Fehler

---

## Phase 2: Shared Components

**Ziel**: Wiederverwendbare Komponenten an Ember-Sprache anpassen.

**Geschätzter Aufwand**: S–M

---

### 2.1 — `IronLogSurfaceCard.kt`

**Datei**: `core/designsystem/src/main/java/com/ironlog/app/presentation/components/IronLogSurfaceCard.kt`

`IronLogSurfaceTone` Enum erweitern:

```kotlin
enum class IronLogSurfaceTone {
    DEFAULT,    // Standard-Glassmorphism
    ELEVATED,   // Stärkerer Hintergrund
    ACCENT,     // NEU: Hero-Karte mit Primary-Tint (stärkeres Glassmorphism)
    COLORED,    // NEU: Semantisch gefärbt (akzeptiert semanticColor Parameter)
}
```

**ACCENT-Variante**:
- Stärkeres Glassmorphism: `alpha = 0.10f` statt `0.05f`
- Primary-Amber als Tint: `Color(0xFFFF6B00).copy(alpha = 0.08f)`
- Border: `Color(0xFFFF9500).copy(alpha = 0.20f)`

**COLORED-Variante**:
- Neuer Parameter: `semanticColor: Color? = null`
- Wenn gesetzt: `semanticColor.copy(alpha = 0.08f)` als Background-Tint
- Border: `semanticColor.copy(alpha = 0.15f)`

---

### 2.2 — `StatCard.kt`

**Datei**: `core/designsystem/src/main/java/com/ironlog/app/presentation/components/StatCard.kt`

Farb-Differenzierung nach `StatCardType`:

```kotlin
enum class StatCardType { PRIMARY, SECONDARY, TERTIARY }

// PRIMARY → Teal (Hauptmetriken: Workouts, Volumen)
// SECONDARY → Violet (Sekundärmetriken: PRs, Strähne)
// TERTIARY → Sky (Informationsmetriken: Durchschnitt, etc.)

val statColor = when (type) {
    StatCardType.PRIMARY -> MaterialTheme.semantic.teal
    StatCardType.SECONDARY -> MaterialTheme.semantic.violet
    StatCardType.TERTIARY -> MaterialTheme.semantic.sky
}
```

Icon-Farbe = `statColor`, Icon-Background = `statColor.copy(alpha = 0.12f)`

---

### 2.3 — `CompactTextField.kt` + `IronLogTextField.kt`

**Dateien**:
- `core/designsystem/src/main/java/com/ironlog/app/presentation/components/CompactTextField.kt`
- `core/designsystem/src/main/java/com/ironlog/app/presentation/components/IronLogTextField.kt`

**Warme Flame-Border im Focus-State**:

```kotlin
val focusBorderColor = when {
    isFocused -> Color(0xFFFF6B00)  // Ember Primary
    isError -> MaterialTheme.semantic.danger
    else -> MaterialTheme.colorScheme.outline
}
```

**Hardcoded dp → Design Tokens ersetzen**:
- `height = 48.dp` → `ButtonSize.height`
- `height = 36.dp` → `ButtonSize.heightSm`
- `cornerRadius = 8.dp` → `Radius.sm`
- `cornerRadius = 12.dp` → `Radius.md`

---

### 2.4 — `RestTimer.kt`

**Datei**: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/components/RestTimer.kt`

```kotlin
// Default-Farbe → Sky-Blue statt Primary
val timerColor = MaterialTheme.semantic.sky       // war: MaterialTheme.colorScheme.primary
val timerTrackColor = MaterialTheme.semantic.sky.copy(alpha = 0.20f)

// Hardcoded dp → Tokens
// size(64.dp) → IconSize.xl * 1.8f o.ä.
// Padding(16.dp) → DesignTokens.spacingMd
```

---

### 2.5 — `WorkoutTimer.kt`, `SetInputRow.kt`, `EmptyStateScreen.kt`

**Hardcoded Größen → neue Tokens**:

```kotlin
// WorkoutTimer.kt
// size(24.dp) → IconSize.md
// size(20.dp) → IconSize.sm

// SetInputRow.kt
// size(28.dp) → IconSize.lg - 0.dp (oder IconSize.lg)
// size(20.dp) → IconSize.sm
// height(40.dp) → ButtonSize.iconButton

// EmptyStateScreen.kt
// size(80.dp) → IconSize.xl * 2.2f (oder neuer Token IconSize.xxl)
// size(48.dp) → IconSize.xl
```

---

### Verifikation Phase 2

- [ ] Alle 4 Karten-Varianten (DEFAULT, ELEVATED, ACCENT, COLORED) visuell prüfen
- [ ] Stat-Cards: PRIMARY in Teal, SECONDARY in Violet, TERTIARY in Sky
- [ ] Text-Felder: Focus-Border in Ember-Orange (nicht mehr blau)
- [ ] Rest-Timer: Kreisring in Sky-Blue
- [ ] `./gradlew assembleDebug` läuft ohne Fehler

---

## Phase 3: Navigation + App Shell

**Kann parallel zu Phase 2 starten (braucht nur Phase 1).**

**Geschätzter Aufwand**: M

---

### 3.1 — `BottomNavBar.kt`

**Datei**: `core/designsystem/src/main/java/com/ironlog/app/presentation/components/BottomNavBar.kt`

- Glassmorphism wird automatisch warm durch Phase 1
- Indicator-Color auf Ember-Flame anpassen:
  ```kotlin
  // Aktiver Indikator: Primary statt bisheriger Farbe
  indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
  activeIconColor = MaterialTheme.colorScheme.primary
  inactiveIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f)
  ```

---

### 3.2 — `MainActivity.kt` Ambient Glow

**Datei**: `app/src/main/java/com/ironlog/app/MainActivity.kt`

- Globaler Hintergrund-Gradient wird automatisch warm (Primary = Ember)
- Ggf. Alpha des Ambient-Glow leicht erhöhen: `0.05f` → `0.08f` für wärmeres Gefühl

---

### 3.3 — Splash Screen hinzufügen

**Dependency** in `app/build.gradle.kts`:
```kotlin
implementation("androidx.core:core-splashscreen:1.0.1")
```

**`values/themes.xml`** (bzw. `values-v31/themes.xml` für API 31+):
```xml
<style name="Theme.IronLog.Splash" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/ember_surface_dark</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
    <item name="windowSplashScreenIconBackgroundColor">@color/ember_primary</item>
    <item name="postSplashScreenTheme">@style/Theme.IronLog</item>
</style>
```

**`colors.xml`** Einträge hinzufügen:
```xml
<color name="ember_surface_dark">#0C0806</color>
<color name="ember_primary">#FF6B00</color>
```

**`AndroidManifest.xml`**: Activity-Theme auf `Theme.IronLog.Splash` setzen.

**`MainActivity.kt`**:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()  // VOR super.onCreate()
    super.onCreate(savedInstanceState)
    // ...
}
```

---

### 3.4 — App Icon redesignen

**Dateien**:
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`

**`ic_launcher_foreground.xml`**: Dumbbell-Icon mit Ember-Flame-Farbe:
```xml
<!-- Dumbbell in Ember-Orange statt bisheriger Farbe -->
<path android:fillColor="#FF6B00" android:pathData="..." />
```

**`ic_launcher_background.xml`**: Warmer Gradient statt Flat-Color:
```xml
<gradient
    android:startColor="#1A110C"
    android:endColor="#2E1A00"
    android:type="linear"
    android:angle="135" />
```

**`ic_launcher_monochrome.xml`**: Gleiche Form, weiß für Adaptive Icons.

---

### Verifikation Phase 3

- [ ] App-Start: Splash Screen mit warmem Ember-Branding sichtbar
- [ ] Bottom Nav: warmes Glassmorphism, aktiver Tab in Ember-Orange
- [ ] App Icon im Launcher zeigt Ember-Farben
- [ ] App Icon in Recents korrekt
- [ ] `./gradlew assembleDebug` läuft ohne Fehler

---

## Phase 4: Dashboard Screen

**Geschätzter Aufwand**: L

---

### 4.1 — `DashboardScreen.kt`

**Datei**: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardScreen.kt`

**Persönliche Begrüßungszeile** (basierend auf Tageszeit):

```kotlin
@Composable
fun GreetingHeader(userName: String) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "Guten Morgen"
        in 12..17 -> "Guten Tag"
        in 18..21 -> "Guten Abend"
        else -> "Noch wach"
    }
    // Stil: Großes, warmes "Guten Morgen, Max" mit Sekundärtext
}
```

**Streak-Card** (neue ACCENT-Variante):

```kotlin
// IronLogSurfaceCard mit tone = IronLogSurfaceTone.ACCENT
// Inhalt:
// - Wochenkalender als Mini-Dots (Mo–So), gefüllt/leer
// - Progress-Bar: "5 / 7 Tage" mit Ember-Primary-Farbe
// - Streak-Zahl groß, Icon: Flamme (wenn streak > 0)
```

**CommandCenterCard** (Start-Workout-CTA):

```kotlin
// IronLogSurfaceCard mit tone = IronLogSurfaceTone.ACCENT
// Großer CTA-Button in Ember-Primary
// Sekundärtext: Letztes Workout / Vorgeschlagener Plan
```

**StatCards**: Farb-Differenzierung aus Phase 2.2 anwenden:
- Workouts Diese Woche → PRIMARY (Teal)
- Gesamtvolumen → PRIMARY (Teal)
- Aktuelle Strähne → SECONDARY (Violet)
- Persönliche Bestleistungen → SECONDARY (Violet)

**Hardcoded dp ersetzen**:
- `44.dp` → `ButtonSize.iconButton + 4.dp`
- `140.dp` → prüfen, ggf. behalten wenn Screen-spezifisch
- `120.dp` → prüfen, ggf. behalten wenn Screen-spezifisch
- Alle `size(x.dp)` für Icons → `IconSize.*`

---

### 4.2 — `MuscleHeatmapCard.kt`

**Datei**: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/components/MuscleHeatmapCard.kt`

```kotlin
// Max-Intensitäts-Farbe: EmberRose statt Primary-abgeleitet
val maxIntensityColor = MaterialTheme.semantic.rose  // war: colorScheme.primary-Ableitung

// Heatmap-Gradient (3 Stufen):
val heatmapColors = listOf(
    Color(0xFF34D399),  // Niedrig: EmberSuccess (Grün)
    Color(0xFFFF9500),  // Mittel: EmberPrimaryLight (Orange)
    Color(0xFFE11D48),  // Hoch: EmberRose (Rot)
)
// Interpolation je nach Intensitätswert
```

---

### 4.3 — `WeeklyVolumeCard.kt`

**Datei**: `feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/components/WeeklyVolumeCard.kt`

```kotlin
// Vico-Library-Farben aktualisieren
// Chart-Linienfarbe → Teal
val lineColor = MaterialTheme.semantic.teal  // war: colorScheme.primary

// Fill unter der Linie → Teal mit Alpha-Verlauf
val lineFill = ShaderProvider.verticalGradient(
    arrayOf(
        MaterialTheme.semantic.teal.copy(alpha = 0.30f),
        MaterialTheme.semantic.teal.copy(alpha = 0.00f),
    )
)
```

---

### Verifikation Phase 4

- [ ] Begrüßungszeile zeigt korrekte Tageszeit-abhängige Anrede
- [ ] Streak-Card: Kalender-Dots und Progress-Bar korrekt
- [ ] CommandCenterCard mit Ember-Flame-Styling
- [ ] StatCards farblich differenziert (Teal/Violet)
- [ ] Heatmap: Grün → Orange → Rose Farbverlauf
- [ ] Volume-Chart: Teal-Linie
- [ ] `./gradlew assembleDebug` läuft ohne Fehler

---

## Phase 5: Active Workout Screen

**Datei**: `feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt`

**Geschätzter Aufwand**: L (höchste Komplexität, ~1030 Zeilen)

---

### 5.1 — Hardcoded dp ersetzen (ca. 30+ Stellen)

Systematisch alle Instanzen ersetzen:

```kotlin
// Icons
size(28.dp) → size(IconSize.lg)
size(24.dp) → size(IconSize.md)
size(20.dp) → size(IconSize.sm)
size(18.dp) → size(IconSize.sm)

// Buttons
size(40.dp) → size(ButtonSize.iconButton)
height(48.dp) → height(ButtonSize.height)
height(36.dp) → height(ButtonSize.heightSm)

// Radien
shape = RoundedCornerShape(8.dp) → RoundedCornerShape(Radius.sm)
shape = RoundedCornerShape(12.dp) → RoundedCornerShape(Radius.md)
shape = RoundedCornerShape(16.dp) → RoundedCornerShape(Radius.lg)
```

---

### 5.2 — Ember Semantic Coloring

**Supersets — Violet-Tint**:
```kotlin
// War: Primary-Color-Cycling für verschiedene Supersets
// Neu: Violet-Tint für alle Superset-Gruppen
val supersetColor = MaterialTheme.semantic.violet
val supersetBackground = MaterialTheme.semantic.violet.copy(alpha = 0.10f)
val supersetBorder = MaterialTheme.semantic.violet.copy(alpha = 0.25f)
```

**Geloggte Sätze — Success-Grün**:
```kotlin
// Abgehakter/geloggter Satz: Grüner Checkmark-Indikator
val loggedSetIndicator = MaterialTheme.semantic.success
val loggedSetBackground = MaterialTheme.semantic.success.copy(alpha = 0.08f)
```

**RPE-Badges mit dynamischer Farbcodierung**:
```kotlin
fun rpeColor(rpe: Int): Color = when (rpe) {
    6, 7 -> MaterialTheme.semantic.success      // Grün: Leicht/Moderat
    8 -> MaterialTheme.colorScheme.primary      // Amber: Fordernd
    9 -> MaterialTheme.semantic.warning         // Orange: Sehr hart
    10 -> MaterialTheme.semantic.rose           // Rose: Maximum
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
}

// Badge-Hintergrund = rpeColor(rpe).copy(alpha = 0.15f)
// Badge-Text = rpeColor(rpe)
// Badge-Form: RoundedCornerShape(Radius.pill) für capsule-Form
```

**Delete-Aktionen**:
```kotlin
val deleteColor = MaterialTheme.semantic.danger
// Swipe-to-delete Hintergrund: danger.copy(alpha = 0.15f)
// Delete-Icon: danger
```

**Timer-Warnungen**:
```kotlin
// Wenn Restzeit < 10 Sek: Farbe wechselt zu EmberWarning
val timerWarningColor = MaterialTheme.semantic.warning
```

---

### 5.3 — Previous Session Styling

**Aufklappbare Box** mit warmem Muted-Styling:

```kotlin
// Hintergrund: surfaceVariant mit leichtem Amber-Tint
// Border: onSurface.copy(alpha = 0.08f) — sehr subtil
// Text-Farbe: onSurface.copy(alpha = 0.60f) — gedimmt, klar sekundär
// Header: "Letzte Session" mit Icon, tap zum Aufklappen
// Expansion-Animation: animateContentSize()
// Visuell: Klar vom aktiven Input abgegrenzt, aber nicht störend
```

---

### Verifikation Phase 5

- [ ] Hardcoded dp auf < 5 Restinstanzen reduziert (Screen-spezifische Größen OK)
- [ ] Supersets: Violet-Hintergrund und Border sichtbar
- [ ] RPE 6: Grün-Badge, RPE 8: Amber-Badge, RPE 9: Orange-Badge, RPE 10: Rose-Badge
- [ ] Geloggte Sätze: Grüner Indikator
- [ ] Previous-Session: aufklappbar, visuell gedimmt und getrennt
- [ ] Rest-Timer: Sky-Blue (aus Phase 2.4)
- [ ] Delete-Swipe: EmberDanger-Rot
- [ ] `./gradlew assembleDebug` läuft ohne Fehler

---

## Phase 6: Übrige Feature Screens

**Geschätzter Aufwand**: M

---

### 6.1 — `WorkoutHistoryScreen.kt` + `WorkoutDetailScreen.kt`

**Dateien**:
- `feature/history/src/main/java/com/ironlog/app/presentation/history/WorkoutHistoryScreen.kt`
- `feature/history/src/main/java/com/ironlog/app/presentation/history/WorkoutDetailScreen.kt`

- Workout-Karten: DEFAULT-Glassmorphism (wird automatisch warm durch Phase 1)
- **PR-Badges**: Violet-Akzent statt bisheriger Farbe:
  ```kotlin
  val prBadgeColor = MaterialTheme.semantic.violet
  val prBadgeBackground = MaterialTheme.semantic.violet.copy(alpha = 0.15f)
  // Form: RoundedCornerShape(Radius.pill)
  // Text: "PR" oder "PB" in violetLight
  ```
- Datum-Text: `onSurface.copy(alpha = 0.60f)`

---

### 6.2 — `ExerciseLibraryScreen.kt`

**Datei**: `feature/exercises/src/main/java/com/ironlog/app/presentation/exercises/ExerciseLibraryScreen.kt`

- Suchleiste: Warmer Focus-Border (aus Phase 2.3 übernommen)
- **Filter-Chips**:
  ```kotlin
  // Aktiver Chip: Primary-Ember-Hintergrund
  // Inaktiver Chip: surfaceVariant + onSurface-Text
  selectedChipColor = MaterialTheme.colorScheme.primary
  selectedChipTextColor = MaterialTheme.colorScheme.onPrimary
  ```
- Übungs-Karten: DEFAULT-Glassmorphism
- Muskelgruppen-Tags: `Radius.pill`, `onSurface.copy(alpha = 0.08f)` Background

---

### 6.3 — `ExerciseStatsScreen.kt`

**Datei**: `feature/exercises/src/main/java/com/ironlog/app/presentation/exercises/ExerciseStatsScreen.kt`

- Chart-Farben auf Ember-Palette:
  - Fortschrittslinie: Primary (Ember)
  - Volumenlinie: Teal
- **PR-Marker**: Violet-Punkt auf der Chart-Linie
- Max-Gewicht-Anzeige: Primary-Farbe hervorgehoben

---

### 6.4 — `TrainingPlanListScreen.kt` + `PlanEditorScreen.kt`

**Dateien**:
- `feature/plans/src/main/java/com/ironlog/app/presentation/plans/TrainingPlanListScreen.kt`
- `feature/plans/src/main/java/com/ironlog/app/presentation/plans/PlanEditorScreen.kt`

- Warme Karten (automatisch durch Phase 1)
- Input-Felder (automatisch durch Phase 2.3)
- Plan-Status-Badge (Aktiv/Inaktiv):
  ```kotlin
  val activeBadgeColor = MaterialTheme.semantic.success
  val inactiveBadgeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
  ```

---

### 6.5 — `MetaPlanListScreen.kt` + `MetaPlanEditorScreen.kt`

**Dateien**:
- `feature/plans/src/main/java/com/ironlog/app/presentation/plans/MetaPlanListScreen.kt`
- `feature/plans/src/main/java/com/ironlog/app/presentation/plans/MetaPlanEditorScreen.kt`

- **Violet-Akzent** für Meta-Plan-Elemente (Meta-Pläne sind übergeordnete Strukturen):
  ```kotlin
  // Meta-Plan Icon: Violet
  // Meta-Plan Karten-Akzent: IronLogSurfaceTone.COLORED + semanticColor = violet
  val metaPlanAccent = MaterialTheme.semantic.violet
  ```

---

### 6.6 — `SettingsScreen.kt`

**Datei**: `feature/settings/src/main/java/com/ironlog/app/presentation/settings/SettingsScreen.kt`

- **Theme-Selector**: 3 Chips mit Ember/Cool/Neutral-Preview-Farben:
  ```kotlin
  // Ember-Chip: kleines Orange-Quadrat als Preview
  // Cool-Chip: kleines Blau-Quadrat
  // Neutral-Chip: kleines Grau-Quadrat
  // Aktiver Chip: Ember-Primary-Border + leichter Hintergrund
  ```
- Switches/Toggles: Track-Farbe wenn aktiviert = Primary (Ember)
- Section-Headers: `onSurface.copy(alpha = 0.50f)` — subtil

---

### 6.7 — `ExercisePickerSheet.kt` + `PlanSelectionSheet.kt`

**Dateien**:
- Suche nach Bottom-Sheet-Dateien in `feature/*/components/`

- Bottom-Sheet: Warmes Glassmorphism (automatisch durch Phase 1)
- **Handle-Bar**: `onSurface.copy(alpha = 0.20f)` — warm-getönt
- Suchfeld: Warmer Focus-Border (aus Phase 2.3)
- Liste: DEFAULT-Karten, ausgewähltes Item in `primary.copy(alpha = 0.12f)`

---

### Verifikation Phase 6

- [ ] History: PR-Badges in Violet
- [ ] Library: Filter-Chips in Ember, Suchleiste mit warmem Focus
- [ ] Stats: Chart in Ember/Teal, PR-Marker in Violet
- [ ] Plans: Warme Karten, Aktiv-Badge in Grün
- [ ] Meta-Plans: Violet-Akzent
- [ ] Settings: Theme-Selector mit Farb-Preview, Switches in Ember
- [ ] Bottom Sheets: Warmes Glassmorphism
- [ ] Jeden Screen in Light + Dark + alle 3 Themes prüfen

---

## End-to-End Verifikation (nach allen Phasen)

1. **Build**: `./gradlew assembleDebug` muss ohne Fehler durchlaufen
2. **Visuell**: Jeden der 12 Screens in Dark + Light + alle 3 Themes prüfen
3. **Funktional**:
   - Workout starten
   - Sätze loggen
   - RPE-Werte eingeben (6, 7, 8, 9, 10)
   - Supersets erstellen
   - Rest-Timer starten und warten
   - Previous-Session aufklappen
4. **Accessibility**:
   - Reduced Motion toggle testen
   - Content Descriptions vorhanden
   - WCAG AA Kontrast für alle Text/Background-Kombinationen
5. **Dynamic Color**: Android 12+ Gerät/Emulator testen
6. **Splash Screen**: Kaltstart der App prüfen
7. **App Icon**: Im Launcher + Recents prüfen

---

## Risiken und Mitigationen

| Risiko | Impact | Mitigation |
|--------|--------|------------|
| Kontrast-Ratios auf warmen Surfaces | Hoch | WCAG AA Check nach Phase 1, vor Phase 2 fortfahren |
| ActiveWorkoutScreen Regression (1030 Zeilen) | Hoch | Schrittweise vorgehen: erst dp-Ersatz, dann Farben, dann neue Features |
| Glassmorphism wirkt trüb auf LCD-Displays | Mittel | Alpha-Werte auf verschiedenen Geräten prüfen, ggf. reduzieren |
| Dynamic Color überschreibt Ember | Mittel | `deriveSurfaceRoles()` anpassen und auf Android 12 testen |
| Vico Chart-API Änderungen | Niedrig | API-Kompatibilität verifizieren vor Änderung |

---

## Wichtige Dateipfade (Referenz)

```
core/
  designsystem/src/main/java/com/ironlog/app/presentation/theme/
    Color.kt                    ← Phase 1.1
    DesignTokens.kt             ← Phase 1.2
    ThemeTokens.kt              ← Phase 1.3
    Theme.kt                    ← Phase 1.3
    Glassmorphism.kt            ← Phase 1.4
    Interactions.kt             ← Phase 1.5
  designsystem/src/main/java/com/ironlog/app/presentation/components/
    IronLogSurfaceCard.kt       ← Phase 2.1
    StatCard.kt                 ← Phase 2.2
    CompactTextField.kt         ← Phase 2.3
    IronLogTextField.kt         ← Phase 2.3
    BottomNavBar.kt             ← Phase 3.1

app/
  src/main/java/com/ironlog/app/
    MainActivity.kt             ← Phase 3.2, 3.3
  src/main/res/
    drawable/ic_launcher_foreground.xml   ← Phase 3.4
    drawable/ic_launcher_background.xml   ← Phase 3.4
    drawable/ic_launcher_monochrome.xml   ← Phase 3.4
    values/themes.xml                     ← Phase 3.3
    values-v31/themes.xml                 ← Phase 3.3
    values/colors.xml                     ← Phase 3.3

feature/
  dashboard/src/main/java/com/ironlog/app/presentation/dashboard/
    DashboardScreen.kt          ← Phase 4.1
    components/MuscleHeatmapCard.kt  ← Phase 4.2
    components/WeeklyVolumeCard.kt   ← Phase 4.3
  workout/src/main/java/com/ironlog/app/presentation/workout/
    ActiveWorkoutScreen.kt      ← Phase 5
    components/RestTimer.kt     ← Phase 2.4
    components/WorkoutTimer.kt  ← Phase 2.5
    components/SetInputRow.kt   ← Phase 2.5
  history/...
    WorkoutHistoryScreen.kt     ← Phase 6.1
    WorkoutDetailScreen.kt      ← Phase 6.1
  exercises/...
    ExerciseLibraryScreen.kt    ← Phase 6.2
    ExerciseStatsScreen.kt      ← Phase 6.3
  plans/...
    TrainingPlanListScreen.kt   ← Phase 6.4
    PlanEditorScreen.kt         ← Phase 6.4
    MetaPlanListScreen.kt       ← Phase 6.5
    MetaPlanEditorScreen.kt     ← Phase 6.5
  settings/...
    SettingsScreen.kt           ← Phase 6.6

.superpowers/brainstorm/1874-1774205146/
  ember-palette-v2.html        ← Referenz: Farb-Tokens
  ember-components-v2.html     ← Referenz: Component-Specs
  ember-screens.html           ← Referenz: Dashboard + Workout Mockups
  ember-workout-v2.html        ← Referenz: Enhanced Workout Screen
```

---

## Fortschritts-Tracker

| Phase | Status | Notizen |
|-------|--------|---------|
| 1.1 Color.kt | ✅ Erledigt | |
| 1.2 DesignTokens.kt | ✅ Erledigt | |
| 1.3 ThemeTokens + Theme | ✅ Erledigt | |
| 1.4 Glassmorphism.kt | ✅ Erledigt | |
| 1.5 Interactions.kt | ✅ Erledigt | |
| 2.1 IronLogSurfaceCard | ✅ Erledigt | |
| 2.2 StatCard | ✅ Erledigt | |
| 2.3 TextFields | ✅ Erledigt | |
| 2.4 RestTimer | ✅ Erledigt | |
| 2.5 WorkoutTimer/SetInputRow/EmptyState | ✅ Erledigt | |
| 3.1 BottomNavBar | ✅ Erledigt | |
| 3.2 MainActivity Glow | ✅ Erledigt | |
| 3.3 Splash Screen | ✅ Erledigt | |
| 3.4 App Icon | ✅ Erledigt | |
| 4.1 DashboardScreen | ✅ Erledigt | |
| 4.2 MuscleHeatmapCard | ✅ Erledigt | |
| 4.3 WeeklyVolumeCard | ✅ Erledigt | |
| 5.1 ActiveWorkout dp-Ersatz | ✅ Erledigt | |
| 5.2 ActiveWorkout Ember Colors | ✅ Erledigt | |
| 5.3 Previous Session Styling | ✅ Erledigt | |
| 6.1 History Screens | ✅ Erledigt | |
| 6.2 Exercise Library | ✅ Erledigt | |
| 6.3 Exercise Stats | ✅ Erledigt | |
| 6.4 Training Plans | ✅ Erledigt | |
| 6.5 Meta Plans | ✅ Erledigt | |
| 6.6 Settings | ✅ Erledigt | |
| 6.7 Bottom Sheets | ✅ Erledigt | |
