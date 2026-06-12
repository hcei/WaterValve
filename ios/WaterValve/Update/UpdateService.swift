import Foundation

final class UpdateService {
    private let client: APIClient
    private let updateRepository: UpdateRepository

    init(client: APIClient, updateRepository: UpdateRepository) {
        self.client = client
        self.updateRepository = updateRepository
    }

    convenience init(updateRepository: UpdateRepository) {
        self.init(client: APIClient(), updateRepository: updateRepository)
    }

    func checkForUpdate() async -> UpdateInfo? {
        let localVersion = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"

        for source in [
            AppConstants.githubLatestReleaseURL,
            AppConstants.giteeLatestReleaseURL,
            AppConstants.proxyLatestReleaseURL
        ] {
            guard let info = await fetchUpdate(from: source, localVersion: localVersion) else {
                continue
            }
            return info
        }

        return nil
    }

    private func fetchUpdate(from url: URL, localVersion: String) async -> UpdateInfo? {
        do {
            guard let json = try await client.getJSON(from: url) as? [String: Any],
                  let release = updateRepository.parseRelease(from: json) else {
                return nil
            }

            let directURL = release.assetDownloadURL.flatMap(URL.init(string:))
            let releasePageURL = release.releasePageURL.flatMap(URL.init(string:))
            let assetName = release.assetName
            let hasDirectInstallable = assetName?.lowercased().hasSuffix(".ipa") == true
            let metadata = parseMetadata(from: release.body)
            let requiresUpgrade = metadata.isForced || isBelowMinimumVersion(localVersion: localVersion, minimumVersion: metadata.minimumSupportedVersion)
            let remoteVersion = release.tagName

            let hasNewerVersion = Crypto.isRemoteVersionNewer(local: localVersion, remote: remoteVersion)
            guard requiresUpgrade || hasNewerVersion else {
                return nil
            }

            // Do not hard-lock iOS users on Android-only releases.
            let shouldForce = requiresUpgrade && hasDirectInstallable

            return UpdateInfo(
                version: remoteVersion,
                notes: sanitizedNotes(from: release.body),
                downloadURL: hasDirectInstallable ? directURL : nil,
                releasePageURL: releasePageURL ?? directURL,
                packageName: assetName,
                deliveryKind: hasDirectInstallable ? .directPackage : .releasePage,
                isForced: shouldForce,
                minimumSupportedVersion: metadata.minimumSupportedVersion
            )
        } catch {
            return nil
        }
    }

    private func parseMetadata(from body: String) -> (isForced: Bool, minimumSupportedVersion: String?) {
        let isForced = body.localizedCaseInsensitiveContains("[FORCED]")
        let pattern = #"\[MIN_VER:([^\]]+)\]"#

        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: body, range: NSRange(body.startIndex..., in: body)),
              let range = Range(match.range(at: 1), in: body) else {
            return (isForced, nil)
        }

        return (isForced, String(body[range]))
    }

    private func sanitizedNotes(from body: String) -> String {
        body
            .replacingOccurrences(of: "[FORCED]", with: "")
            .replacingOccurrences(of: #"\[MIN_VER:[^\]]+\]"#, with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func isBelowMinimumVersion(localVersion: String, minimumVersion: String?) -> Bool {
        guard let minimumVersion else { return false }
        return Crypto.isRemoteVersionNewer(local: localVersion, remote: minimumVersion)
    }
}
