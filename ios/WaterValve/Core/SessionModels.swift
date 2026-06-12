import Foundation

struct UserSession: Codable, Equatable {
    var userId: String
    var nickname: String
    var accNum: String
    var epId: String
    var perCode: String
    var uisToken: String
    var uwcToken: String
    var sessionCookie: String
}

struct UpdateRelease: Codable, Equatable {
    let tagName: String
    let body: String
    let assetDownloadURL: String?
    let releasePageURL: String?
    let assetName: String?
}

struct SyncDevicePayload: Codable, Equatable {
    let id: String
    let name: String
    let qrUrl: String
}

struct LoginTokenResponse: Codable, Equatable {
    let token: String
    let userId: String
    let nickname: String
}
