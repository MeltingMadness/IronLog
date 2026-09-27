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
                .font(.geist(.caption, weight: .bold))
                .tracking(0.8)
                .ironLogSecondaryText()
                .lineLimit(1)
        HStack(alignment: .center, spacing: 10) {
            VStack(alignment: .leading, spacing: 0) {
                TimelineView(.periodic(from: startDate, by: 1)) { context in
                    Text(elapsedText(at: context.date))
                        .font(.geist(size: 30, weight: .bold))
                        .monospacedDigit()
                }
                Text(progressText)
                    .font(.geist(.caption))
                    .monospacedDigit()
                    .ironLogSecondaryText()
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
                    .font(.geist(.subheadline, weight: .heavy))
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
                                .font(.geist(.footnote, weight: .heavy))
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
                    .font(.geist(.footnote, weight: .bold))
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
                .font(.geist(.caption, weight: .bold))
                .tracking(0.8)
                .ironLogSecondaryText()
                .lineLimit(1)
            HStack(alignment: .lastTextBaseline, spacing: 4) {
                TextField("–", text: $text)
                    .keyboardType(keyboard)
                    .font(.geist(size: 46, weight: .bold))
                    .monospacedDigit()
                    .multilineTextAlignment(.center)
                    .fixedSize()
                    .accessibilityLabel(label)
                if let unit {
                    Text(unit)
                        .font(.geist(size: 18))
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
                        .font(.geist(.subheadline, weight: .heavy))
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
            .font(.geist(size: 17, weight: .heavy))
            .foregroundStyle(dark ? glassInk : .white)
            .frame(maxWidth: .infinity, minHeight: 60)
            .background((dark ? Color.white : glassInk).opacity(isEnabled ? 1 : 0.35), in: Capsule())
            .shadow(color: .black.opacity(isEnabled ? 0.35 : 0), radius: 14, y: 8)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

/// Ends a countdown like the Ember timer card: one success haptic, then
/// `onComplete` (which dismisses the timer). Liquid Glass shows timers in the dock
/// and pause screen, so this carries the end-of-rest behavior without visible UI.
struct IOSWorkoutRestCompletionWatcher: View {
    let timer: IOSWorkoutRestTimer
    let onComplete: () -> Void

    @Environment(\.scenePhase) private var scenePhase
    @State private var didComplete = false

    var body: some View {
        Color.clear
            .frame(width: 0, height: 0)
            .accessibilityHidden(true)
            .task(id: "\(timer.id):\(timer.startedAtEpochMillis):\(timer.deadlineEpochMillis ?? 0)") {
                didComplete = false
                guard let deadline = timer.deadlineEpochMillis else { return }
                let delay = max(0, deadline - Int64(Date().timeIntervalSince1970 * 1_000))
                if delay > 0 {
                    do { try await Task.sleep(nanoseconds: UInt64(delay) * 1_000_000) } catch { return }
                }
                completeIfElapsed()
            }
            .onChange(of: scenePhase) { _, phase in
                if phase == .active { completeIfElapsed() }
            }
    }

    private func completeIfElapsed() {
        guard timer.isCountdown, timer.hasElapsed, !didComplete else { return }
        didComplete = true
        let feedback = UINotificationFeedbackGenerator()
        feedback.prepare()
        feedback.notificationOccurred(.success)
        onComplete()
    }
}

private func pauseClock(_ seconds: Int) -> String {
    String(format: "%d:%02d", max(0, seconds) / 60, max(0, seconds) % 60)
}

/// Floating glass dock with a mini ring for the running rest: tap opens the
/// pause screen, "Überspringen" ends the rest right away.
struct IOSWorkoutGlassRestDock: View {
    let timer: IOSWorkoutRestTimer
    let exerciseName: String
    let onOpen: () -> Void
    let onSkip: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let dark = colorScheme == .dark
        let ink = dark ? Color.white : glassInk
        TimelineView(.periodic(from: Date(), by: 1)) { context in
            let seconds = timer.remaining(at: context.date)
            let fraction = timer.isCountdown && timer.durationSeconds > 0
                ? Double(seconds) / Double(timer.durationSeconds)
                : 1
            HStack(spacing: 12) {
                Button(action: onOpen) {
                    HStack(spacing: 12) {
                        ZStack {
                            Circle().stroke(ink.opacity(0.18), lineWidth: 4)
                            Circle()
                                .trim(from: 0, to: fraction)
                                .stroke(glassTeal, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                                .rotationEffect(.degrees(-90))
                            Text(pauseClock(seconds))
                                .font(.geist(size: 15, weight: .bold))
                                .monospacedDigit()
                        }
                        .frame(width: 56, height: 56)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("PAUSE")
                                .font(.geist(.caption, weight: .bold))
                                .tracking(0.8)
                                .ironLogSecondaryText()
                            Text(exerciseName)
                                .font(.geist(.subheadline, weight: .heavy))
                                .lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Pause öffnen, \(exerciseName)")
                .accessibilityValue(timer.isCountdown ? "Noch \(pauseClock(seconds))" : pauseClock(seconds))
                Button(action: onSkip) {
                    Text("Überspringen")
                        .font(.geist(.subheadline, weight: .heavy))
                        .foregroundStyle(dark ? glassInk : .white)
                        .padding(.horizontal, 18)
                        .frame(minHeight: 48)
                        .background(dark ? Color.white : glassInk, in: Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding(8)
            .liquidGlass(.strong, in: RoundedRectangle(cornerRadius: 36, style: .continuous))
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }
}

/// Pause as a full-screen lens: the glass empties with the remaining time,
/// −15 s / +30 s, what comes next and "Pause überspringen". "Zum Training"
/// returns to the workout while the timer keeps running in the dock.
struct IOSWorkoutGlassPauseScreen: View {
    let timer: IOSWorkoutRestTimer
    let exerciseName: String
    let nextSetNumber: Int
    let progressText: String
    let onMinus: () -> Void
    let onPlus: () -> Void
    let onSkip: () -> Void
    let onClose: () -> Void

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.ironLogTheme) private var theme

    var body: some View {
        let ink = colorScheme == .dark ? Color.white : glassInk
        TimelineView(.periodic(from: Date(), by: reduceMotion || theme.reducedMotion ? 1 : 1.0 / 30)) { context in
            let seconds = timer.remaining(at: context.date)
            let level = timer.isCountdown && timer.durationSeconds > 0
                ? Double(seconds) / Double(timer.durationSeconds)
                : 0.5
            let phase = reduceMotion || theme.reducedMotion
                ? 0
                : context.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 4) / 4 * 2 * .pi
            VStack(spacing: 20) {
                HStack {
                    Text(progressText.uppercased())
                        .font(.geist(.caption, weight: .bold))
                        .tracking(0.8)
                        .ironLogSecondaryText()
                    Spacer()
                    Button("Zum Training", action: onClose)
                        .font(.geist(.subheadline, weight: .heavy))
                        .foregroundStyle(ink)
                        .padding(.horizontal, 16)
                        .frame(minHeight: 44)
                        .liquidGlass(in: Capsule())
                        .buttonStyle(.plain)
                }
                Spacer(minLength: 0)
                ZStack {
                    Canvas { canvas, size in
                        func wave(offset: Double, amplitude: Double, shift: Double, color: Color) {
                            let base = size.height * (1 - min(max(level, 0), 1)) + offset
                            var path = Path()
                            path.move(to: CGPoint(x: 0, y: base))
                            for step in 0...48 {
                                let x = size.width * Double(step) / 48
                                let y = base + amplitude * sin(x / size.width * 2 * .pi + phase + shift)
                                path.addLine(to: CGPoint(x: x, y: y))
                            }
                            path.addLine(to: CGPoint(x: size.width, y: size.height))
                            path.addLine(to: CGPoint(x: 0, y: size.height))
                            path.closeSubpath()
                            canvas.fill(path, with: .color(color))
                        }
                        wave(offset: 0, amplitude: 9, shift: 0, color: ink.opacity(0.16))
                        wave(offset: 6, amplitude: 8, shift: 1.6, color: glassTeal.opacity(0.28))
                    }
                    .clipShape(Circle())
                    VStack(spacing: 2) {
                        Text("PAUSE")
                            .font(.geist(.caption, weight: .bold))
                            .tracking(0.8)
                            .ironLogSecondaryText()
                        Text(pauseClock(seconds))
                            .font(.geist(size: 88, weight: .bold))
                            .monospacedDigit()
                            .tracking(-3)
                            .minimumScaleFactor(0.6)
                        if timer.isCountdown {
                            Text("von \(pauseClock(timer.durationSeconds))")
                                .font(.geist(.subheadline))
                                .ironLogSecondaryText()
                        }
                    }
                }
                .frame(maxWidth: 296)
                .aspectRatio(1, contentMode: .fit)
                .liquidGlass(.strong, in: Circle())
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Pause")
                .accessibilityValue("Noch \(seconds / 60) Minuten \(seconds % 60) Sekunden")

                if timer.isCountdown {
                    HStack(spacing: 12) {
                        pauseButton("−15 s", label: "Pause um 15 Sekunden verkürzen", action: onMinus)
                        pauseButton("+30 s", label: "Pause um 30 Sekunden verlängern", action: onPlus)
                    }
                }
                Spacer(minLength: 0)
                VStack(alignment: .leading, spacing: 3) {
                    Text("ALS NÄCHSTES")
                        .font(.geist(.caption, weight: .bold))
                        .tracking(0.8)
                        .ironLogSecondaryText()
                    Text("\(exerciseName) · Satz \(nextSetNumber)")
                        .font(.geist(.title3, weight: .heavy))
                        .lineLimit(2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 18)
                .padding(.vertical, 16)
                .liquidGlass(in: RoundedRectangle(cornerRadius: 30, style: .continuous))
                .accessibilityElement(children: .combine)

                Button("Pause überspringen", action: onSkip)
                    .buttonStyle(IOSWorkoutGlassPrimaryButtonStyle())
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 16)
        }
        .background { IronLogLiquidBackground() }
    }

    private func pauseButton(_ text: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text)
                .font(.geist(.footnote, weight: .heavy))
                .frame(width: 104, height: 52)
                .liquidGlass(in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}
