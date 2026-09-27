import SwiftUI

/// Semantic colors and layout tokens shared by the native iOS surfaces.
///
/// The available schemes intentionally mirror the shared Android
/// `ThemeScheme` enum. A theme keeps both appearances so a live light/dark
/// change never has to rebuild or infer colors from the current system mode.
struct IronLogTheme {
    enum Scheme: String, CaseIterable, Sendable {
        case amber = "AMBER"
        case deepCyan = "DEEP_CYAN"
        case neonRed = "NEON_RED"
        case forge = "FORGE"
        case raster = "RASTER"
        case tide = "TIDE"
        case pulse = "PULSE"
    }

    enum Accent {
        case primary
        case secondary
        case success
        case warning
        case danger
        case information
    }

    struct Palette {
        let background: Color
        let surface: Color
        let surfaceElevated: Color
        let surfaceMuted: Color
        let textPrimary: Color
        let textSecondary: Color
        let separator: Color
        let progressTrack: Color
        let primary: Color
        let accentText: Color
        let buttonText: Color
        let secondary: Color
        let success: Color
        let warning: Color
        let danger: Color
        let information: Color

        func accent(_ accent: Accent) -> Color {
            switch accent {
            case .primary:
                primary
            case .secondary:
                secondary
            case .success:
                success
            case .warning:
                warning
            case .danger:
                danger
            case .information:
                information
            }
        }
    }

    struct Metrics {
        let cardCornerRadius: CGFloat
        let controlCornerRadius: CGFloat
        let contentSpacing: CGFloat
        let compactSpacing: CGFloat
        let cardPadding: CGFloat
    }

    let scheme: Scheme
    let light: Palette
    let dark: Palette
    let metrics: Metrics
    let reducedMotion: Bool

    /// The default Ember/Amber palette used by the shared Android model.
    static let ember = IronLogTheme()

    init(scheme: Scheme = .amber, reducedMotion: Bool = false) {
        self.scheme = scheme
        self.reducedMotion = reducedMotion
        let palettes = Self.palettes(for: scheme)
        light = palettes.light
        dark = palettes.dark
        metrics = Metrics(
            cardCornerRadius: 18,
            controlCornerRadius: 12,
            contentSpacing: 16,
            compactSpacing: 8,
            cardPadding: 16
        )
    }

    /// Creates a theme from the persisted shared preference value.
    /// Unknown values fail closed to the shared default instead of inventing a
    /// new visual scheme.
    init(schemeName: String, reducedMotion: Bool = false) {
        self.init(
            scheme: Scheme(rawValue: schemeName) ?? .amber,
            reducedMotion: reducedMotion
        )
    }

    func palette(for colorScheme: ColorScheme) -> Palette {
        colorScheme == .dark ? dark : light
    }

