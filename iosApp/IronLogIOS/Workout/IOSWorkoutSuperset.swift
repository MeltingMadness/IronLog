import SwiftUI

/// The Android workout surface uses a violet, sky, and rose tint cycle for the
/// cards in a superset. Keeping that mapping in one small render helper makes
/// the visual cue deterministic while leaving target data untouched.
enum IOSWorkoutSupersetTint {
    static func color(for groupID: Int?, index: Int) -> Color? {
        guard groupID != nil, index >= 0 else { return nil }
        switch index % 3 {
        case 0: return .purple
        case 1: return .blue
        default: return .pink
        }
    }
}

struct IOSWorkoutSupersetHeader: View {
    let groupID: Int
    let exerciseCount: Int
    let exerciseNames: String

    @Environment(\.ironLogTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let palette = theme.palette(for: colorScheme)
        let countLabel = exerciseCount == 1 ? "Übung" : "Übungen"

        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                Image(systemName: "link")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.purple)

                Text("Superset #\(groupID)")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.purple)

                Spacer(minLength: 8)

                Text("\(exerciseCount) \(countLabel)")
                    .font(.caption)
                    .foregroundStyle(palette.textSecondary)
            }

            Text(exerciseNames)
                .font(.caption)
                .foregroundStyle(palette.textSecondary)
                .lineLimit(2)
                .truncationMode(.tail)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surfaceElevated, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(.purple.opacity(0.28), lineWidth: 1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Superset #\(groupID), \(exerciseCount) \(countLabel)")
        .accessibilityValue(exerciseNames)
    }
}
