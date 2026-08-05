import XCTest
@testable import IronLogIOS

final class IronLogIOSTests: XCTestCase {
    func testSharedBootstrapStatusLineIsPresent() {
        XCTAssertFalse(SharedBootstrap.statusLine.isEmpty)
    }
}
