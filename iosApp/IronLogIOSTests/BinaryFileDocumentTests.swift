import XCTest
@testable import IronLogIOS

final class BinaryFileDocumentTests: XCTestCase {
    func testDocumentStoresNameAndData() {
        let document = BinaryFileDocument(fileName: "backup.json", data: Data([1, 2, 3]))

        XCTAssertEqual(document.fileName, "backup.json")
        XCTAssertEqual(document.data, Data([1, 2, 3]))
    }
}
