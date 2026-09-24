import SwiftUI

/// Dashboard controls for the shared deload preference.
///
/// The recommendation comes from the shared training-trend assessment
/// (`deloadSuggested`/`deloadActive` plus the core's own reasons). This card only
/// presents that evidence and persists the user's chosen display mode; it never
/// changes a plan, a target weight, or a set count. The display adjustment itself
/// stays in the shared `SharedDeloadTargetAdjustment` helper used by the workout
/// screen.
struct IOSDashboardDeloadCard: View {
    let trend: ILTrainingTrendAssessment
    let activeMode: String
    let onActivate: (String) -> Void
    let onDeactivate: () -> Void

    private var isActive: Bool {
        activeMode.uppercased() != "NONE" && !activeMode.isEmpty
    }

    var body: some View {
        IronLogCard(
            title: isActive ? "Deload-Woche aktiv" : "Deload aus dem Trainingstrend",
            subtitle: subtitle,
            tone: .muted
        ) {
            VStack(alignment: .leading, spacing: 10) {
                if isActive {
                    activeBody
                } else {
                    recommendationBody
                }

                actionRow
            }
        }
        .accessibilityElement(children: .contain)
    }

    @ViewBuilder
    private var recommendationBody: some View {
        if trend.deloadSuggested {
            Text("Mehrere Übungen verschlechtern sich wiederholt. Das ist eine Trainingsheuristik aus deinen protokollierten Sätzen, keine medizinische Einschätzung.")
                .font(.body)
                .foregroundStyle(.secondary)
        } else {
            Text("Der Trend zeigt aktuell keine wiederholte Verschlechterung über mehrere Übungen. Du kannst eine Deload-Anzeige trotzdem bewusst starten.")
                .font(.body)
                .foregroundStyle(.secondary)
        }

        if let decline = trend.strongestDecline, let change = decline.changePercent {
            Text("Stärkster Rückgang: \(decline.exerciseName) (\(percentText(change)) über \(decline.comparableUnitCount) vergleichbare Einheiten)")
                .font(.subheadline.weight(.semibold))
        }

        // A deload decision is only as trustworthy as its evidence, so this focused card
        // lists every distinct reason instead of cutting the list off behind an
        // unreachable count.
        let evidence = ilReasonTexts(trend.reasons)
        if !evidence.isEmpty {
            VStack(alignment: .leading, spacing: 5) {
                ForEach(evidence, id: \.self) { text in
                    Label(text, systemImage: "circle.fill")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var activeBody: some View {
        Text(activeModeText)
            .font(.body)
            .foregroundStyle(.secondary)
    }

    private var actionRow: some View {
        HStack(alignment: .center, spacing: 10) {
            if isActive {
                Label("Deload aktiv", systemImage: "checkmark.circle.fill")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.green)

                Spacer(minLength: 8)

                Button("Deload beenden", role: .destructive, action: onDeactivate)
                    .buttonStyle(.bordered)
                    .accessibilityHint("Setzt den Deload-Modus auf normale Trainingsziele zurück")
            } else {
                Button("Volumen halbieren") {
                    onActivate("HALVE_SET_VOLUME")
                }
                .buttonStyle(.borderedProminent)
                .accessibilityHint("Zeigt im Training die halbe Satzzahl")

                Button("Intensität −15 %") {
                    onActivate("REDUCE_INTENSITY_BY_15_PERCENT")
                }
                .buttonStyle(.bordered)
                .accessibilityHint("Zeigt im Training 15 Prozent weniger Gewicht")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var subtitle: String? {
        if isActive { return "Anzeige angepasst" }
        return trend.deloadSuggested ? "Wiederholte Verschlechterung erkannt" : "Kein Deload nötig"
    }

    private var activeModeText: String {
        switch activeMode.uppercased() {
        case "HALVE_SET_VOLUME":
            return "Deine Trainingsziele zeigen die halbe Satzzahl."
        case "REDUCE_INTENSITY_BY_15_PERCENT":
            return "Deine Trainingsziele zeigen 15 % weniger Gewicht."
        default:
            return "Der Deload-Modus passt die angezeigten Trainingsziele an."
        }
    }

    private func percentText(_ value: Double) -> String {
        let sign = value > 0 ? "+" : ""
        return "\(sign)\(value.formatted(.number.precision(.fractionLength(1)))) %"
    }
}
