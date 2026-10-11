import SwiftUI

extension Font {
    static func geist(_ style: Font.TextStyle, weight: Font.Weight = .regular) -> Font {
        let size: CGFloat
        switch style {
        case .largeTitle: size = 34
        case .title: size = 28
        case .title2: size = 22
        case .title3: size = 20
        case .headline: size = 17
        case .body: size = 17
        case .callout: size = 16
        case .subheadline: size = 15
        case .footnote: size = 13
        case .caption: size = 12
        case .caption2: size = 11
        default: size = 17
        }
        let resolvedWeight: Font.Weight = style == .headline && weight == .regular ? .semibold : weight
        return geist(size: size, weight: resolvedWeight, relativeTo: style)
    }

    static func geist(
        size: CGFloat,
        weight: Font.Weight = .regular,
        relativeTo style: Font.TextStyle = .body
    ) -> Font {
        let name: String
        switch weight {
        case .medium: name = "Geist-Medium"
        case .semibold: name = "Geist-SemiBold"
        case .bold: name = "Geist-Bold"
        case .heavy: name = "Geist-ExtraBold"
        case .black: name = "Geist-Black"
        default: name = "Geist-Regular"
        }
        return .custom(name, size: size, relativeTo: style)
    }
}
