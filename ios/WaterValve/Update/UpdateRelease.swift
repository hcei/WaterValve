import Foundation

struct UpdateRelease: Codable, Equatable {
    let tagName: String
    let body: String
    let assetDownloadURL: String?
    let releasePageURL: String?
    let assetName: String?
}
