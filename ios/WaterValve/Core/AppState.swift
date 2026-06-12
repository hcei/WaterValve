import Foundation
import SwiftUI

@MainActor
final class AppState: ObservableObject {
    @Published var isLoggedIn = false
    @Published var isBanned = false
    @Published var showUpdateAlert = false
    @Published var updateInfo: UpdateInfo?
    @Published var devices: [Device] = []
    @Published var records: [WaterRecord] = []
    @Published var currentUserId = ""
    @Published var currentNickname = ""
    @Published var currentAccNum = ""
    @Published var currentEpId = ""
    @Published var currentPerCode = ""
    @Published var uwcToken = ""
    @Published var uisToken = ""
    @Published var sessionCookie = ""
    @Published var homeNotice: String?
    @Published var lastSyncDate: Date?
}

struct LoginConfig: Equatable {
    let url: URL
    let userAgent: String
}

struct Device: Identifiable, Codable, Equatable {
    let id: String
    var name: String
    var qrURL: String
    var starred: Bool
    var createdAt: Date

    var displayName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? String(id.prefix(8)) : name
    }
}

struct WaterRecord: Identifiable, Codable, Equatable {
    let id: UUID
    var deviceName: String
    var timestamp: Date

    init(id: UUID = UUID(), deviceName: String, timestamp: Date = .now) {
        self.id = id
        self.deviceName = deviceName
        self.timestamp = timestamp
    }
}

extension DateFormatter {
    static let appDateTime: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter
    }()
}
