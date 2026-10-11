import Foundation
import Shared

enum SharedBootstrap {
    static var statusLine: String {
        String(localized: "Shared framework connected")
    }
}

/// Translates a message from the shared Kotlin core into the app language.
///
/// The core reports user-facing errors as fixed German texts that are also keys
/// of `Localizable.xcstrings` (extraction state "manual"). Anything else, such as
/// English diagnostics or system errors that are already localized, has no
/// catalog entry and passes through unchanged.
func ilSharedMessage(_ message: String) -> String {
    Bundle.main.localizedString(forKey: message, value: message, table: nil)
}
