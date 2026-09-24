import SwiftUI

struct IOSWorkoutStartView: View {
    let plans: [ILTrainingPlan]
    let onStartFree: (String) -> Void
    let onStartPlan: (ILTrainingPlan) -> Void

    @State private var freeWorkoutName = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Bereit für dein Training?")
                        .font(.largeTitle.bold())
                    Text("Starte eine freie Session oder wähle einen gespeicherten Plan. Die Planziele werden beim Start für diese Session übernommen.")
                        .font(.body)
                        .foregroundStyle(.secondary)
                }

                IronLogCard(title: "Freies Workout", subtitle: "Übungen fügst du während des Trainings hinzu.") {
                    TextField("Name (optional)", text: $freeWorkoutName)
                        .textInputAutocapitalization(.sentences)
                        .accessibilityLabel("Name des freien Workouts")

                    Button {
                        onStartFree(freeWorkoutName.trimmingCharacters(in: .whitespacesAndNewlines))
                        freeWorkoutName = ""
                    } label: {
                        Label("Freies Workout starten", systemImage: "play.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }

                IronLogCard(title: "Trainingspläne", subtitle: plans.isEmpty ? "Noch kein Plan angelegt." : "Wähle einen Plan für diese Session.") {
                    if plans.isEmpty {
                        Label("Lege zuerst unter Pläne einen Trainingsplan an.", systemImage: "list.bullet.rectangle")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(plans) { plan in
                            Button {
                                onStartPlan(plan)
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: "list.bullet.rectangle.portrait")
                                        .foregroundStyle(.tint)
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(plan.name)
                                            .font(.body.weight(.semibold))
                                            .foregroundStyle(.primary)
                                        Text("Plan starten")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.caption.weight(.bold))
                                        .foregroundStyle(.secondary)
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(Text("Plan \(plan.name) starten"))
                        }
                    }
                }
            }
            .padding()
        }
    }
}
