import Foundation
import SwiftUI
import UIKit

struct BannedAlertView: View {
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(spacing: 20) {
            Text("账号已被封禁")
                .font(.title2.bold())
            Text("当前账号已被同步服务封禁，无法继续使用云端设备同步功能。请联系开发者处理后再重新登录。")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
            HStack(spacing: 12) {
                Button("联系开发者") {
                    guard let mailURL = URL(string: "mailto:\(AppConstants.supportEmail)") else { return }
                    openURL(mailURL)
                }
                .buttonStyle(.borderedProminent)

                Button("退出应用") {
                    requestAppSuspend()
                }
                .buttonStyle(.bordered)
            }
            Text("联系邮箱：\(AppConstants.supportEmail)")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
    }

    private func requestAppSuspend() {
        UIApplication.shared.perform(#selector(NSXPCConnection.suspend))
    }
}
