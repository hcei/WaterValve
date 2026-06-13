import Foundation
import Shared

struct SharedSessionSnapshot: Equatable {
    var userId: String
    var nickname: String
    var accNum: String
    var epId: String
    var perCode: String
    var uisToken: String
    var uwcToken: String
    var sessionCookie: String
}

enum SharedBridgeError: LocalizedError {
    case loginFailed(String)
    case repository(String)

    var errorDescription: String? {
        switch self {
        case let .loginFailed(message):
            return message
        case let .repository(message):
            return message
        }
    }
}

extension Device {
    init(shared: IosDeviceSnapshot) {
        self.init(
            id: shared.id,
            name: shared.name,
            qrURL: shared.qrUrl,
            starred: shared.starred,
            createdAt: Date(timeIntervalSince1970: TimeInterval(shared.createdAt) / 1000)
        )
    }
}

extension WaterRecord {
    init(shared: IosWaterRecordSnapshot) {
        self.init(
            id: UUID(uuidString: stableUUIDString(id: shared.id, deviceName: shared.deviceName, timestamp: shared.timestamp)) ?? UUID(),
            deviceName: shared.deviceName,
            timestamp: Date(timeIntervalSince1970: TimeInterval(shared.timestamp) / 1000)
        )
    }

    private static func stableUUIDString(id: Int64, deviceName: String, timestamp: Int64) -> String {
        let raw = "record-\(id)-\(deviceName)-\(timestamp)"
        let hash = Crypto.md5(raw)
        let parts = [
            String(hash.prefix(8)),
            String(hash.dropFirst(8).prefix(4)),
            String(hash.dropFirst(12).prefix(4)),
            String(hash.dropFirst(16).prefix(4)),
            String(hash.dropFirst(20).prefix(12))
        ]
        return parts.joined(separator: "-")
    }
}

extension UserSession {
    init(sharedUserInfo: UserInfo, snapshot: SharedSessionSnapshot) {
        self.init(
            userId: sharedUserInfo.userId,
            nickname: sharedUserInfo.nickname,
            accNum: snapshot.accNum,
            epId: snapshot.epId,
            perCode: snapshot.perCode,
            uisToken: snapshot.uisToken,
            uwcToken: snapshot.uwcToken,
            sessionCookie: snapshot.sessionCookie
        )
    }
}

extension LoginConfig {
    init(shared: CasLoginConfig) {
        self.init(
            url: URL(string: shared.url)!,
            userAgent: shared.userAgent
        )
    }
}

extension UpdateRelease {
    init(shared: IosAppReleaseSnapshot) {
        self.init(
            tagName: shared.tagName,
            body: shared.body,
            assetDownloadURL: shared.downloadUrl,
            releasePageURL: shared.downloadUrl,
            assetName: shared.downloadUrl.components(separatedBy: "/").last
        )
    }
}

func sharedErrorMessage(_ error: NSError) -> String {
    if let kotlinException = error.kotlinException {
        return String(describing: kotlinException)
    }
    if !error.localizedDescription.isEmpty {
        return error.localizedDescription
    }
    return "Unknown shared-layer error."
}

func loginErrorMessage(_ error: LoginError) -> String {
    let rawValue = String(describing: error).lowercased()

    if rawValue.contains("invalidcredentials") {
        return "The CAS ticket could not be verified."
    }
    if rawValue.contains("banned") {
        return "This account is banned from device sync."
    }
    if rawValue.contains("network") {
        return "The shared login request failed."
    }
    return String(describing: error)
}