    private static func palettes(for scheme: Scheme) -> (light: Palette, dark: Palette) {
        switch scheme {
        case .amber:
            return (
                palette(
                    background: 0xFFF8F0,
                    surface: 0xFFFFFF,
                    elevated: 0xFFFCF7,
                    muted: 0xFFF3E5,
                    text: 0x1A0F00,
                    primary: 0xFF6B00,
                    accentText: 0xA83F00,
                    buttonText: 0x121212,
                    secondary: 0x0D9488,
                    success: 0x166534,
                    warning: 0x92400E,
                    danger: 0xB3261E,
                    information: 0x0284C7,
                    dark: false
                ),
                palette(
                    background: 0x0E131A,
                    surface: 0x191F29,
                    elevated: 0x2C323E,
                    muted: 0x202631,
                    text: 0xF5F4EF,
                    primary: 0xF58B20,
                    accentText: 0xFFC98A,
                    buttonText: 0x121212,
                    secondary: 0x14B8A6,
                    success: 0x34D399,
                    warning: 0xFBBF24,
                    danger: 0xF87171,
                    information: 0x38BDF8,
                    dark: true
                )
            )

        case .deepCyan:
            return (
                palette(
                    background: 0xF2F4F8,
                    surface: 0xF7F9FC,
                    elevated: 0xFCFDFF,
                    muted: 0xECF1F8,
                    text: 0x17181C,
                    primary: 0x00838F,
                    accentText: 0x00636D,
                    buttonText: 0x000000,
                    secondary: 0x455A64,
                    success: 0x2E7D32,
                    warning: 0xEF6C00,
                    danger: 0xB3261E,
                    information: 0x00838F,
                    dark: false
                ),
                palette(
                    background: 0x0E1117,
                    surface: 0x12161E,
                    elevated: 0x1A202A,
                    muted: 0x161B24,
                    text: 0xE5E8EF,
                    primary: 0x4DD0E1,
                    accentText: 0xA0F4FF,
                    buttonText: 0x121212,
                    secondary: 0xB0BEC5,
                    success: 0x81C784,
                    warning: 0xFFB74D,
                    danger: 0xF2B8B5,
                    information: 0x4DD0E1,
                    dark: true
                )
            )

        case .neonRed:
            return (
                palette(
                    background: 0xF2F4F8,
                    surface: 0xF7F9FC,
                    elevated: 0xFCFDFF,
                    muted: 0xECF1F8,
                    text: 0x17181C,
                    primary: 0xD50000,
                    accentText: 0xA80000,
                    buttonText: 0xFFFFFF,
                    secondary: 0x212121,
                    success: 0x2E7D32,
                    warning: 0xEF6C00,
                    danger: 0xB3261E,
                    information: 0xD50000,
                    dark: false
                ),
                palette(
                    background: 0x0E1117,
                    surface: 0x12161E,
                    elevated: 0x1A202A,
                    muted: 0x161B24,
                    text: 0xE5E8EF,
                    primary: 0xFF5252,
                    accentText: 0xFFB0B0,
                    buttonText: 0x121212,
                    secondary: 0x9E9E9E,
                    success: 0x81C784,
                    warning: 0xFFB74D,
                    danger: 0xF2B8B5,
                    information: 0xFF5252,
                    dark: true
                )
            )

        case .forge:
            return (
                palette(
                    background: 0xF4F4F6,
                    surface: 0xFFFFFF,
                    elevated: 0xFCFDFF,
                    muted: 0xECF1F8,
                    text: 0x17181C,
                    primary: 0xFF7A1A,
                    accentText: 0xA34400,
                    buttonText: 0x121212,
                    secondary: 0x2E2E34,
                    success: 0x3DFF88,
                    warning: 0xFF7A1A,
                    danger: 0xB3261E,
                    information: 0x3DFF88,
                    dark: false
                ),
                palette(
                    background: 0x060606,
                    surface: 0x121214,
                    elevated: 0x1C1C20,
                    muted: 0x161618,
                    text: 0xF4F4F5,
                    primary: 0xFF7A1A,
                    accentText: 0xFFB37A,
                    buttonText: 0x121212,
                    secondary: 0x2E2E34,
                    success: 0x3DFF88,
                    warning: 0xFF7A1A,
                    danger: 0xF87171,
                    information: 0x3DFF88,
                    dark: true
                )
            )

        case .raster:
            return (
                palette(
                    background: 0xF8FAFC,
                    surface: 0xFFFFFF,
                    elevated: 0xFCFDFF,
                    muted: 0xECF1F8,
                    text: 0x17181C,
                    primary: 0x3B82F6,
                    accentText: 0x1D4ED8,
                    buttonText: 0x121212,
                    secondary: 0x64748B,
                    success: 0x10B981,
                    warning: 0x3B82F6,
                    danger: 0xB3261E,
                    information: 0x3B82F6,
                    dark: false
                ),
                palette(
                    background: 0x08090A,
                    surface: 0x121316,
                    elevated: 0x1A1C22,
                    muted: 0x15171C,
                    text: 0xF1F5F9,
                    primary: 0x3B82F6,
                    accentText: 0x9EC5FF,
                    buttonText: 0x121212,
                    secondary: 0x64748B,
                    success: 0x10B981,
                    warning: 0x3B82F6,
                    danger: 0xF87171,
                    information: 0x3B82F6,
                    dark: true
                )
            )

        case .tide:
            return (
                palette(
                    background: 0xF0FDF4,
                    surface: 0xFFFFFF,
                    elevated: 0xFCFDFF,
                    muted: 0xECF1F8,
                    text: 0x17181C,
                    primary: 0x00F5A0,
                    accentText: 0x007A50,
                    buttonText: 0x121212,
                    secondary: 0x06B6D4,
                    success: 0x2E7D32,
                    warning: 0x00F5A0,
                    danger: 0xB3261E,
                    information: 0x06B6D4,
                    dark: false
                ),
                palette(
                    background: 0x040908,
                    surface: 0x091412,
                    elevated: 0x102420,
                    muted: 0x0C1B18,
                    text: 0xECFDF5,
                    primary: 0x00F5A0,
                    accentText: 0xA4FFD7,
                    buttonText: 0x121212,
                    secondary: 0x06B6D4,
                    success: 0x00F5A0,
                    warning: 0x06B6D4,
                    danger: 0xF87171,
                    information: 0x06B6D4,
                    dark: true
                )
            )

        case .pulse:
            return (
                palette(
                    background: 0xFFF1F2,
                    surface: 0xFFFFFF,
                    elevated: 0xFCFDFF,
                    muted: 0xECF1F8,
                    text: 0x17181C,
                    primary: 0xFF3366,
                    accentText: 0xB00036,
                    buttonText: 0x121212,
                    secondary: 0xA855F7,
                    success: 0x2E7D32,
                    warning: 0xFF3366,
                    danger: 0xB3261E,
                    information: 0x06B6D4,
                    dark: false
                ),
                palette(
                    background: 0x0B080C,
                    surface: 0x171219,
                    elevated: 0x221A26,
                    muted: 0x1B151E,
                    text: 0xFDF2F8,
                    primary: 0xFF3366,
                    accentText: 0xFFB4D0,
                    buttonText: 0x121212,
                    secondary: 0xA855F7,
                    success: 0x34D399,
                    warning: 0xFBBF24,
                    danger: 0xFF3366,
                    information: 0x06B6D4,
                    dark: true
                )
            )
        }
    }

