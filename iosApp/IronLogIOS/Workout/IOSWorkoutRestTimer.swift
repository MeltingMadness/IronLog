import Foundation
import SwiftUI
import UIKit

/// The rest timer is presentation state, but it must survive a view being rebuilt or the app
/// moving through the background.  Epoch values are persisted instead of a ticking counter so
/// the displayed value always derives from the current wall clock and cannot drift.
struct IOSWorkoutRestTimer: Codable, Equatable, Identifiable {
    let sessionID: Int64
    let rowKey: String
    let startedAtEpochMillis: Int64
    let deadlineEpochMillis: Int64?
    let durationSeconds: Int

    var id: String { "\(sessionID):\(rowKey)" }

    var isCountdown: Bool { deadlineEpochMillis != nil }

    func remaining(at date: Date = Date()) -> Int {
        let now = Int64(date.timeIntervalSince1970 * 1_000)
        if let deadlineEpochMillis {
            return max(0, Int((deadlineEpochMillis - now) / 1_000))
        }
        return max(0, Int((now - startedAtEpochMillis) / 1_000))
    }

    var hasElapsed: Bool {
        guard let deadlineEpochMillis else { return false }
        return Int64(Date().timeIntervalSince1970 * 1_000) >= deadlineEpochMillis
    }

    static func make(
        sessionID: Int64,
        rowKey: String,
        durationSeconds: Int,
        now: Date = Date()
    ) -> IOSWorkoutRestTimer {
        let started = Int64(now.timeIntervalSince1970 * 1_000)
        let deadline = durationSeconds > 0
            ? started + Int64(durationSeconds) * 1_000
            : nil
        return IOSWorkoutRestTimer(
            sessionID: sessionID,
            rowKey: rowKey,
            startedAtEpochMillis: started,
            deadlineEpochMillis: deadline,
            durationSeconds: max(0, durationSeconds)
        )
    }
}

/// Named keys make the persisted timer inspectable and allow a later migration to remove or
/// replace this small presentation record without touching workout business data.
enum IOSWorkoutRestTimerPersistence {
    /// Current format: one JSON array per app, filtered by session and keyed by
    /// `rowKey` at the workout layer. This mirrors Android's per-exercise map.
    static let timersKey = "ironlog.workout.restTimers"

    /// Legacy single-timer keys from the first iOS workout implementation. They
    /// stay readable for one migration and are removed after a successful read.
    static let sessionIDKey = "ironlog.workout.restTimer.sessionID"
    static let rowKeyKey = "ironlog.workout.restTimer.rowKey"
    static let startedAtEpochMillisKey = "ironlog.workout.restTimer.startedAtEpochMillis"
    static let deadlineEpochMillisKey = "ironlog.workout.restTimer.deadlineEpochMillis"
    static let durationSecondsKey = "ironlog.workout.restTimer.durationSeconds"

    static func load(sessionID: Int64) -> [String: IOSWorkoutRestTimer] {
        let defaults = UserDefaults.standard
        if let data = defaults.data(forKey: timersKey),
           let decoded = try? JSONDecoder().decode([IOSWorkoutRestTimer].self, from: data) {
            let matching = decoded.filter { $0.sessionID == sessionID }
            return Dictionary(uniqueKeysWithValues: matching.map { ($0.rowKey, $0) })
        }

        // Migrate the old one-row representation exactly once. If it belongs to
        // another session it is stale and can be discarded without affecting the
        // current workout.
        guard let legacy = loadLegacy(sessionID: sessionID) else {
            // A legacy record for a different or already-finished session must
            // not spring back when that old id is encountered later.
            if defaults.object(forKey: sessionIDKey) != nil {
                clearLegacy()
            }
            return [:]
        }
        saveAll([legacy])
        clearLegacy()
        return [legacy.rowKey: legacy]
    }

    static func upsert(_ timer: IOSWorkoutRestTimer) {
        var timers = load(sessionID: timer.sessionID)
        timers[timer.rowKey] = timer
        saveAll(Array(timers.values))
    }

    static func remove(sessionID: Int64, rowKey: String) {
        var timers = load(sessionID: sessionID)
        timers.removeValue(forKey: rowKey)
        saveAll(Array(timers.values))
    }

    static func saveAll(_ timers: [IOSWorkoutRestTimer]) {
        let defaults = UserDefaults.standard
        let sortedTimers = timers.sorted {
            $0.sessionID == $1.sessionID
                ? ($0.rowKey == $1.rowKey
                    ? $0.startedAtEpochMillis < $1.startedAtEpochMillis
                    : $0.rowKey < $1.rowKey)
                : $0.sessionID < $1.sessionID
        }
        if let data = try? JSONEncoder().encode(sortedTimers) {
            defaults.set(data, forKey: timersKey)
        }
    }

    static func clear(sessionID: Int64) {
        let defaults = UserDefaults.standard
        if let data = defaults.data(forKey: timersKey),
           let decoded = try? JSONDecoder().decode([IOSWorkoutRestTimer].self, from: data) {
            saveAll(decoded.filter { $0.sessionID != sessionID })
        }
        if Int64(defaults.integer(forKey: sessionIDKey)) == sessionID {
            clearLegacy()
        }
    }

