import SwiftUI
import UniformTypeIdentifiers

struct BinaryFileDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }

    var fileName: String
    var data: Data

    init(fileName: String, data: Data) {
        self.fileName = fileName
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        fileName = "ironlog.json"
        data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}