    private static func palette(
        background: UInt32,
        surface: UInt32,
        elevated: UInt32,
        muted: UInt32,
        text: UInt32,
        primary: UInt32,
        accentText: UInt32,
        buttonText: UInt32,
        secondary: UInt32,
        success: UInt32,
        warning: UInt32,
        danger: UInt32,
        information: UInt32,
        dark: Bool
    ) -> Palette {
        let textColor = Color.ironLogHex(text)
        return Palette(
            background: Color.ironLogHex(background),
            surface: Color.ironLogHex(surface),
            surfaceElevated: Color.ironLogHex(elevated),
            surfaceMuted: Color.ironLogHex(muted),
            textPrimary: textColor,
            textSecondary: Color.ironLogHex(dark ? 0xB7BBC4 : 0x454B54),
            separator: textColor.opacity(dark ? 0.16 : 0.14),
            progressTrack: textColor.opacity(dark ? 0.14 : 0.12),
            primary: Color.ironLogHex(primary),
            accentText: Color.ironLogHex(accentText),
            buttonText: Color.ironLogHex(buttonText),
            secondary: Color.ironLogHex(secondary),
            success: Color.ironLogHex(success),
            warning: Color.ironLogHex(warning),
            danger: Color.ironLogHex(danger),
            information: Color.ironLogHex(information)
        )
    }
}

private struct IronLogThemeEnvironmentKey: EnvironmentKey {
    static let defaultValue = IronLogTheme.ember
}

extension EnvironmentValues {
    var ironLogTheme: IronLogTheme {
        get { self[IronLogThemeEnvironmentKey.self] }
        set { self[IronLogThemeEnvironmentKey.self] = newValue }
    }
}

extension View {
    /// Installs an IronLog theme for this view hierarchy.
    func ironLogTheme(_ theme: IronLogTheme = .ember) -> some View {
        modifier(IronLogThemeModifier(theme: theme))
    }
}

private struct IronLogThemeModifier: ViewModifier {
    let theme: IronLogTheme
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        content
            .environment(\.ironLogTheme, theme)
            .tint(theme.palette(for: colorScheme).primary)
    }
}

private struct IronLogAccentTextModifier: ViewModifier {
    @Environment(\.ironLogTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        content.foregroundStyle(theme.palette(for: colorScheme).accentText)
    }
}

private struct IronLogSecondaryTextModifier: ViewModifier {
    @Environment(\.ironLogTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        content.foregroundStyle(theme.palette(for: colorScheme).textSecondary)
    }
}

private struct IronLogButtonTextModifier: ViewModifier {
    @Environment(\.ironLogTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        content.foregroundStyle(theme.palette(for: colorScheme).buttonText)
    }
}

extension View {
    func ironLogAccentText() -> some View { modifier(IronLogAccentTextModifier()) }
    func ironLogSecondaryText() -> some View { modifier(IronLogSecondaryTextModifier()) }
    func ironLogButtonText() -> some View { modifier(IronLogButtonTextModifier()) }
}

private extension Color {
    static func ironLogHex(_ value: UInt32) -> Color {
        Color(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }
}
