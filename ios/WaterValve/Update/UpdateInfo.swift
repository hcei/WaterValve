import Foundation

enum UpdateDeliveryKind: Equatable {
    case directPackage
    case releasePage
}

struct UpdateInfo: Identifiable, Equatable {
    let id = UUID()
    let version: String
    let notes: String
    let downloadURL: URL?
    let releasePageURL: URL?
    let packageName: String?
    let deliveryKind: UpdateDeliveryKind
    let isForced: Bool
    let minimumSupportedVersion: String?
}
