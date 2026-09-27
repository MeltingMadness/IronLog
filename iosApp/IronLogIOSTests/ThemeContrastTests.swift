import SwiftUI
import UIKit
import XCTest
@testable import IronLogIOS

private struct IronLogTestRGB {
    var red: Double
    var green: Double
    var blue: Double
}

final class ThemeContrastTests: XCTestCase {
    private let teal = IronLogTestRGB(red: 46 / 255, green: 196 / 255, blue: 182 / 255)
    private let violet = IronLogTestRGB(red: 109 / 255, green: 91 / 255, blue: 255 / 255)

    func testThemeTextRolesMeetWCAGContrastAcrossGlassLevels() {
        var weakest = (ratio: Double.infinity, description: "")

        for scheme in IronLogTheme.Scheme.allCases {
            let theme = IronLogTheme(scheme: scheme)

            for colorScheme in [ColorScheme.light, .dark] {
                let dark = colorScheme == .dark
                let palette = theme.palette(for: colorScheme)
                let primary = rgb(palette.primary)
                let backdrop = backdropSamples(primary: primary, dark: dark)
                let textRoles = [
                    ("Nebentext", rgb(palette.textSecondary)),
                    ("Akzenttext", rgb(palette.accentText))
                ]

                for level in [IronLogGlassLevel.standard, .tint] {
                    for (role, foreground) in textRoles {
                        var worst = (ratio: Double.infinity, sample: backdrop[0])
                        for sample in backdrop {
                            for sheenAlpha in dark ? [0.20, 0.05, 0.03] : [0.70, 0.30, 0.20] {
                                let glass = glassColor(
                                    over: sample,
                                    primary: primary,
                                    level: level,
                                    dark: dark,
                                    sheenAlpha: sheenAlpha
                                )
                                let value = contrast(foreground, glass)
                                if value < worst.ratio {
                                    worst = (value, sample)
                                }
                            }
                        }
                        let description = "\(scheme.rawValue) \(dark ? "dunkel" : "hell") \(level) \(role)"
                        if worst.ratio < weakest.ratio {
                            weakest = (worst.ratio, description)
                        }
                        XCTAssertGreaterThanOrEqual(
                            worst.ratio,
                            4.5,
                            "\(description): \(String(format: "%.3f", worst.ratio)):1 bei x=\(worst.sample.x), y=\(worst.sample.y)"
                        )
                    }
                }

                let buttonContrast = contrast(rgb(palette.buttonText), primary)
                let buttonDescription = "\(scheme.rawValue) \(dark ? "dunkel" : "hell") Buttontext/Akzent"
                if buttonContrast < weakest.ratio {
                    weakest = (buttonContrast, buttonDescription)
                }
                XCTAssertGreaterThanOrEqual(
                    buttonContrast,
                    4.5,
                    "\(buttonDescription): \(String(format: "%.3f", buttonContrast)):1"
                )
            }
        }

        print("Knappster geprüfter Kontrast: \(String(format: "%.3f", weakest.ratio)):1 (\(weakest.description))")
    }

    private func backdropSamples(primary: IronLogTestRGB, dark: Bool) -> [(x: Double, y: Double, color: IronLogTestRGB)] {
        let base = rgb(hex: dark ? 0x07080C : 0xEEF0F6)
        let strength = dark ? 1.0 : 0.55
        return (0...100).flatMap { yi in
            (0...100).map { xi in
                let x = Double(xi) / 100
                let y = Double(yi) / 100
                var color = base
                color = blob(color, x: x, y: y, centerX: 0.25, centerY: 0.06, radius: 1.0, color: primary, alpha: 0.85 * strength)
                color = blob(color, x: x, y: y, centerX: 0.98, centerY: 0.48, radius: 0.9, color: teal, alpha: 0.5 * strength)
                color = blob(color, x: x, y: y, centerX: 0.12, centerY: 0.96, radius: 0.95, color: violet, alpha: 0.5 * strength)
                if y > 0.55 {
                    color = composite(base, alpha: 0.6 * ((y - 0.55) / 0.45), over: color)
                }
                return (x, y, color)
            }
        }
    }

    private func blob(
        _ background: IronLogTestRGB,
        x: Double,
        y: Double,
        centerX: Double,
        centerY: Double,
        radius: Double,
        color: IronLogTestRGB,
        alpha: Double
    ) -> IronLogTestRGB {
        let distance = hypot(x - centerX, 2 * (y - centerY)) / radius
        guard distance < 1 else { return background }
        return composite(color, alpha: alpha * (1 - distance), over: background)
    }

    private func glassColor(
        over backdrop: (x: Double, y: Double, color: IronLogTestRGB),
        primary: IronLogTestRGB,
        level: IronLogGlassLevel,
        dark: Bool,
        sheenAlpha: Double
    ) -> IronLogTestRGB {
        var result: IronLogTestRGB
        switch level {
        case .standard:
            result = composite(
                dark ? rgb(hex: 0x10121A) : rgb(hex: 0xFFFFFF),
                alpha: dark ? 0x47 / 255 : 0x59 / 255,
                over: backdrop.color
            )
        case .tint:
            result = composite(primary, alpha: dark ? 0.30 : 0.22, over: backdrop.color)
        case .strong:
            return backdrop.color
        }

        if dark {
            result = composite(rgb(hex: 0x000000), alpha: 0.84, over: result)
        } else if level == .tint {
            result = composite(rgb(hex: 0xFFFFFF), alpha: 0.50, over: result)
        }

        return composite(rgb(hex: 0xFFFFFF), alpha: sheenAlpha, over: result)
    }

    private func composite(_ foreground: IronLogTestRGB, alpha: Double, over background: IronLogTestRGB) -> IronLogTestRGB {
        IronLogTestRGB(
            red: foreground.red * alpha + background.red * (1 - alpha),
            green: foreground.green * alpha + background.green * (1 - alpha),
            blue: foreground.blue * alpha + background.blue * (1 - alpha)
        )
    }

    private func rgb(_ color: Color) -> IronLogTestRGB {
        let uiColor = UIColor(color)
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        XCTAssertTrue(uiColor.getRed(&red, green: &green, blue: &blue, alpha: &alpha))
        return IronLogTestRGB(red: Double(red), green: Double(green), blue: Double(blue))
    }

    private func rgb(hex: UInt32) -> IronLogTestRGB {
        IronLogTestRGB(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }

    private func contrast(_ foreground: IronLogTestRGB, _ background: IronLogTestRGB) -> Double {
        let first = luminance(foreground)
        let second = luminance(background)
        return (max(first, second) + 0.05) / (min(first, second) + 0.05)
    }

    private func luminance(_ color: IronLogTestRGB) -> Double {
        func linear(_ component: Double) -> Double {
            component <= 0.04045 ? component / 12.92 : pow((component + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
}
