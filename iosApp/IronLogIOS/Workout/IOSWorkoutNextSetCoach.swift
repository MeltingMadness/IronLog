import Shared
import SwiftUI

/// Swift value representation of the shared next-set coach result. The workout
/// row owns this snapshot for rendering; applying a value remains an explicit
/// action on the next set editor.
struct IOSWorkoutNextSetRecommendation: Equatable {
    let recommendedWeightKg: Double
    let lastWeightKg: Double
    let lastRpe: Double
    let targetRpe: Double?
    let isOvershoot: Bool
    let backoffWeightKg: Double?
}

enum IOSWorkoutNextSetCoach {
    /// Delegates selection, RPE/RIR math, capping, rounding, and backoff math to
    /// the shared implementation used by Android. Stored `ILWorkoutSet.rpe` is
    /// already canonical RPE, so no client-specific conversion is repeated here.
    static func evaluate(
        sets: [ILWorkoutSet],
        target: ILWorkoutPlanTarget?,
        intensitySystem: String
    ) -> IOSWorkoutNextSetRecommendation? {
        let configuredIntensity = intensitySystem.uppercased()
        let targetScheme = target?.progression.scheme.uppercased()
        let effectiveIntensityEnabled = configuredIntensity != "OFF" || targetScheme == "RPE_RIR"
        guard effectiveIntensityEnabled else { return nil }

        let coachSets = sets.map { set in
            NextSetCoachSet(
                id: set.id,
                setNumber: Int32(set.setNumber),
                weightKg: set.weightKg,
                rpe: set.rpe.map { KotlinDouble(double: $0) },
                setType: resolvedIOSWorkoutSetType(set).rawValue,
                completedAtEpochMillis: set.completedAt,
                orderingToken: set.id
            )
        }

        let targetRpe = targetScheme == "RPE_RIR"
            ? target?.progression.targetRpe.map { KotlinDouble(double: $0) }
            : nil
        let backoffPercent = target?.progression.backoffPercent ?? 10.0

        guard let result = NextSetCoachFacade.shared.evaluate(
            sets: coachSets,
            targetRpe: targetRpe,
            backoffPercent: backoffPercent
        ) else {
            return nil
        }

        return IOSWorkoutNextSetRecommendation(
            recommendedWeightKg: result.recommendedWeightKg,
            lastWeightKg: result.lastWeightKg,
            lastRpe: result.lastRpe,
            targetRpe: result.targetRpe?.doubleValue,
            isOvershoot: result.isOvershoot,
            backoffWeightKg: result.backoffWeightKg?.doubleValue
        )
    }
}

struct IOSWorkoutNextSetRecommendationView: View {
    let recommendation: IOSWorkoutNextSetRecommendation
    let unitSystem: String
    let onApplyWeight: (Double) -> Void
    let onApplyBackoff: (Double) -> Void

    @Environment(\.ironLogTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let palette = theme.palette(for: colorScheme)
        let recommendationColor: Color = recommendation.isOvershoot ? palette.danger : palette.information
        let delta = recommendation.recommendedWeightKg - recommendation.lastWeightKg
        let weight = iosWorkoutDisplayWeight(
            kilograms: recommendation.recommendedWeightKg,
            unitSystem: unitSystem
        )
        let deltaText = deltaMagnitudeText(delta)

        VStack(alignment: .leading, spacing: 7) {
            HStack(spacing: 6) {
                Text(deltaText.isEmpty ? "Nächster Satz: \(weight)" : "Nächster Satz: \(weight) (\(deltaText))")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(recommendationColor)
                    .lineLimit(2)

                Spacer(minLength: 4)

                Button("Übernehmen") {
                    onApplyWeight(recommendation.recommendedWeightKg)
                }
                .font(.caption.weight(.semibold))
                .buttonStyle(.bordered)
                .controlSize(.small)
                .tint(recommendationColor)
                .accessibilityLabel("Coach-Gewicht \(weight) übernehmen")
            }

            HStack(spacing: 6) {
                if let targetRpe = recommendation.targetRpe {
                    IOSWorkoutCoachPill(
                        text: "Ziel-RPE \(iosWorkoutDecimal(targetRpe, fractionDigits: 1))",
                        color: palette.information
                    )
                }
                if recommendation.isOvershoot {
                    IOSWorkoutCoachPill(
                        text: "Zu schwer (RPE \(iosWorkoutDecimal(recommendation.lastRpe, fractionDigits: 1)))",
                        color: palette.danger
                    )
                }
            }

            if let backoff = recommendation.backoffWeightKg {
                HStack(spacing: 6) {
                    IOSWorkoutCoachPill(
                        text: "Backoff-Satz: \(iosWorkoutDisplayWeight(kilograms: backoff, unitSystem: unitSystem))",
                        color: palette.danger
                    )
                    Button("Backoff übernehmen") {
                        onApplyBackoff(backoff)
                    }
                    .font(.caption.weight(.semibold))
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                    .tint(palette.danger)
                    .accessibilityLabel("Backoff-Gewicht \(iosWorkoutDisplayWeight(kilograms: backoff, unitSystem: unitSystem)) übernehmen")
                }
            }
        }
        .padding(9)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(recommendationColor.opacity(0.10), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .stroke(recommendationColor.opacity(0.30), lineWidth: 1)
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Coach-Empfehlung")
        .accessibilityValue(Text(deltaText.isEmpty ? "Nächster Satz \(weight)" : "Nächster Satz \(weight), \(deltaText)"))
    }

    private func deltaMagnitudeText(_ delta: Double) -> String {
        guard abs(delta) >= 0.05 else { return "" }
        let sign = delta > 0 ? "+" : "−"
        return "\(sign)\(iosWorkoutDisplayWeight(kilograms: abs(delta), unitSystem: unitSystem))"
    }
}

private struct IOSWorkoutCoachPill: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 7)
            .padding(.vertical, 4)
            .background(color.opacity(0.14), in: Capsule())
    }
}
