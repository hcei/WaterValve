import Foundation

final class UpdateService {
    private let client: JSONClient
    private let parser: UpdateReleaseParsing
    private let decisionEngine: UpdateDecisionEngine
    private let sources: [URL]

    init(
        client: JSONClient = APIClient(),
        parser: UpdateReleaseParsing = UpdateReleaseParser(),
        decisionEngine: UpdateDecisionEngine = UpdateDecisionEngine(),
        sources: [URL] = [
            AppConstants.githubLatestReleaseURL,
            AppConstants.giteeLatestReleaseURL,
            AppConstants.proxyLatestReleaseURL
        ]
    ) {
        self.client = client
        self.parser = parser
        self.decisionEngine = decisionEngine
        self.sources = sources
    }

    func checkForUpdate() async -> UpdateInfo? {
        let localVersion = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        return await checkForUpdate(localVersion: localVersion, sources: sources)
    }

    func checkForUpdate(localVersion: String, sources: [URL]) async -> UpdateInfo? {
        for source in sources {
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
                  let release = parser.parseRelease(from: json) else {
                return nil
            }
            return decisionEngine.evaluate(localVersion: localVersion, release: release)
        } catch {
            return nil
        }
    }
}
