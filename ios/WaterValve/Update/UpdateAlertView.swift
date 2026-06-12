import SwiftUI

struct UpdateAlertView: View {
    @EnvironmentObject private var container: AppContainer
    @Environment(\.openURL) private var openURL
    let info: UpdateInfo

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: info.isForced ? "exclamationmark.arrow.triangle.2.circlepath" : "arrow.down.circle.fill")
                    .font(.title2)
                    .foregroundStyle(info.isForced ? .orange : .blue)
                Text("Update \(info.version) Available")
                    .font(.headline)
            }

            Text(info.notes.isEmpty ? "A newer release is available." : info.notes)
                .font(.subheadline)
                .foregroundStyle(.secondary)

            if let minimumVersion = info.minimumSupportedVersion {
                Text("Minimum supported version: \(minimumVersion)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            if info.deliveryKind == .releasePage {
                Text("No iOS install package was found in the latest release. Open the release page to check the published assets.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                Text("An IPA file is available, but iOS cannot install it directly from this app. Open the IPA link, then import it into AltStore or SideStore manually.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            HStack {
                if !info.isForced {
                    Button("Later") {
                        container.dismissUpdateAlert()
                    }
                }

                Spacer()

                if info.deliveryKind == .directPackage,
                   let releasePageURL = info.releasePageURL,
                   releasePageURL != info.downloadURL {
                    Button("Open Release") {
                        openURL(releasePageURL)
                    }
                    .buttonStyle(.bordered)
                }

                if let targetURL = preferredOpenURL {
                    Button(primaryActionTitle) {
                        openURL(targetURL)
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.ultraThickMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    private var preferredOpenURL: URL? {
        switch info.deliveryKind {
        case .directPackage:
            return info.downloadURL ?? info.releasePageURL
        case .releasePage:
            return info.releasePageURL ?? info.downloadURL
        }
    }

    private var primaryActionTitle: String {
        info.deliveryKind == .directPackage ? "Open IPA Link" : "Open Release"
    }
}
