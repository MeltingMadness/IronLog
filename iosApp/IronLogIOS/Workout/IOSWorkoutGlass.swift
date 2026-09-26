import SwiftUI

private let glassInk = Color(red: 11 / 255, green: 13 / 255, blue: 18 / 255)
private let glassTeal = Color(red: 46 / 255, green: 196 / 255, blue: 182 / 255)

/// Status of an exercise in the Liquid Glass exercise rail.
enum IOSWorkoutRailStatus {
    case done
    case current
    case upcoming
}

struct IOSWorkoutRailItem: Identifiable {
    let id: String
    let name: String
    let status: IOSWorkoutRailStatus
}

/// Workout head in Liquid Glass: plan and exercise position, elapsed time as a
/// large number, set progress and volume, "Beenden" as a glass pill and a menu
/// for notes and discarding. Replaces the navigation title and bottom bar.
struct IOSWorkoutGlassHeader: View {
    let label: String
    let startDate: Date
    let progressText: String
    let hasNotes: Bool
    let onNotes: () -> Void
    let onDiscard: () -> Void
    let onFinish: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased())
                .font(.caption.weight(.bold))
                .tracking(0.8)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        HStack(alignment: .center, spacing: 10) {
            VStack(alignment: .leading, spacing: 0) {
                TimelineView(.periodic(from: startDate, by: 1)) { context in
                    Text(elapsedText(at: context.date))
                        .font(.system(size: 30, weight: .bold))
                        .monospacedDigit()
                }
                Text(progressText)
                    .font(.caption)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            Spacer(minLength: 0)
            Menu {
                Button(hasNotes ? "Notiz bearbeiten" : "Notiz", systemImage: "note.text", action: onNotes)
                Button("Workout verwerfen", systemImage: "trash", role: .destructive, action: onDiscard)
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 17, weight: .bold))
                    .frame(width: 44, height: 44)
                    .liquidGlass(in: Circle())
            }
            .accessibilityLabel("Weitere Aktionen")
            Button(action: onFinish) {
                Text("Beenden")
                    .font(.subheadline.weight(.heavy))
                    .padding(.horizontal, 18)
                    .frame(minHeight: 44)
                    .liquidGlass(in: Capsule())
            }
            .buttonStyle(.plain)
        }
        }
        .padding(.horizontal, 20)
    }

    private func elapsedText(at date: Date) -> String {
        let elapsed = max(0, Int(date.timeIntervalSince(startDate)))
        let hours = elapsed / 3600
        let minutes = (elapsed % 3600) / 60
        let seconds = elapsed % 60
        return hours > 0
            ? String(format: "%d:%02d:%02d", hours, minutes, seconds)
            : String(format: "%02d:%02d", minutes, seconds)
    }
}

/// Horizontal exercise rail: done (check), current (bright pill), upcoming (outlined).
struct IOSWorkoutGlassRail: View {
    let items: [IOSWorkoutRailItem]
    let onSelect: (IOSWorkoutRailItem) -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let dark = colorScheme == .dark
        let ink = dark ? Color.white : glassInk
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(items) { item in
                    Button { onSelect(item) } label: {
                        switch item.status {
                        case .done:
                            HStack(spacing: 6) {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 11, weight: .heavy))
                                    .foregroundStyle(.white)
                                    .frame(width: 22, height: 22)
                                    .background(glassTeal.opacity(0.6), in: Circle())
                                Text(item.name)
                            }
                            .padding(.leading, 8)
                            .padding(.trailing, 12)
                            .frame(height: 36)
                            .background(ink.opacity(0.10), in: Capsule())
                            .opacity(0.8)
                        case .current:
                            Text(item.name)
                                .fontWeight(.heavy)
                                .foregroundStyle(dark ? glassInk : .white)
                                .padding(.horizontal, 14)
                                .frame(height: 36)
                                .background(dark ? Color.white : glassInk, in: Capsule())
                        case .upcoming:
                            Text(item.name)
                                .foregroundStyle(ink.opacity(0.8))
                                .padding(.horizontal, 12)
                                .frame(height: 36)
                                .overlay { Capsule().strokeBorder(ink.opacity(0.18)) }
                        }
                    }
                    .buttonStyle(.plain)
                    .font(.footnote.weight(.bold))
                    .lineLimit(1)
                    .accessibilityLabel(item.name)
                    .accessibilityValue(accessibilityValue(item.status))
                    .accessibilityAddTraits(item.status == .current ? .isSelected : [])
                }
            }
            .padding(.horizontal, 20)
        }
    }

    private func accessibilityValue(_ status: IOSWorkoutRailStatus) -> String {
        switch status {
        case .done: return "erledigt"
        case .current: return "aktuell"
        case .upcoming: return ""
        }
    }
}

