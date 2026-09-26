import SwiftUI

/// Overall look, mirrors the shared `AppearanceStyle` preference.
enum IronLogAppearance: String {
    case ember = "EMBER"
    case liquidGlass = "LIQUID_GLASS"

    init(rawPreference: String) {
        self = IronLogAppearance(rawValue: rawPreference) ?? .ember
    }
}

private struct IronLogAppearanceKey: EnvironmentKey {
    static let defaultValue: IronLogAppearance = .ember
}

extension EnvironmentValues {
    var ironLogAppearance: IronLogAppearance {
        get { self[IronLogAppearanceKey.self] }
        set { self[IronLogAppearanceKey.self] = newValue }
    }
}

/// Glass strength, same levels as Android's `GlassLevel`.
/// Only `strong` uses a system material (real blur); `standard` and `tint` stay
/// translucent so long lists remain cheap to scroll.
enum IronLogGlassLevel {
    case standard
    case strong
    case tint
}

/// Colored backdrop of the Liquid Glass look. Radial gradients instead of
/// blurred shapes: identical look, no blur cost. The main blob follows the
/// active accent.
struct IronLogLiquidBackground: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.ironLogTheme) private var theme

    var body: some View {
        let dark = colorScheme == .dark
        let base = dark ? Color(red: 7 / 255, green: 8 / 255, blue: 12 / 255) : Color(red: 238 / 255, green: 240 / 255, blue: 246 / 255)
        let strength = dark ? 1.0 : 0.55
        let accent = theme.palette(for: colorScheme).primary
        let teal = Color(red: 46 / 255, green: 196 / 255, blue: 182 / 255)
        let violet = Color(red: 109 / 255, green: 91 / 255, blue: 255 / 255)

        Canvas { context, size in
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(base))
            func blob(_ color: Color, x: Double, y: Double, radius: Double, alpha: Double) {
                let center = CGPoint(x: size.width * x, y: size.height * y)
                let r = size.width * radius
                let rect = CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2)
                context.fill(
                    Path(ellipseIn: rect),
                    with: .radialGradient(
                        Gradient(stops: [
                            .init(color: color.opacity(alpha * strength), location: 0),
                            .init(color: color.opacity(alpha * strength * 0.45), location: 0.55),
                            .init(color: color.opacity(0), location: 1)
                        ]),
                        center: center,
                        startRadius: 0,
                        endRadius: r
                    )
                )
            }
            blob(accent, x: 0.25, y: 0.06, radius: 1.0, alpha: 0.85)
            blob(teal, x: 0.98, y: 0.48, radius: 0.9, alpha: 0.5)
            blob(violet, x: 0.12, y: 0.96, radius: 0.95, alpha: 0.5)
            // Soft veil at the bottom keeps text on lower surfaces readable.
            context.fill(
                Path(CGRect(origin: .zero, size: size)),
                with: .linearGradient(
                    Gradient(stops: [
                        .init(color: base.opacity(0), location: 0.55),
                        .init(color: base.opacity(0.6), location: 1)
                    ]),
                    startPoint: .zero,
                    endPoint: CGPoint(x: 0, y: size.height)
                )
            )
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }
}

private struct IronLogLiquidGlassModifier<S: InsettableShape>: ViewModifier {
    let level: IronLogGlassLevel
    let shape: S
    let tint: Color?

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.ironLogTheme) private var theme
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    func body(content: Content) -> some View {
        let dark = colorScheme == .dark
        content
            .background { surface(dark: dark) }
            .clipShape(shape)
            .overlay {
                shape.strokeBorder(
                    LinearGradient(colors: edge(dark: dark), startPoint: .top, endPoint: .bottom),
                    lineWidth: 1
                )
            }
    }

    @ViewBuilder
    private func surface(dark: Bool) -> some View {
        let accent = tint ?? theme.palette(for: colorScheme).primary
        ZStack {
            if reduceTransparency || theme.reducedMotion {
                shape.fill(opaque(dark: dark, accent: accent))
            } else {
                switch level {
                case .strong:
                    shape.fill(.ultraThinMaterial)
                    shape.fill(dark ? Color(red: 16 / 255, green: 18 / 255, blue: 26 / 255).opacity(0.18) : Color.white.opacity(0.5))
                case .standard:
                    shape.fill(dark ? Color(red: 16 / 255, green: 18 / 255, blue: 26 / 255).opacity(0.28) : Color.white.opacity(0.35))
                case .tint:
                    shape.fill(accent.opacity(dark ? 0.30 : 0.22))
                }
            }
            shape.fill(
                LinearGradient(colors: sheen(dark: dark), startPoint: .topLeading, endPoint: .bottomTrailing)
            )
        }
    }

    private func opaque(dark: Bool, accent: Color) -> Color {
        switch level {
        case .standard:
            return dark ? Color(red: 26 / 255, green: 28 / 255, blue: 38 / 255) : Color.white
        case .strong:
            return dark ? Color(red: 34 / 255, green: 36 / 255, blue: 48 / 255) : Color.white
        case .tint:
            return accent.opacity(dark ? 0.45 : 0.30)
        }
    }

    private func sheen(dark: Bool) -> [Color] {
        dark
            ? [Color.white.opacity(0.20), Color.white.opacity(0.05), Color.white.opacity(0.03)]
            : [Color.white.opacity(0.70), Color.white.opacity(0.30), Color.white.opacity(0.20)]
    }

    private func edge(dark: Bool) -> [Color] {
        dark
            ? [Color.white.opacity(0.42), Color.white.opacity(0.10), Color.black.opacity(0.30)]
            : [Color.white, Color.white.opacity(0.6), Color.black.opacity(0.06)]
    }
}

extension View {
    /// Liquid Glass surface: translucent tint, sheen from the top leading corner,
    /// bright top edge and dark bottom edge. Falls back to an opaque tint with
    /// "Reduce Transparency" or reduced motion.
    func liquidGlass<S: InsettableShape>(
        _ level: IronLogGlassLevel = .standard,
        in shape: S,
        tint: Color? = nil
    ) -> some View {
        modifier(IronLogLiquidGlassModifier(level: level, shape: shape, tint: tint))
    }
}
