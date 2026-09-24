# Meta-Pläne und Trainingsstart

Ein **Meta-Plan** ist eine Rotation über mehrere Trainingspläne (Teilpläne), z. B. Push → Pull → Beine. Die App schlägt jeweils den nächsten Teilplan vor.

## Training starten

Im Dialog „Training starten“ (Home) stehen **Meta-Pläne oben**, darunter die normalen Pläne. Innerhalb der Gruppen bleibt die gewohnte Sortierung.

Für jeden Meta-Plan zeigt der Dialog den vorgeschlagenen Teilplan mit der Aktion **„Überspringen“**.

## Rotation

- Pro Teilplan zählt das **jüngste Ereignis**: der Start des letzten abgeschlossenen Trainings oder das letzte Überspringen.
- Vorgeschlagen wird der Teilplan mit dem **ältesten** Ereignis. Ein Teilplan ohne Ereignis kommt vor allen bereits genutzten.
- Bei Gleichstand entscheidet die Reihenfolge im Meta-Plan.

## Überspringen

- Speichert ein Rotationsereignis (`meta_plan_skips`) und wechselt sofort zum nächsten Teilplan.
- Erzeugt **kein** Training. Statistik und Verlauf bleiben unverändert.
- Bei nur einem Teilplan ist die Aktion sichtbar, aber deaktiviert.
- Die Aktion ist gegen Doppeltippen geschützt. Eine Transaktion prüft, ob der Vorschlag noch aktuell ist. Ist er veraltet, wird nichts gespeichert und die Liste neu geladen.

## Gewichtshistorie nach Kontext

Einstellung **„Gewichte zwischen Einzel- und Meta-Plänen teilen“**, Standard: **aus**.

| Einstellung | Training gestartet … | Frühere Trainings, die als Historie dienen |
|---|---|---|
| Aus | als normaler Plan | gleicher Plan, ohne Meta-Plan |
| Aus | innerhalb eines Meta-Plans | gleicher Plan im **selben** Meta-Plan |
| An | beliebig | gleicher Plan, egal in welchem Kontext |

Dieselbe Historie liefert die Gewichtshinweise im aktiven Workout und den Erfolgsindikator.

## Erfolgsindikator im Workout

Eine geplante Übung bekommt einen positiven Hinweis, wenn im letzten relevanten Training der **letzte Nicht-Aufwärmsatz** das **aktuelle** Zielgewicht und die aktuellen Ziel-Wiederholungen erreicht oder überschritten hat. Freie Workouts und Übungen ohne vollständiges Ziel zeigen keinen Indikator.

## Backup

Überspringen-Ereignisse sind Teil des Backups. Ältere Backups ohne dieses Feld importieren mit einer leeren Liste.

## Code

- Rotation und Überspringen: `MetaTrainingPlanRepository` / `MetaTrainingPlanDao`
- Startdialog: `feature/dashboard/.../PlanSelectionSheet.kt`
- Verwaltung: `feature/plans/.../MetaPlan*`
- Tests (Emulator): `app/src/androidTest/.../MetaPlanSkipDaoTest.kt`, `WorkoutSetDaoContextScopeTest.kt`