    static func clear() {
        let defaults = UserDefaults.standard
        defaults.removeObject(forKey: timersKey)
        clearLegacy()
    }

    private static func loadLegacy(sessionID: Int64) -> IOSWorkoutRestTimer? {
        let defaults = UserDefaults.standard
        guard defaults.object(forKey: sessionIDKey) != nil,
              Int64(defaults.integer(forKey: sessionIDKey)) == sessionID,
              let rowKey = defaults.string(forKey: rowKeyKey),
              defaults.object(forKey: startedAtEpochMillisKey) != nil,
              defaults.object(forKey: durationSecondsKey) != nil
        else { return nil }

        let deadline: Int64?
        if defaults.object(forKey: deadlineEpochMillisKey) == nil {
            deadline = nil
        } else {
            deadline = Int64(defaults.integer(forKey: deadlineEpochMillisKey))
        }

        return IOSWorkoutRestTimer(
            sessionID: sessionID,
            rowKey: rowKey,
            startedAtEpochMillis: Int64(defaults.integer(forKey: startedAtEpochMillisKey)),
            deadlineEpochMillis: deadline,
            durationSeconds: defaults.integer(forKey: durationSecondsKey)
        )
    }

    private static func clearLegacy() {
        let defaults = UserDefaults.standard
        defaults.removeObject(forKey: sessionIDKey)
        defaults.removeObject(forKey: rowKeyKey)
        defaults.removeObject(forKey: startedAtEpochMillisKey)
        defaults.removeObject(forKey: deadlineEpochMillisKey)
        defaults.removeObject(forKey: durationSecondsKey)
    }
}

struct IOSWorkoutRestTimerView: View {
    let timer: IOSWorkoutRestTimer
    let title: String?
    let accentColor: Color?
    let onDismiss: () -> Void
    let onComplete: () -> Void

    @State private var didComplete = false

    @Environment(\.ironLogTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        let palette = theme.palette(for: colorScheme)
        let timerColor = accentColor ?? (timer.isCountdown ? palette.warning : palette.information)

        TimelineView(.periodic(from: Date(), by: 1)) { context in
            let seconds = timer.remaining(at: context.date)
            HStack(spacing: 12) {
                Image(systemName: timer.isCountdown ? "timer" : "stopwatch")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(timerColor)

                VStack(alignment: .leading, spacing: 2) {
                    if let title, !title.isEmpty {
                        Text(title)
                            .font(.subheadline.weight(.semibold))
                    } else {
                        Text(timer.isCountdown ? "Pause" : "Pause läuft")
                            .font(.subheadline.weight(.semibold))
                    }
                    Text(timer.isCountdown ? "Noch \(formatDuration(seconds))" : formatDuration(seconds))
                        .font(.headline.monospacedDigit())
                }

                Spacer(minLength: 8)

                Button("Beenden", action: onDismiss)
                    .buttonStyle(.bordered)
                    .tint(palette.textSecondary)
                    .accessibilityLabel("Pausentimer für \(title ?? "Übung") beenden")
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(palette.surfaceElevated, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(timerColor.opacity(0.38), lineWidth: 1)
            }
            .accessibilityElement(children: .contain)
            .accessibilityLabel(title ?? "Pausentimer")
            .accessibilityValue(Text(timer.isCountdown ? "Noch \(formatDuration(seconds))" : formatDuration(seconds)))
        }
        // The row key keeps SwiftUI's card identity stable, while a fresh start
        // epoch restarts the completion task when the athlete logs the next set.
        .task(id: "\(timer.id):\(timer.startedAtEpochMillis)")
        {
            await waitForCompletionIfNeeded()
        }
        .onChange(of: timer.startedAtEpochMillis) { _, _ in
            didComplete = false
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            completeIfElapsed()
        }
    }

    private func waitForCompletionIfNeeded() async {
        guard timer.isCountdown, let deadline = timer.deadlineEpochMillis else { return }

        let now = Int64(Date().timeIntervalSince1970 * 1_000)
        let delayMillis = max(0, deadline - now)
        if delayMillis > 0 {
            do {
                try await Task.sleep(nanoseconds: UInt64(delayMillis) * 1_000_000)
            } catch {
                return
            }
        }
        guard !Task.isCancelled, !didComplete else { return }
        completeIfElapsed()
    }

    private func completeIfElapsed() {
        guard timer.isCountdown, timer.hasElapsed, !didComplete else { return }
        didComplete = true

        // Match Android's completion feedback while keeping the timer dismissal in the
        // workout screen, where the persisted timer state is cleared atomically.
        let feedback = UINotificationFeedbackGenerator()
        feedback.prepare()
        feedback.notificationOccurred(.success)
        onComplete()
    }

    private func formatDuration(_ totalSeconds: Int) -> String {
        let minutes = max(0, totalSeconds) / 60
        let seconds = max(0, totalSeconds) % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }
}
