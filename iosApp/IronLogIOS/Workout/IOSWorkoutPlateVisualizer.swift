import SwiftUI
import Shared

/// Presents the shared KMP plate calculation next to the weight field. The calculator itself
/// remains in `Shared`; this view only converts the configured values into Kotlin wrapper
/// objects and renders the returned combination.
struct IOSWorkoutPlateVisualizer: View {
    let targetWeightKg: Double
    let barbellWeightKg: Double
    let availablePlates: [Double]
    let unitSystem: String

    @State private var calculation: PlateCalculationResult?

    private var requestID: String {
        [
            String(targetWeightKg),
            String(barbellWeightKg),
            availablePlates.map { String($0) }.joined(separator: ",")
        ].joined(separator: "|")
    }

    var body: some View {
        Group {
            if let calculation {
                resultView(calculation)
            }
        }
        .task(id: requestID) {
            calculation = calculate()
        }
    }

    private func calculate() -> PlateCalculationResult? {
        guard targetWeightKg.isFinite,
              targetWeightKg >= 0,
              barbellWeightKg.isFinite,
              barbellWeightKg >= 0
        else { return nil }

        let plates = availablePlates
            .filter { $0.isFinite && $0 > 0 }
            .map { KotlinDouble(double: $0) }
        guard !plates.isEmpty else { return nil }

        return PlateCalculatorFacade.shared.calculate(
            targetWeightKg: targetWeightKg,
            barbellWeightKg: barbellWeightKg,
            availablePlates: plates
        )
    }

    @ViewBuilder
    private func resultView(_ result: PlateCalculationResult) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Label("Plattenrechner", systemImage: "circle.grid.2x2")
                    .font(.subheadline.weight(.semibold))
                Spacer(minLength: 8)
                Text(result.isExact ? "Exakt" : "Näherung")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(result.isExact ? .green : .orange)
            }

            if result.platesPerSide.isEmpty {
                Text("Keine Platte pro Seite erforderlich")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            } else {
                Text("Pro Seite")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                LazyVGrid(
                    columns: [GridItem(.adaptive(minimum: 78), alignment: .leading)],
                    alignment: .leading,
                    spacing: 6
                ) {
                    ForEach(groupedPlates(result.platesPerSide), id: \.id) { plate in
                        Text("×\(plate.count) \(iosWorkoutDisplayWeight(kilograms: plate.value, unitSystem: unitSystem))")
                            .font(.caption.monospacedDigit())
                            .padding(.horizontal, 8)
                            .padding(.vertical, 5)
                            .background(.thinMaterial, in: Capsule())
                    }
                }
            }

            HStack(spacing: 8) {
                Text("Stange \(iosWorkoutDisplayWeight(kilograms: result.barbellWeightKg, unitSystem: unitSystem))")
                Text("·")
                Text("Ziel \(iosWorkoutDisplayWeight(kilograms: result.targetWeightKg, unitSystem: unitSystem))")
            }
            .font(.caption)
            .foregroundStyle(.secondary)

            if !result.isExact {
                Text("Es fehlen \(iosWorkoutDisplayWeight(kilograms: result.remainderKg * 2, unitSystem: unitSystem)) für das Zielgewicht.")
                    .font(.caption)
                    .foregroundStyle(.orange)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Plattenrechner")
        .accessibilityValue(
            result.isExact
                ? Text("Exakte Beladung")
                : Text("Näherung, Rest \(iosWorkoutDisplayWeight(kilograms: result.remainderKg * 2, unitSystem: unitSystem))")
        )
    }

    private func groupedPlates(_ plates: [KotlinDouble]) -> [PlateDisplayItem] {
        var result: [PlateDisplayItem] = []
        for plate in plates {
            let value = plate.doubleValue
            if let index = result.firstIndex(where: { abs($0.value - value) < 0.000_001 }) {
                result[index].count += 1
            } else {
                result.append(PlateDisplayItem(value: value, count: 1))
            }
        }
        return result
    }

    private struct PlateDisplayItem: Identifiable {
        let id: String
        let value: Double
        var count: Int

        init(value: Double, count: Int) {
            self.value = value
            self.count = count
            self.id = String(value)
        }
    }
}
