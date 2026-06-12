import SwiftUI
import UIKit

struct BannedAlertView: View {
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(spacing: 20) {
            Text("Account Restricted")
                .font(.title2.bold())
            Text("The current account was blocked by the sync service. Contact the developer to restore access.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
            HStack(spacing: 12) {
                Button("Email Support") {
                    guard let mailURL = URL(string: "mailto:\(AppConstants.supportEmail)") else { return }
                    openURL(mailURL)
                }
                .buttonStyle(.borderedProminent)
                Button("Copy Support Email") {
                    UIPasteboard.general.string = AppConstants.supportEmail
                }
                .buttonStyle(.bordered)
            }
            Text("Close the app manually after contacting support.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
    }
}
