import XCTest
@testable import WaterValveLogic

final class BackgroundRefreshPolicyTests: XCTestCase {
    func testRequiresAuthenticatedSessionToSchedule() {
        XCTAssertTrue(BackgroundRefreshPolicy.shouldSchedule(hasAuthenticatedSession: true))
        XCTAssertFalse(BackgroundRefreshPolicy.shouldSchedule(hasAuthenticatedSession: false))
    }

    func testEarliestBeginDateUsesTwelveHourInterval() {
        let start = Date(timeIntervalSinceReferenceDate: 1234)
        let next = BackgroundRefreshPolicy.earliestBeginDate(from: start)

        XCTAssertEqual(next.timeIntervalSince(start), 12 * 60 * 60, accuracy: 0.001)
    }
}
