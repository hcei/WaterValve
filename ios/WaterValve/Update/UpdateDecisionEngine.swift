import Foundation

struct UpdateDecisionEngine {
    struct Metadata: Equatable {
        let isForced: Bool
        let minimumSupportedVersion: String?
    }

    func evaluate(localVersion: String, release: UpdateRelease) -> UpdateInfo? {
        let directURL = release.assetDownloadURL.flatMap(URL.init(string:))
        let releasePageURL = release.releasePageURL.flatMap(URL.init(string:))
        let assetName = release.assetName
        let hasDirectInstallable = assetName?.lowercased().hasSuffix(".ipa") == true
        let metadata = parseMetadata(from: release.body)
        let requiresUpgrade = metadata.isForced || isBelowMinimumVersion(
            localVersion: localVersion,
            minimumVersion: metadata.minimumSupportedVersion
        )
        let hasNewerVersion = isRemoteVersionNewer(local: localVersion, remote: release.tagName)

        guard requiresUpgrade || hasNewerVersion else {
            return nil
        }

        // Do not hard-lock iOS users on Android-only releases.
        let shouldForce = requiresUpgrade && hasDirectInstallable

        return UpdateInfo(
            version: release.tagName,
            notes: sanitizedNotes(from: release.body),
            downloadURL: hasDirectInstallable ? directURL : nil,
            releasePageURL: releasePageURL ?? directURL,
            packageName: assetName,
            deliveryKind: hasDirectInstallable ? .directPackage : .releasePage,
            isForced: shouldForce,
            minimumSupportedVersion: metadata.minimumSupportedVersion
        )
    }

    func parseMetadata(from body: String) -> Metadata {
        let isForced = body.localizedCaseInsensitiveContains("[FORCED]")
        let pattern = #"\[MIN_VER:([^\]]+)\]"#

        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: body, range: NSRange(body.startIndex..., in: body)),
              let range = Range(match.range(at: 1), in: body) else {
            return Metadata(isForced: isForced, minimumSupportedVersion: nil)
        }

        return Metadata(isForced: isForced, minimumSupportedVersion: String(body[range]))
    }

    func sanitizedNotes(from body: String) -> String {
        body
            .replacingOccurrences(of: "[FORCED]", with: "")
            .replacingOccurrences(of: #"\[MIN_VER:[^\]]+\]"#, with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func isBelowMinimumVersion(localVersion: String, minimumVersion: String?) -> Bool {
        guard let minimumVersion else { return false }
        return isRemoteVersionNewer(local: localVersion, remote: minimumVersion)
    }

    func isRemoteVersionNewer(local: String, remote: String) -> Bool {
        let localParts = versionParts(local)
        let remoteParts = versionParts(remote)
        let count = max(localParts.count, remoteParts.count)

        for index in 0..<count {
            let localValue = index < localParts.count ? localParts[index] : 0
            let remoteValue = index < remoteParts.count ? remoteParts[index] : 0
            if remoteValue != localValue {
                return remoteValue > localValue
            }
        }

        return false
    }

    private func versionParts(_ version: String) -> [Int] {
        let trimmed = version
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "vV"))
        return trimmed.split(separator: ".").compactMap { Int($0) }
    }
}
