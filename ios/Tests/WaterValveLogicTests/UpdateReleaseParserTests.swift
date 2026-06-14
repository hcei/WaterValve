import XCTest
@testable import WaterValveLogic

final class UpdateReleaseParserTests: XCTestCase {
    private let parser = UpdateReleaseParser()

    func testDirectGitHubPayloadPrefersIpaAsset() {
        let json: [String: Any] = [
            "tag_name": "v1.2.0",
            "body": "Notes",
            "html_url": "https://example.com/release",
            "assets": [
                [
                    "name": "app.apk",
                    "browser_download_url": "https://example.com/app.apk"
                ],
                [
                    "name": "app.ipa",
                    "browser_download_url": "https://example.com/app.ipa"
                ]
            ]
        ]

        let release = parser.parseRelease(from: json)

        XCTAssertEqual(release?.tagName, "v1.2.0")
        XCTAssertEqual(release?.assetName, "app.ipa")
        XCTAssertEqual(release?.assetDownloadURL, "https://example.com/app.ipa")
        XCTAssertEqual(release?.releasePageURL, "https://example.com/release")
    }

    func testProxyWrappedPayloadFallsBackToWrappedReleaseObject() {
        let json: [String: Any] = [
            "release": [
                "tagName": "v2.0.0",
                "body": "Proxy notes",
                "downloadUrl": "https://example.com/proxy.ipa",
                "releasePageUrl": "https://example.com/proxy"
            ]
        ]

        let release = parser.parseRelease(from: json)

        XCTAssertEqual(release?.tagName, "v2.0.0")
        XCTAssertEqual(release?.assetDownloadURL, "https://example.com/proxy.ipa")
        XCTAssertEqual(release?.releasePageURL, "https://example.com/proxy")
    }
}
