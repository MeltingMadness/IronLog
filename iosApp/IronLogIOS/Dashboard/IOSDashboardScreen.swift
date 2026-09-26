import SwiftUI

/// Native iOS dashboard driven by the shared, immutable analytics projection.
///
/// The screen owns navigation, the optional daily check-in, and actions only.
/// Training trend, daily form, muscle facts, week boundaries, volume thresholds,
/// and rotation choices all come from the shared core (`ILTrainingAnalytics` and
/// `ILReadinessAssessment`); no readiness number is recomputed in Swift.
struct IOSDashboardScreen: View {
    @Environment(IOSTrainingStore.self) private var store
    @EnvironmentObject private var settings: IOSSettingsViewModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var showingProgressionReview = false
    @State private var showingStatistics = false
    @State private var showingCheckIn = false
    @State private var skipSuggestion: ILMetaRotationSuggestion?
    @State private var selectedMuscleVolumeWeek: ILTrainingWeeklyMuscleVolume?
    @State private var isMuscleVolumeLoading = false
    @State private var muscleVolumeError: String?
    /// Local calendar day the dashboard currently shows. Kept fresh by
    /// `watchLocalDayChange()` and the scene re-activation check, so "heute" can
    /// never silently mean yesterday.
    @State private var dayGuard = ILLocalDayGuard()

    private var todayKey: String { dayGuard.currentDate }

