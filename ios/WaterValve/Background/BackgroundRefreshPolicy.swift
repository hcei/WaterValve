import Foundation

enum BackgroundRefreshPolicy {
    static let refreshInterval: TimeInterval = 12 * 60 * 60

    static func shouldSchedule(hasAuthenticatedSession: Bool) -> Bool {
        hasAuthenticatedSession
    }

    static func earliestBeginDate(from now: Date = .now) -> Date {
        now.addingTimeInterval(refreshInterval)
    }
}
