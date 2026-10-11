import Foundation
import XCTest
@testable import ThrymIOS

/// Text fields are prefilled in the current locale and parsed with either
/// separator, so a prefilled value must read back unchanged in any locale.
final class NumberInputFormatTests: XCTestCase {
    func testInputValuesRoundTripWithoutGrouping() {
        for value in [0, 2.5, 82.5, 100, 1_000, 1_234.56, 12_500] {
            let text = IOSNumber.format(value)
            XCTAssertEqual(IOSNumber.parse(text), value, "\(value) → \(text)")
            XCTAssertEqual(iosWorkoutParseDecimal(text), value, "\(value) → \(text)")
            XCTAssertTrue(text.allSatisfy { $0.isASCII && ($0.isNumber || $0 == "." || $0 == ",") }, text)
            XCTAssertLessThanOrEqual(text.filter { $0 == "." || $0 == "," }.count, 1, "grouped: \(text)")
        }
    }

    func testInputUsesTheDecimalSeparatorOfTheLocale() {
        let separator = Locale.current.decimalSeparator == "," ? "," : "."
        XCTAssertEqual(IOSNumber.format(82.5), "82\(separator)5")
        XCTAssertEqual(IOSNumber.format(1_000), "1000")
        XCTAssertEqual(IOSNumber.format(7.5, fractionDigits: 1), "7\(separator)5")
        XCTAssertEqual(IOSNumber.format(7, fractionDigits: 1), "7")
    }

    func testParsingAcceptsCommaAndDot() {
        XCTAssertEqual(IOSNumber.parse("82,5"), 82.5)
        XCTAssertEqual(IOSNumber.parse("82.5"), 82.5)
        XCTAssertEqual(iosWorkoutParseDecimal(" 7,5 "), 7.5)
    }

    func testIntensityPrefillRoundTrips() throws {
        let rir = try XCTUnwrap(iosWorkoutFormatIntensity(storedRPE: 7.5, intensitySystem: "RIR"))
        XCTAssertEqual(iosWorkoutParseDecimal(rir), 2.5)
        let rpe = try XCTUnwrap(iosWorkoutFormatIntensity(storedRPE: 8, intensitySystem: "RPE"))
        XCTAssertEqual(iosWorkoutParseDecimal(rpe), 8)
        XCTAssertNil(iosWorkoutFormatIntensity(storedRPE: 8, intensitySystem: "OFF"))
    }

    func testDisplayNumbersFollowTheLocale() {
        let expected = 1_250.5.formatted(.number.precision(.fractionLength(0...2)))
        XCTAssertEqual(iosWorkoutDecimal(1_250.5), expected)
    }
}
