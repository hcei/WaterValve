import SwiftUI

@main
@MainActor
struct WaterValveApp: App {
    @StateObject private var appState: AppState
    @StateObject private var container: AppContainer

    init() {
        let appState = AppState()
        let container = AppContainer()
        container.prepareForLaunch()
        _appState = StateObject(wrappedValue: appState)
        _container = StateObject(wrappedValue: container)
    }

    var body: some Scene {
        WindowGroup {
            AppNavigation()
                .environmentObject(appState)
                .environmentObject(container)
                .onAppear {
                    container.bootstrap(appState: appState)
                }
        }
    }
}
