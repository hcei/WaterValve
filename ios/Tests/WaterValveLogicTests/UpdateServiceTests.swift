import Foundation
import XCTest
@testable import WaterValveLogic

private final class MockJSONClient: JSONClient {
    private let responses: [URL: Result<Any, Error>]
    private(set) var requestedURLs: [URL] = []

    init(responses: [URL: Result<Any, Error>]) {
        self.responses = responses
    }

    func getJSON(from url: URL) async throws -> Any {
        requestedURLs.append(url)
        guard let response = responses[url] else {
            struct MissingResponse: Error {}
            throw MissingResponse()
        }
        return try response.get()
    }
}

final class UpdateServiceTests: XCTestCase {
    func testFallsBackToLaterSourceWhenEarlierSourceFails() async {
        let firstURL = URL(string: "https://example.com/first")!
        let secondURL = URL(string: "https://example.com/second")!
        let secondJSON: [String: Any] = [
            "tag_name": "v1.2.0",
            "body": "Notes",
            "html_url": "https://example.com/release",
            "assets": [
                [
                    "name": "app.ipa",
                    "browser_download_url": "https://example.com/app.ipa"
                ]
            ]
        ]
        let client = MockJSONClient(
            responses: [
                firstURL: .failure(URLError(.badServerResponse)),
                secondURL: .success(secondJSON)
            ]
        )
        let service = UpdateService(client: client)

        let info = await service.checkForUpdate(localVersion: "1.0.0", sources: [firstURL, secondURL])

        XCTAssertEqual(client.requestedURLs, [firstURL, secondURL])
        XCTAssertEqual(info?.version, "v1.2.0")
        XCTAssertEqual(info?.deliveryKind, .directPackage)
    }

    func testReturnsNilWhenAllSourcesFail() async {
        let source = URL(string: "https://example.com/fail")!
        let client = MockJSONClient(
            responses: [
                source: .failure(URLError(.cannotLoadFromNetwork))
            ]
        )
        let service = UpdateService(client: client)

        let info = await service.checkForUpdate(localVersion: "1.0.0", sources: [source])

        XCTAssertNil(info)
        XCTAssertEqual(client.requestedURLs, [source])
    }
}