    private var weekStartsSunday: Bool {
        settings.state.weekStart.uppercased() == "SUNDAY"
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                if let analytics = store.analytics {
                    dashboardContent(analytics)
                } else {
                    unavailableContent
                }
            }
            .background(themeBackground)
            .navigationTitle("Dashboard")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showingStatistics = true
                    } label: {
                        Label("Statistiken", systemImage: "chart.xyaxis.line")
                    }
                    .accessibilityHint("Öffnet die vollständigen Trainingsstatistiken")
                }
            }
            .refreshable {
                store.refreshAnalytics(
                    timeZoneId: TimeZone.current.identifier,
                    weekStartsSunday: weekStartsSunday
                )
                loadMuscleVolumeWeek(
                    selectedMuscleVolumeWeek?.weekStart ?? store.analytics?.currentWeekStart
                )
            }
        }
        .sheet(isPresented: $showingProgressionReview) {
            IOSProgressionReviewScreen()
        }
        .sheet(isPresented: $showingStatistics) {
            IOSStatisticsScreen()
        }
        .sheet(isPresented: $showingCheckIn) {
            IOSDashboardCheckInSheet(localDate: todayKey)
        }
        .confirmationDialog(
            "Rotation anpassen",
            isPresented: Binding(
                get: { skipSuggestion != nil },
                set: { isPresented in
                    if !isPresented { skipSuggestion = nil }
                }
            )
        ) {
            if let suggestion = skipSuggestion {
                Button("\(suggestion.nextTrainingPlanName) überspringen", role: .destructive) {
                    Task { @MainActor in
                        _ = await store.command(
                            "metaplan.skip",
                            fields: [
                                "metaPlanId": suggestion.metaPlanId,
                                "expectedTrainingPlanId": suggestion.nextTrainingPlanId
                            ]
                        )
                        skipSuggestion = nil
                    }
                }
            }
            Button("Abbrechen", role: .cancel) { skipSuggestion = nil }
        } message: {
            if let suggestion = skipSuggestion {
                Text("Der nächste gültige Teilplan von \(suggestion.metaPlanName) wird übersprungen. Diese Entscheidung bleibt in der Rotation gespeichert.")
            }
        }
        .task(id: settings.state.weekStart) {
            store.refreshAnalytics(
                timeZoneId: TimeZone.current.identifier,
                weekStartsSunday: weekStartsSunday
            )
            selectedMuscleVolumeWeek = nil
            muscleVolumeError = nil
            loadMuscleVolumeWeek(store.analytics?.currentWeekStart)
        }
        .task {
            await watchLocalDayChange()
        }
        .onChange(of: store.analytics?.currentWeekStart) { _, currentWeekStart in
            guard selectedMuscleVolumeWeek == nil else { return }
            loadMuscleVolumeWeek(currentWeekStart)
        }
        .onChange(of: store.data) { _, _ in
            // A completed set or session can keep the same calendar week while
            // changing its volume. Refresh the separately selected week so the
            // card never presents a stale historical projection.
            loadMuscleVolumeWeek(
                selectedMuscleVolumeWeek?.weekStart ?? store.analytics?.currentWeekStart
            )
        }
        .onChange(of: scenePhase) { _, phase in
            // A suspended app wakes up on whatever day it is now; re-read the
            // calendar before the athlete can act on a stale "heute".
            guard phase == .active else { return }
            refreshLocalDayIfNeeded()
        }
    }

    @ViewBuilder
    private func dashboardContent(_ analytics: ILTrainingAnalytics) -> some View {
        let unitSystem = settings.state.unitSystem
        let weekly = selectedMuscleVolumeWeek
        let pendingProgressions = store.data?.progressionSuggestions.filter {
            $0.status.uppercased() == "PENDING"
        }.count ?? 0
        let activeDeloadMode = settings.state.deloadMode.uppercased()
        // The shared projection is the single source for trend, daily form and muscle facts.
        let assessment = store.readiness

        LazyVStack(alignment: .leading, spacing: 16) {
            greeting

            IOSDashboardCommandCenter(
                activeSession: store.data?.activeSession,
                suggestion: analytics.metaRotationSuggestions.first,
                fallbackPlan: store.data?.trainingPlans.first,
                isBusy: store.isBusy,
                onResume: { store.selectedTab = 1 },
                onStartFree: {
                    Task { _ = await store.startWorkout() }
                },
                onStartSuggestion: { suggestion in
                    Task {
                        _ = await store.startWorkout(
                            name: suggestion.nextTrainingPlanName,
                            planId: suggestion.nextTrainingPlanId,
                            metaPlanId: suggestion.metaPlanId
                        )
                    }
                },
                onRequestSkip: { suggestion in
                    skipSuggestion = suggestion
                },
                onStartPlan: { plan in
                    Task {
                        _ = await store.startWorkout(name: plan.name, planId: plan.id)
                    }
                }
            )

            IOSDashboardTrainingTrendCard(trend: assessment?.trainingTrend)

            IOSDashboardTodayCheckInCard(
                localDate: todayKey,
                checkIn: store.readinessData.checkIn(for: todayKey),
                dailyForm: assessment?.dailyForm,
                onEdit: { showingCheckIn = true }
            )

            IOSDashboardTodayMuscleCard(muscleGroups: assessment?.muscleGroups ?? [])

            IOSDashboardWorkoutCounts(
                week: analytics.workoutsThisWeek,
                month: analytics.workoutsThisMonth,
                lastSessionDate: analytics.lastSessionDate
            )

            if let trend = assessment?.trainingTrend,
               trend.deloadSuggested || trend.deloadActive || activeDeloadMode != "NONE" {
                IOSDashboardDeloadCard(
                    trend: trend,
                    activeMode: activeDeloadMode,
                    onActivate: settings.updateDeloadMode,
                    onDeactivate: { settings.updateDeloadMode("NONE") }
                )
            }

            if pendingProgressions > 0 {
                IOSDashboardPendingProgressions(
                    count: pendingProgressions,
                    action: { showingProgressionReview = true }
                )
            }

            IronLogWeeklyMuscleVolumeCard(
                title: "Wochenvolumen",
                subtitle: weekly.map { $0.weekStart == $0.currentWeekStart
                    ? "MEV · MAV · MRV für die aktuelle Woche"
                    : "MEV · MAV · MRV für die ausgewählte Woche"
                } ?? "MEV · MAV · MRV für die aktuelle Woche",
                rows: (weekly?.volumes ?? analytics.muscleVolumes).map { volume in
                    IronLogWeeklyMuscleVolume(
                        id: volume.id,
                        title: ilMuscleDisplayName(volume.muscleGroup),
                        weeklySets: volume.weeklySets,
                        minimumSets: volume.mev,
                        targetSets: volume.mav,
                        maximumSets: volume.mrv
                    )
                },
                initiallyExpanded: false,
                emptyMessage: "Noch keine Muskelvolumenwerte verfügbar",
                weekStart: weekly?.weekStart ?? analytics.currentWeekStart,
                currentWeekStart: weekly?.currentWeekStart ?? analytics.currentWeekStart,
                completedWorkoutCount: weekly?.completedWorkoutCount ?? analytics.workoutsThisWeek,
                isLoading: isMuscleVolumeLoading,
                errorMessage: muscleVolumeError,
                onRetry: {
                    loadMuscleVolumeWeek(weekly?.weekStart ?? analytics.currentWeekStart)
                },
                onPreviousWeek: {
                    guard let previous = ilAnalyticsShiftedWeekStart(
                        weekly?.weekStart ?? analytics.currentWeekStart,
                        weeks: -1
                    ) else { return }
                    loadMuscleVolumeWeek(previous)
                },
                onNextWeek: {
                    guard let next = ilAnalyticsShiftedWeekStart(
                        weekly?.weekStart ?? analytics.currentWeekStart,
                        weeks: 1
                    ) else { return }
                    loadMuscleVolumeWeek(next)
                }
            )

            IOSDashboardVolumeExplanation()

            IOSWeeklyVolumeTrendCard(bins: analytics.weeklyVolume, unitSystem: unitSystem)

            IOSDashboardRecentRecords(
                records: analytics.recentRecords,
                unitSystem: unitSystem
            )

            if let lastSessionDate = ilAnalyticsDateText(analytics.lastSessionDate) {
                IronLogCard(title: "Letztes Training", tone: .elevated) {
                    Label(lastSessionDate, systemImage: "calendar")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.primary)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    /// Keeps the local "today" fresh across midnight and time-zone changes, so the
    /// check-in is never written to yesterday's date. The loop is cancelled with the
    /// screen's `.task`, and re-reads the calendar day instead of assuming it advanced.
    private func watchLocalDayChange() async {
        while !Task.isCancelled {
            refreshLocalDayIfNeeded()
            try? await Task.sleep(nanoseconds: 60 * 1_000_000_000)
        }
    }

    /// Re-reads the local calendar day and, only when it actually changed, moves the
    /// whole screen onto the new day.
    ///
    /// A midnight crossing invalidates more than the date string: the shared
    /// readiness/trend projection still describes yesterday, and an open check-in
    /// sheet would keep its in-progress draft while receiving the new date, so saving
    /// it would file yesterday's answers under today. Dismissing the sheet discards
    /// that draft, and the projection is recomputed from the same durable snapshot.
    private func refreshLocalDayIfNeeded() {
        guard dayGuard.observe() else { return }
        if showingCheckIn {
            showingCheckIn = false
        }
        store.refreshAnalytics(
            timeZoneId: TimeZone.current.identifier,
            weekStartsSunday: weekStartsSunday
        )
        selectedMuscleVolumeWeek = nil
        muscleVolumeError = nil
        loadMuscleVolumeWeek(store.analytics?.currentWeekStart)
    }

    private func loadMuscleVolumeWeek(_ requestedWeekStart: String?) {
        guard let requestedWeekStart else { return }
        isMuscleVolumeLoading = true
        muscleVolumeError = nil

        let result = store.weeklyMuscleVolume(
            for: requestedWeekStart,
            timeZoneId: TimeZone.current.identifier,
            weekStartsSunday: weekStartsSunday
        )
        selectedMuscleVolumeWeek = result
        if result == nil {
            muscleVolumeError = store.weeklyMuscleVolumeError ?? "Wochenvolumen konnte nicht geladen werden."
        }
        isMuscleVolumeLoading = false
    }

    private var greeting: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(greetingTitle)
                .font(.title2.weight(.bold))
            Text("Dein Training, klar und nachvollziehbar.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }

    private var greetingTitle: String {
        switch Calendar.current.component(.hour, from: Date()) {
        case 5..<12: return "Guten Morgen"
        case 12..<18: return "Guten Tag"
        default: return "Guten Abend"
        }
    }

    private var unavailableContent: some View {
        ContentUnavailableView {
            Label("Auswertung nicht verfügbar", systemImage: "chart.bar.xaxis")
        } description: {
            Text(store.errorMessage ?? "Die Trainingsdaten werden noch ausgewertet.")
        } actions: {
            ProgressView()
                .controlSize(.small)
        }
        .frame(maxWidth: .infinity, minHeight: 420)
        .padding(20)
    }

    private var themeBackground: some View {
        Color(uiColor: .systemGroupedBackground)
            .ignoresSafeArea()
    }
}

private struct IOSDashboardCommandCenter: View {
    let activeSession: ILWorkoutSession?
    let suggestion: ILMetaRotationSuggestion?
    let fallbackPlan: ILTrainingPlan?
    let isBusy: Bool
    let onResume: () -> Void
    let onStartFree: () -> Void
    let onStartSuggestion: (ILMetaRotationSuggestion) -> Void
    let onRequestSkip: (ILMetaRotationSuggestion) -> Void
    let onStartPlan: (ILTrainingPlan) -> Void

    var body: some View {
        IronLogCard(title: "Nächster Schritt", subtitle: subtitle, tone: .elevated) {
            VStack(alignment: .leading, spacing: 12) {
                if let activeSession {
                    Button(action: onResume) {
                        Label(
                            activeSession.name.isEmpty ? "Training fortsetzen" : activeSession.name,
                            systemImage: "play.fill"
                        )
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isBusy)
                } else if let suggestion {
                    Button {
                        onStartSuggestion(suggestion)
                    } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            Label("Rotation starten", systemImage: "arrow.triangle.2.circlepath")
                                .font(.headline)
                            Text("\(suggestion.metaPlanName) · \(suggestion.nextTrainingPlanName)")
                                .font(.subheadline)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isBusy)
                } else if let fallbackPlan {
                    Button {
                        onStartPlan(fallbackPlan)
                    } label: {
                        Label("\(fallbackPlan.name) starten", systemImage: "play.fill")
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isBusy)
                } else {
                    Button(action: onStartFree) {
                        Label("Freies Training starten", systemImage: "play.fill")
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isBusy)
                }

                if activeSession == nil, let suggestion, suggestion.canSkip {
                    Button {
                        onRequestSkip(suggestion)
                    } label: {
                        Label("Nächsten Teilplan überspringen", systemImage: "forward.end")
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.bordered)
                    .disabled(isBusy)
                }

                if activeSession == nil && (suggestion != nil || fallbackPlan != nil) {
                    Button("Freies Training") {
                        onStartFree()
                    }
                    .buttonStyle(.bordered)
                    .disabled(isBusy)
                }
            }
            .buttonBorderShape(.roundedRectangle(radius: 12))
        }
    }

    private var subtitle: String {
        if activeSession != nil { return "Ein begonnenes Training wartet auf dich." }
        if let suggestion { return "Nächster Vorschlag aus \(suggestion.metaPlanName)." }
        if fallbackPlan != nil { return "Dein erster gespeicherter Plan ist bereit." }
        return "Starte eine freie Einheit und halte sie sauber fest."
    }
}

/// The start-page trend card. The ring keeps the existing graphical circle, but the
/// value is the shared `trainingIndex`, which the core leaves `nil` below its
/// evidence minimum. A missing index therefore renders as "not enough data"
/// instead of an invented 100 %.
private struct IOSDashboardTrainingTrendCard: View {
    let trend: ILTrainingTrendAssessment?

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    /// Collapsed, the card stays scannable (ring, status, evidence). Every reason,
    /// exercise and data-quality note the core computed stays reachable behind this
    /// disclosure, so a "further reasons" hint is never a dead end.
    @State private var showsDetails = false

    var body: some View {
        IronLogCard(title: "Trainingstrend") {
            if let trend {
                layout {
                    IronLogMetricRing(
                        title: "Trainingstrend",
                        value: trend.trainingIndex.map(String.init),
                        progress: trend.trainingIndex.map { Double($0) / 100.0 },
                        unit: trend.trainingIndex == nil ? nil : "%",
                        detail: nil,
                        accent: accent(trend),
                        unknownLabel: "Nicht genug Daten"
                    )

                    VStack(alignment: .leading, spacing: 7) {
                        statusLine(trend)
                        evidenceLine(trend)
                        detailsDisclosure(trend)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                Text("Der Trainingstrend konnte nicht gelesen werden.")
                    .font(.body)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var layout: AnyLayout {
        dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 18))
            : AnyLayout(HStackLayout(alignment: .center, spacing: 18))
    }

    /// The one short line kept outside the disclosure: the conclusion the core reached,
    /// plus the actionable deload state. The long explanation stays in the disclosure.
    @ViewBuilder
    private func statusLine(_ trend: ILTrainingTrendAssessment) -> some View {
        Text(ilTrendStatusText(trend.status))
            .font(.subheadline.weight(.semibold))
            .fixedSize(horizontal: false, vertical: true)

        if trend.deloadSuggested {
            Label("Deload vorgeschlagen", systemImage: "exclamationmark.triangle.fill")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.orange)
        }
        if trend.deloadActive {
            Label("Deload aktiv", systemImage: "checkmark.circle.fill")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.green)
        }
    }

    /// The single evidence line kept outside the disclosure: how many exercises the core
    /// could actually evaluate, and how many comparable units carry that statement.
    private func evidenceLine(_ trend: ILTrainingTrendAssessment) -> some View {
        Text("\(trend.exercisesWithSufficientHistory) von \(ilCount(trend.analyzedExerciseCount, "Übung", "Übungen")) auswertbar · \(ilCount(trend.dataQuality.comparableUnitCount, "vergleichbare Einheit", "vergleichbare Einheiten"))")
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .fixedSize(horizontal: false, vertical: true)
    }

    /// Everything the core computed beyond the compact state. Nothing is dropped: the
    /// long explanation, the confidence, the strongest decline, every reason, every
    /// exercise and every data-quality note stay one tap away.
    @ViewBuilder
    private func detailsDisclosure(_ trend: ILTrainingTrendAssessment) -> some View {
        if hasDetails(trend) {
            DisclosureGroup(isExpanded: $showsDetails) {
                VStack(alignment: .leading, spacing: 10) {
                    if trend.status == .insufficientData {
                        Text("Es fehlen vergleichbare Einheiten für einen belastbaren Trend.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    Text("Evidenz: \(ilConfidenceText(trend.confidence))")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    if trend.repeatedDeclineExerciseCount > 0 {
                        Text("\(trend.repeatedDeclineExerciseCount) Übung\(trend.repeatedDeclineExerciseCount == 1 ? "" : "en") mit wiederholtem Rückgang")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    if let strongest = trend.strongestDecline, let change = strongest.changePercent {
                        Text("Stärkster Rückgang: \(strongest.exerciseName) (\(changeText(change)) über \(ilCount(strongest.comparableUnitCount, "Einheit", "Einheiten")))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    allReasons(trend)
                    allExercises(trend)
                    dataQualityNotes(trend)
                }
                .padding(.top, 4)
                .frame(maxWidth: .infinity, alignment: .leading)
            } label: {
                Text("Details")
                    .font(.subheadline.weight(.semibold))
            }
        }
    }

    /// The disclosure is only rendered when it actually has something to reveal.
    private func hasDetails(_ trend: ILTrainingTrendAssessment) -> Bool {
        trend.status == .insufficientData
            || !trend.reasons.isEmpty
            || !trend.exercises.isEmpty
            || !trend.dataQuality.notes.isEmpty
            || trend.repeatedDeclineExerciseCount > 0
            || trend.strongestDecline != nil
    }

    private func reasonLine(_ text: String) -> some View {
        Text("· \(text)")
            .font(.caption)
            .foregroundStyle(.secondary)
            .fixedSize(horizontal: false, vertical: true)
    }

    @ViewBuilder
    private func allReasons(_ trend: ILTrainingTrendAssessment) -> some View {
        let texts = ilReasonTexts(trend.reasons)
        if !texts.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                Text("Alle Gründe")
                    .font(.caption.weight(.semibold))
                ForEach(texts, id: \.self) { reasonLine($0) }
            }
        }
    }

    @ViewBuilder
    private func allExercises(_ trend: ILTrainingTrendAssessment) -> some View {
        if !trend.exercises.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
                Text("Übungen (\(trend.exercises.count))")
                    .font(.caption.weight(.semibold))
                ForEach(trend.exercises, id: \.exerciseId) { exercise in
                    VStack(alignment: .leading, spacing: 1) {
                        Text("\(exercise.exerciseName) · \(ilExerciseTrendStatusText(exercise.status))")
                            .font(.caption)
                        Text("\(changeText(exercise.changePercent)) über \(ilCount(exercise.comparableUnitCount, "Einheit", "Einheiten")) · \(ilConfidenceText(exercise.confidence))")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func dataQualityNotes(_ trend: ILTrainingTrendAssessment) -> some View {
        if !trend.dataQuality.notes.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                Text("Datenqualität")
                    .font(.caption.weight(.semibold))
                ForEach(Array(trend.dataQuality.notes.enumerated()), id: \.offset) { _, note in
                    Text("· \(ilDataQualityNoteText(note))")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private func accent(_ trend: ILTrainingTrendAssessment) -> IronLogTheme.Accent {
        switch trend.status {
        case .multipleExerciseDecline: return .warning
        case .singleExerciseDecline: return .secondary
        case .noNotableStrain: return .success
        case .insufficientData, .unknown: return .secondary
        }
    }

    private func changeText(_ value: Double?) -> String {
        guard let value, value.isFinite else { return "keine Vergleichszahl" }
        let sign = value > 0 ? "+" : ""
        return "\(sign)\(value.formatted(.number.precision(.fractionLength(1)))) %"
    }
}

/// The optional, dated self-assessment for exactly one local day.
///
/// Raw answers come from the stored check-in, the evaluation from the shared
/// daily-form projection. The two are shown separately so a missing answer is
/// never presented as an assessment.
private struct IOSDashboardTodayCheckInCard: View {
    let localDate: String
    let checkIn: ILReadinessCheckIn?
    let dailyForm: ILDailyFormAssessment?
    let onEdit: () -> Void

    var body: some View {
        IronLogCard(title: "Tagesform", subtitle: "Heute · \(ilCheckInDateText(localDate))") {
            VStack(alignment: .leading, spacing: 10) {
                if let checkIn, checkIn.hasAnyAnswer {
                    answerList(checkIn)
                    if let dailyForm {
                        evaluation(dailyForm)
                    }
                    Button("Bearbeiten", action: onEdit)
                        .buttonStyle(.bordered)
                        .accessibilityHint("Öffnet den heutigen Check-in zum Ändern oder Löschen")
                } else {
                    Text("Freiwillige Selbsteinschätzung. Ohne Angabe bleibt alles offen; es wird kein Wert vorausgefüllt.")
                        .font(.body)
                        .foregroundStyle(.secondary)
                    Button("Check-in für heute", action: onEdit)
                        .buttonStyle(.borderedProminent)
                }
            }
        }
    }

    @ViewBuilder
    private func answerList(_ checkIn: ILReadinessCheckIn) -> some View {
        let rows = answerRows(checkIn)
        ForEach(rows, id: \.label) { row in
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(row.label)
                    .font(.subheadline.weight(.semibold))
                Spacer(minLength: 8)
                Text(row.value)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func answerRows(_ checkIn: ILReadinessCheckIn) -> [(label: String, value: String)] {
        var rows: [(label: String, value: String)] = []
        if let sleep = checkIn.sleepQuality {
            rows.append((label: "Schlafqualität", value: ilCheckInScaleText(sleep, dimension: .sleepQuality)))
        }
        if let energy = checkIn.energy {
            rows.append((label: "Energie", value: ilCheckInScaleText(energy, dimension: .energy)))
        }
        if let stress = checkIn.stress {
            rows.append((label: "Stress", value: ilCheckInScaleText(stress, dimension: .stress)))
        }
        let soreness = checkIn.muscleSoreness
            .filter { (1...5).contains($0.value) }
            .sorted { $0.key < $1.key }
        if !soreness.isEmpty {
            let text = soreness
                .map { "\(ilMuscleDisplayName($0.key)) \($0.value)/5" }
                .joined(separator: ", ")
            rows.append((label: "Muskelkater", value: text))
        }
        return rows
    }

    @ViewBuilder
    private func evaluation(_ dailyForm: ILDailyFormAssessment) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(dailyForm.signals.enumerated()), id: \.offset) { _, signal in
                Text("\(ilDailyFormDimensionText(signal.dimension)): \(ilDailyFormStateText(signal.state)) (\(signal.value)/5)")
                    .font(.caption)
                    .foregroundStyle(signal.state == .concern ? Color.orange : Color.secondary)
            }
        }
        Text("Aussagekraft: \(ilConfidenceText(dailyForm.confidence)). Der Trainingstrend bleibt davon unabhängig.")
            .font(.caption)
            .foregroundStyle(.secondary)
    }
}

/// Facts for the muscles of today's plan, available before the workout starts.
///
/// The shared projection already resolves the plan (running session first, then the
/// rotation suggestion). This card shows last load and the rolling 7-day set count
/// as recorded facts; it deliberately contains no MEV/MAV-derived recovery value.
private struct IOSDashboardTodayMuscleCard: View {
    let muscleGroups: [ILMuscleGroupContext]

    /// Today's planned muscles come first and are marked. The projection also reports
    /// muscles that only have history, so those stay visible as facts but are never
    /// presented as if they were part of today's plan.
    private var plannedMuscles: [ILMuscleGroupContext] {
        muscleGroups.filter { $0.plannedToday == true }
    }

    private var otherMuscles: [ILMuscleGroupContext] {
        muscleGroups.filter { $0.plannedToday != true }
    }

    var body: some View {
        IronLogCard(title: "Heutiges Training", subtitle: subtitle) {
            if muscleGroups.isEmpty {
                Text("Kein Tagesbezug: Es ist keine Einheit geplant und keine Muskelgruppe gemeldet.")
                    .font(.body)
                    .foregroundStyle(.secondary)
            } else {
                VStack(alignment: .leading, spacing: 12) {
                    if !plannedMuscles.isEmpty {
                        sectionHeader("Heute geplant")
                        ForEach(plannedMuscles, id: \.muscleGroup) { muscle in
                            row(muscle, isPlanned: true)
                        }
                    }
                    if !otherMuscles.isEmpty {
                        sectionHeader(plannedMuscles.isEmpty ? "Erfasste Muskeln" : "Weitere erfasste Muskeln")
                        ForEach(otherMuscles, id: \.muscleGroup) { muscle in
                            row(muscle, isPlanned: false)
                        }
                    }
                    Text("Fakten aus deiner Historie: letzte Belastung und Arbeitssätze der letzten 7 Tage. Keine Erholungsprognose.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private func sectionHeader(_ text: String) -> some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundStyle(.secondary)
            .textCase(.uppercase)
    }

    private var subtitle: String? {
        guard !muscleGroups.isEmpty else { return nil }
        return plannedMuscles.isEmpty
            ? "Erfasste Muskelgruppen"
            : "Heute geplant: \(plannedMuscles.count)"
    }

    @ViewBuilder
    private func row(_ muscle: ILMuscleGroupContext, isPlanned: Bool) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(ilMuscleDisplayName(muscle.muscleGroup))
                    .font(.subheadline.weight(.semibold))
                if isPlanned {
                    Text("geplant")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.tint)
                }
                Spacer(minLength: 8)
                if let soreness = muscle.soreness {
                    Text("Muskelkater \(soreness)/5")
                        .font(.caption)
                        .foregroundStyle(soreness >= 4 ? Color.orange : Color.secondary)
                }
            }

            Text("\(ilPastDayText(muscle.lastTrainedEpochMillis)) · \(ilSetCountText(muscle.setsInWindow)) in den letzten 7 Tagen")
                .font(.caption)
                .foregroundStyle(.secondary)

            if muscle.setsToday > 0 {
                Text("Heute bereits \(ilSetCountText(muscle.setsToday))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            let flags = muscle.flags.filter { $0 != .unknown }
            if !flags.isEmpty {
                Text(flags.map(ilMuscleFlagText).joined(separator: " · "))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

/// The check-in form for one already-fixed local date.
///
/// Every dimension starts unanswered and stays unanswered until the athlete taps a
/// value. "Abbrechen" and the swipe-down gesture dismiss without writing, so a
/// skipped form never deletes answers that were already stored.
private struct IOSDashboardCheckInSheet: View {
    let localDate: String

    @Environment(IOSTrainingStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    @State private var sleepQuality: Int?
    @State private var energy: Int?
    @State private var stress: Int?
    @State private var soreness: [String: Int] = [:]
    @State private var recordedAtEpochMillis: Int64?
    @State private var didLoad = false
    @State private var isSaving = false
    @State private var saveFailed = false

    private var existingCheckIn: ILReadinessCheckIn? {
        store.readinessData.checkIn(for: localDate)
    }

    private var hasAnyAnswer: Bool {
        sleepQuality != nil || energy != nil || stress != nil || !soreness.isEmpty
    }

    /// Today's planned muscles first, so the form opens on what the athlete will train.
    private var orderedMuscleGroups: [String] {
        let planned = store.readiness?.muscleGroups.map(\.muscleGroup) ?? []
        return planned + ilReadinessMuscleGroups.filter { !planned.contains($0) }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ILCheckInScaleRow(title: "Schlafqualität", dimension: .sleepQuality, value: $sleepQuality)
                    ILCheckInScaleRow(title: "Energie", dimension: .energy, value: $energy)
                    ILCheckInScaleRow(title: "Stress", dimension: .stress, value: $stress)
                } header: {
                    Text("Tagesform · \(ilCheckInDateText(localDate))")
                } footer: {
                    Text("Alle Angaben sind freiwillig. Eine nicht beantwortete Zeile bleibt offen und wird nicht als Wert gespeichert.")
                }

                Section {
                    ForEach(orderedMuscleGroups, id: \.self) { muscle in
                        ILCheckInScaleRow(
                            title: ilMuscleDisplayName(muscle),
                            dimension: .soreness,
                            value: sorenessBinding(for: muscle)
                        )
                    }
                } header: {
                    Text("Muskelkater je Muskelgruppe")
                } footer: {
                    Text("1 bedeutet kein bis sehr geringer, 5 sehr starker Muskelkater. Nicht gemeldete Muskeln bleiben offen.")
                }

                if saveFailed {
                    Section {
                        Text(store.errorMessage ?? "Speichern fehlgeschlagen. Bitte erneut versuchen.")
                            .font(.footnote)
                            .foregroundStyle(.orange)
                    }
                }

                if existingCheckIn != nil {
                    Section {
                        Button("Check-in für heute löschen", role: .destructive) {
                            Task { await deleteCheckIn() }
                        }
                        .disabled(isSaving)
                    } footer: {
                        Text("Löschen entfernt die gespeicherten Antworten dieses Tages. Abbrechen lässt sie unverändert.")
                    }
                }
            }
            .navigationTitle("Check-in")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                        .disabled(isSaving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Speichern") {
                        Task { await save() }
                    }
                    .disabled(isSaving || !hasAnyAnswer)
                }
            }
            .overlay {
                if isSaving {
                    ProgressView()
                        .controlSize(.regular)
                }
            }
        }
        .task { loadExistingAnswers() }
    }

    private func sorenessBinding(for muscle: String) -> Binding<Int?> {
        Binding(
            get: { soreness[muscle] },
            set: { newValue in
                if let newValue, (1...5).contains(newValue) {
                    soreness[muscle] = newValue
                } else {
                    soreness[muscle] = nil
                }
            }
        )
    }

    /// Prefills only from stored answers. A day without a record keeps every field open.
    private func loadExistingAnswers() {
        guard !didLoad else { return }
        didLoad = true
        guard let existing = existingCheckIn else { return }
        sleepQuality = existing.sleepQuality
        energy = existing.energy
        stress = existing.stress
        soreness = existing.muscleSoreness.filter { (1...5).contains($0.value) }
        recordedAtEpochMillis = existing.recordedAtEpochMillis
    }

    private func save() async {
        guard hasAnyAnswer, !isSaving else { return }
        isSaving = true
        saveFailed = false
        defer { isSaving = false }

        let saved = await store.upsertCheckIn(
            localDate: localDate,
            sleepQuality: sleepQuality,
            energy: energy,
            stress: stress,
            muscleSoreness: soreness.filter { (1...5).contains($0.value) },
            recordedAtEpochMillis: recordedAtEpochMillis
        )
        if saved {
            dismiss()
        } else {
            saveFailed = true
        }
    }

    private func deleteCheckIn() async {
        guard !isSaving else { return }
        isSaving = true
        saveFailed = false
        defer { isSaving = false }

        if await store.deleteCheckIn(localDate: localDate) {
            dismiss()
        } else {
            saveFailed = true
        }
    }
}

/// One 1...5 self-assessment row. Tapping the active value clears the answer again,
/// so "open" stays reachable after a mis-tap.
private struct ILCheckInScaleRow: View {
    let title: String
    let dimension: ILDailyFormDimension
    @Binding var value: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(title)
                Spacer(minLength: 8)
                Text(value.map { ilCheckInScaleText($0, dimension: dimension) } ?? "offen")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            HStack(spacing: 8) {
                ForEach(1...5, id: \.self) { step in
                    Button {
                        value = value == step ? nil : step
                    } label: {
                        Text("\(step)")
                            .font(.subheadline.weight(.semibold))
                            .frame(minWidth: 32, minHeight: 32)
                    }
                    .buttonStyle(.bordered)
                    .tint(value == step ? Color.accentColor : Color.secondary)
                    .accessibilityLabel("\(title): \(step) von 5")
                    .accessibilityAddTraits(value == step ? [.isSelected] : [])
                }
            }
        }
        .padding(.vertical, 2)
    }
}

private struct IOSDashboardWorkoutCounts: View {
    let week: Int
    let month: Int
    let lastSessionDate: String?

    var body: some View {
        IronLogCard(title: "Trainingsrhythmus", tone: .muted) {
            HStack(spacing: 0) {
                metric(title: "Diese Woche", value: week)
                Divider().frame(height: 42)
                metric(title: "Dieser Monat", value: month)
            }
            if let lastSessionDate = ilAnalyticsDateText(lastSessionDate) {
                Text("Zuletzt am \(lastSessionDate)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func metric(title: String, value: Int) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text("\(value)")
                .font(.title2.weight(.bold))
                .monospacedDigit()
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct IOSDashboardPendingProgressions: View {
    let count: Int
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: "arrow.up.right.circle.fill")
                    .font(.title3)
                    .foregroundStyle(.orange)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Progression prüfen")
                        .font(.headline)
                    Text("\(count) Vorschlag\(count == 1 ? "" : "e") wartet auf deine Entscheidung.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right")
                    .foregroundStyle(.secondary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(16)
        .background(.orange.opacity(0.10), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .accessibilityHint("Öffnet die offenen Progressionsvorschläge")
    }
}

private struct IOSDashboardVolumeExplanation: View {
    var body: some View {
        Text("MEV ist das minimale wirksame Volumen, MAV der typische Zielbereich und MRV die obere Regenerationsgrenze. Der Trend stammt aus den gültigen Arbeitssätzen dieser Woche.")
            .font(.caption)
            .foregroundStyle(.secondary)
            .padding(.horizontal, 4)
    }
}

private struct IOSDashboardRecentRecords: View {
    let records: [ILTrainingAnalyticsRecord]
    let unitSystem: String

    var body: some View {
        IronLogCard(title: "Neue Rekorde", subtitle: records.isEmpty ? nil : "Zuletzt erfasst") {
            if records.isEmpty {
                Text("Noch keine persönlichen Rekorde gespeichert.")
                    .font(.body)
                    .foregroundStyle(.secondary)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .top, spacing: 12) {
                        ForEach(records) { record in
                            VStack(alignment: .leading, spacing: 7) {
                                Text(record.exerciseName ?? "Übung \(record.exerciseId)")
                                    .font(.headline)
                                    .lineLimit(2)
                                Text(ilRecordTypeText(record.type))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Text(ilRecordValueText(type: record.type, value: record.value, unitSystem: unitSystem))
                                    .font(.title3.weight(.bold))
                                    .foregroundStyle(.orange)
                                Text(ilAnalyticsDateText(record.achievedAt) ?? record.achievedAt)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            .frame(width: 150, alignment: .leading)
                            .padding(12)
                            .background(.orange.opacity(0.08), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
        }
    }
}
