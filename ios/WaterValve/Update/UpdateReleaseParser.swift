import Foundation

protocol UpdateReleaseParsing {
    func parseRelease(from json: [String: Any]) -> UpdateRelease?
}

struct UpdateReleaseParser: UpdateReleaseParsing {
    func parseRelease(from json: [String: Any]) -> UpdateRelease? {
        let releaseObject = (json["release"] as? [String: Any]) ?? json
        guard let tag = releaseObject["tag_name"] as? String ?? releaseObject["tagName"] as? String else {
            return nil
        }

        let body = releaseObject["body"] as? String ?? ""
        let releasePageURL = releaseObject["html_url"] as? String
            ?? releaseObject["releasePageUrl"] as? String
            ?? json["html_url"] as? String
            ?? json["releasePageUrl"] as? String

        let assetCandidates = (releaseObject["assets"] as? [[String: Any]])
            ?? (json["assets"] as? [[String: Any]])
            ?? []
        let selectedAsset = assetCandidates.first(where: {
            guard let url = $0["browser_download_url"] as? String else { return false }
            return url.lowercased().hasSuffix(".ipa")
        }) ?? assetCandidates.first

        let assetURL = selectedAsset?["browser_download_url"] as? String
            ?? releaseObject["downloadUrl"] as? String
            ?? json["downloadUrl"] as? String
        let assetName = selectedAsset?["name"] as? String

        return UpdateRelease(
            tagName: tag,
            body: body,
            assetDownloadURL: assetURL,
            releasePageURL: releasePageURL,
            assetName: assetName
        )
    }
}