/// Large number with round −/+ buttons; the number stays a text field.
struct IOSWorkoutGlassStepper: View {
    let label: String
    @Binding var text: String
    let unit: String?
    let keyboard: UIKeyboardType
    let decreaseLabel: String
    let increaseLabel: String
    let onDecrease: () -> Void
    let onIncrease: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let ink = colorScheme == .dark ? Color.white : glassInk
        VStack(spacing: 8) {
            Text(label.uppercased())
                .font(.caption2.weight(.bold))
                .tracking(0.8)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            HStack(alignment: .lastTextBaseline, spacing: 4) {
                TextField("–", text: $text)
                    .keyboardType(keyboard)
                    .font(.system(size: 46, weight: .bold))
                    .monospacedDigit()
                    .multilineTextAlignment(.center)
                    .fixedSize()
                    .accessibilityLabel(label)
                if let unit {
                    Text(unit)
                        .font(.system(size: 18))
                        .foregroundStyle(ink.opacity(0.7))
                }
            }
            HStack(spacing: 12) {
                roundButton("minus", label: decreaseLabel, action: onDecrease)
                roundButton("plus", label: increaseLabel, action: onIncrease)
            }
        }
        .padding(EdgeInsets(top: 14, leading: 10, bottom: 12, trailing: 10))
        .frame(maxWidth: .infinity)
        .background(ink.opacity(0.07), in: RoundedRectangle(cornerRadius: 26, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 26, style: .continuous).strokeBorder(ink.opacity(0.10))
        }
    }

    private func roundButton(_ symbol: String, label: String, action: @escaping () -> Void) -> some View {
        let ink = colorScheme == .dark ? Color.white : glassInk
        return Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 17, weight: .semibold))
                .frame(width: 48, height: 48)
                .background(ink.opacity(0.10), in: Circle())
                .overlay { Circle().strokeBorder(ink.opacity(0.22)) }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// Segmented intensity bar (RPE or RIR); the selected value is a bright pill.
struct IOSWorkoutGlassIntensityBar: View {
    let options: [String]
    @Binding var selection: String

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let dark = colorScheme == .dark
        let ink = dark ? Color.white : glassInk
        HStack(spacing: 4) {
            ForEach(options, id: \.self) { option in
                let selected = option == selection
                Button {
                    selection = selected ? "" : option
                } label: {
                    Text(option)
                        .font(.subheadline.weight(.heavy))
                        .foregroundStyle(selected ? (dark ? glassInk : .white) : ink)
                        .frame(maxWidth: .infinity, minHeight: 40)
                        .background {
                            if selected {
                                Capsule()
                                    .fill(dark ? Color.white : glassInk)
                                    .shadow(color: .black.opacity(0.3), radius: 6, y: 3)
                            }
                        }
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(4)
        .background(Color.black.opacity(dark ? 0.18 : 0.05), in: Capsule())
        .overlay { Capsule().strokeBorder(ink.opacity(0.08)) }
    }
}

/// Bright primary pill used for "Satz bestätigen" in Liquid Glass.
struct IOSWorkoutGlassPrimaryButtonStyle: ButtonStyle {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        let dark = colorScheme == .dark
        configuration.label
            .font(.system(size: 17, weight: .heavy))
            .foregroundStyle(dark ? glassInk : .white)
            .frame(maxWidth: .infinity, minHeight: 60)
            .background((dark ? Color.white : glassInk).opacity(isEnabled ? 1 : 0.35), in: Capsule())
            .shadow(color: .black.opacity(isEnabled ? 0.35 : 0), radius: 14, y: 8)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}
