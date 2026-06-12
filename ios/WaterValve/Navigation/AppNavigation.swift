import SwiftUI

@MainActor
struct AppNavigation: View {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        NavigationStack {
            Group {
                if appState.isBanned {
                    BannedAlertView()
                } else if appState.isLoggedIn {
                    HomeView()
                } else {
                    LoginView()
                }
            }
            .disabled(appState.showUpdateAlert && appState.updateInfo?.isForced == true)
            .overlay(alignment: .bottom) {
                if appState.showUpdateAlert, let updateInfo = appState.updateInfo {
                    Group {
                        if updateInfo.isForced {
                            ZStack {
                                Color.black.opacity(0.35)
                                    .ignoresSafeArea()
                                UpdateAlertView(info: updateInfo)
                                    .padding()
                            }
                        } else {
                            UpdateAlertView(info: updateInfo)
                                .padding()
                        }
                    }
                }
            }
        }
        .environmentObject(container)
    }
}
