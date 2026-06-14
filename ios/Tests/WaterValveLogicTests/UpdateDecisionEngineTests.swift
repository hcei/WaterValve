import XCTest
@testable import WaterValveLogic

final class UpdateDecisionEngineTests: XCTestCase {
    private let engine = UpdateDecisionEngine()

    func testParsesForcedAndMinimumVersionMetadata() {
        let metadata = engine.parseMetadata(from: "[FORCED]\nSomething changed\n[MIN_VER:1.1.0]")

        XCTAssertTrue(metadata.isForced)
        XCTAssertEqual(metadata.minimumSupportedVersion, "1.1.0")
    }

    func testSanitizedNotesRemoveReleaseMarkers() {
        let notes = engine.sanitizedNotes(from: "[FORCED]\nLine one\n[MIN_VER:1.1.0]\nLine two")

        XCTAssertFalse(notes.contains("[FORCED]"))
        XCTAssertFalse(notes.contains("[MIN_VER:1.1.0]"))
        XCTAssertTrue(notes.contains("Line one"))
        XCTAssertTrue(notes.contains("Line two"))
    }

    func testSemanticVersionComparisonTrimsLeadingVAndPadsMissingParts() {
        XCTAssertTrue(engine.isRemoteVersionNewer(local: "1.1", remote: "v1.1.1"))
        XCTAssertFalse(engine.isRemoteVersionNewer(local: "v1.1.1", remote: "1.1.1"))
        XCTAssertFalse(engine.isRemoteVersionNewer(local: "1.2.0", remote: "v1.1.9"))
    }

    func testForcedAndroidOnlyReleaseFallsBackToReleasePageWithoutUnsatisfiableLock() {
        let release = UpdateRelease(
            tagName: "v1.2.0",
            body: "[FORCED]\nAndroid-first release",
            assetDownloadURL: "https://example.com/app.apk",
            releasePageURL: "https://example.com/release",
            assetName: "app.apk"
        )

        let info = engine.evaluate(localVersion: "1.1.0", release: release)

        XCTAssertNotNil(info)
        XCTAssertEqual(info?.deliveryKind, .releasePage)
        XCTAssertFalse(info?.isForced ?? true)
        XCTAssertNil(info?.downloadURL)
        XCTAssertEqual(info?.releasePageURL?.absoluteString, "https://example.com/release")
    }

    func testIpaReleaseKeepsDirectInstallFlowAndForceState() {
        let release = UpdateRelease(
            tagName: "v1.2.0",
            body: "[FORCED]\n[MIN_VER:1.1.0]\nInstall me",
            assetDownloadURL: "https://example.com/app.ipa",
            releasePageURL: "https://example.com/release",
            assetName: "app.ipa"
        )

        let info = engine.evaluate(localVersion: "1.0.0", release: release)

        XCTAssertNotNil(info)
        XCTAssertEqual(info?.deliveryKind, .directPackage)
        XCTAssertTrue(info?.isForced ?? false)
        XCTAssertEqual(info?.downloadURL?.absoluteString, "https://example.com/app.ipa")
        XCTAssertEqual(info?.minimumSupportedVersion, "1.1.0")
    }

    func testReturnsNilWhenReleaseIsNotNewerAndNoMinimumVersionRequiresUpgrade() {
        let release = UpdateRelease(
            tagName: "v1.1.0",
            body: "Stable release",
            assetDownloadURL: "https://example.com/app.ipa",
            releasePageURL: "https://example.com/release",
            assetName: "app.ipa"
        )

        XCTAssertNil(engine.evaluate(localVersion: "1.1.0", release: release))
    }
}
