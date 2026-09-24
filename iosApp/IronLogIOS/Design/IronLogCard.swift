import SwiftUI

enum IronLogCardTone {
    case standard
    case muted
    case elevated
}

/// A small surface primitive for dashboard and detail content.
///
/// The card owns presentation only. It does not add a tap action or infer a
/// state from the content it wraps, which keeps it safe for read-only metrics.
struct IronLogCard<Content: View>: View {
    let title: String?
    let subtitle: String?
    let tone: IronLogCardTone

    private let content: () -> Content

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.ironLogTheme) private var theme
    @ScaledMetric(relativeTo: .body) private var cornerRadius: CGFloat = 18
    @ScaledMetric(relativeTo: .body) private var cardPadding: CGFloat = 16

    init(
        title: String? = nil,
        subtitle: String? = nil,
        tone: IronLogCardTone = .standard,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.title = title
        self.subtitle = subtitle
        self.tone = tone
        self.content = content
    }

    var body: some View {
        let palette = theme.palette(for: colorScheme)

        VStack(alignment: .leading, spacing: theme.metrics.contentSpacing) {
            if title != nil || subtitle != nil {
                VStack(alignment: .leading, spacing: theme.metrics.compactSpacing) {
                    if let title {
                        Text(title)
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(palette.textPrimary)
                    }

                    if let subtitle {
                        Text(subtitle)
                            .font(.subheadline)
                            .foregroundStyle(palette.textSecondary)
                    }
                }
            }

            content()
        }
        .padding(min(cardPadding, 20))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(background(for: palette), in: roundedRectangle)
        .overlay {
            roundedRectangle
                .stroke(palette.separator, lineWidth: 1)
        }
        .accessibilityElement(children: .contain)
    }

    private var roundedRectangle: RoundedRectangle {
        RoundedRectangle(cornerRadius: min(cornerRadius, 24), style: .continuous)
    }

    private func background(for palette: IronLogTheme.Palette) -> Color {
        switch tone {
        case .standard:
            palette.surface
        case .muted:
            palette.surfaceMuted
        case .elevated:
            palette.surfaceElevated
        }
    }
}
