import Foundation
import SwiftUI

@MainActor
final class AppContainer: ObservableObject {
    let keychain = KeychainStore()
    let defaults = KeyValueStore()
    let client = APIClient()
    lazy var authRepository = AuthRepository(client: client, keychain: keychain, defaults: defaults)
    lazy var deviceRepository = DeviceRepository(defaults: defaults, client: client, authRepository: authRepository)
    lazy var updateRepository = UpdateRepository()
    lazy var updateService = UpdateService(client: client, updateRepository: updateRepository)
    lazy var backgroundTaskManager = BackgroundTaskManager(authRepository: authRepository)

    private var didBootstrap = false
    private var didPrepareForLaunch = false
    private weak var appState: AppState?

    func prepareForLaunch() {
        if didPrepareForLaunch { return }
        didPrepareForLaunch = true
        backgroundTaskManager.registerTasks()
    }

    func bootstrap(appState: AppState) {
        if didBootstrap {
            self.appState = appState
            syncStoredState(to: appState)
            return
        }

        didBootstrap = true
        self.appState = appState
        syncStoredState(to: appState)
        backgroundTaskManager.scheduleNextRefreshIfAuthorized()

        Task { @MainActor in
            if let update = await updateService.checkForUpdate() {
                appState.updateInfo = update
                appState.showUpdateAlert = true
            }
        }

        Task {
            await refreshCloudDevicesIfNeeded()
        }
    }

    func syncStoredState(to appState: AppState) {
        deviceRepository.reloadStoredState()

        if let session = authRepository.currentSession() {
            appState.isLoggedIn = !session.uwcToken.isEmpty
            appState.currentUserId = session.userId
            appState.currentNickname = session.nickname
            appState.currentAccNum = session.accNum
            appState.currentEpId = session.epId
            appState.currentPerCode = session.perCode
            appState.uwcToken = session.uwcToken
            appState.uisToken = session.uisToken
            appState.sessionCookie = session.sessionCookie
        } else {
            appState.isLoggedIn = false
            appState.currentUserId = ""
            appState.currentNickname = ""
            appState.currentAccNum = ""
            appState.currentEpId = ""
            appState.currentPerCode = ""
            appState.uwcToken = ""
            appState.uisToken = ""
            appState.sessionCookie = ""
        }

        appState.isBanned = authRepository.isBanned()
        appState.devices = deviceRepository.devices
        appState.records = deviceRepository.records
    }

    func handleLoginSuccess(session: UserSession) {
        authRepository.saveSession(session)
        deviceRepository.reloadStoredState()
        appState?.currentUserId = session.userId
        appState?.currentNickname = session.nickname
        appState?.currentAccNum = session.accNum
        appState?.currentEpId = session.epId
        appState?.currentPerCode = session.perCode
        appState?.uwcToken = session.uwcToken
        appState?.uisToken = session.uisToken
        appState?.sessionCookie = session.sessionCookie
        appState?.isLoggedIn = true
        appState?.isBanned = false
        appState?.devices = deviceRepository.devices
        appState?.records = deviceRepository.records
        backgroundTaskManager.scheduleNextRefreshIfAuthorized()

        Task {
            await refreshCloudDevices()
        }
    }

    func dismissUpdateAlert() {
        appState?.showUpdateAlert = false
    }

    func logout() {
        authRepository.clearAuth()
        deviceRepository.reloadStoredState()
        appState?.isLoggedIn = false
        appState?.isBanned = false
        appState?.currentUserId = ""
        appState?.currentNickname = ""
        appState?.currentAccNum = ""
        appState?.currentEpId = ""
        appState?.currentPerCode = ""
        appState?.uwcToken = ""
        appState?.uisToken = ""
        appState?.sessionCookie = ""
        appState?.devices = []
        appState?.records = []
        appState?.updateInfo = nil
        appState?.showUpdateAlert = false
        appState?.homeNotice = nil
        appState?.lastSyncDate = nil
        backgroundTaskManager.cancelScheduledRefresh()
    }

    func refreshCloudDevicesIfNeeded() async {
        guard authRepository.currentSession() != nil else { return }
        await refreshCloudDevices()
    }

    func refreshCloudDevices() async {
        do {
            try await deviceRepository.pullFromCloud()
            appState?.isBanned = false
            appState?.devices = deviceRepository.devices
            appState?.lastSyncDate = .now
        } catch DeviceRepositoryError.banned {
            authRepository.markBanned()
            appState?.isBanned = true
        } catch {
            if appState?.devices.isEmpty == true {
                appState?.homeNotice = error.localizedDescription
            }
        }
    }

    func addDevice(qrURL: String, name: String? = nil) async -> Bool {
        do {
            _ = try await deviceRepository.addDevice(qrURL: qrURL, name: name)
            appState?.devices = deviceRepository.devices
            appState?.homeNotice = "Device added."
            appState?.lastSyncDate = .now
            return true
        } catch DeviceRepositoryError.banned {
            authRepository.markBanned()
            appState?.isBanned = true
            appState?.devices = deviceRepository.devices
            appState?.homeNotice = DeviceRepositoryError.banned.localizedDescription
            return false
        } catch {
            appState?.devices = deviceRepository.devices
            appState?.homeNotice = error.localizedDescription
            return false
        }
    }

    func renameDevice(id: String, name: String) async {
        do {
            try await deviceRepository.renameDevice(id: id, name: name)
            appState?.devices = deviceRepository.devices
            appState?.lastSyncDate = .now
        } catch DeviceRepositoryError.banned {
            authRepository.markBanned()
            appState?.isBanned = true
            appState?.devices = deviceRepository.devices
        } catch {
            appState?.devices = deviceRepository.devices
            appState?.homeNotice = error.localizedDescription
        }
    }

    func toggleStar(id: String) async {
        do {
            try await deviceRepository.toggleStar(id: id)
            appState?.devices = deviceRepository.devices
            appState?.lastSyncDate = .now
        } catch DeviceRepositoryError.banned {
            authRepository.markBanned()
            appState?.isBanned = true
            appState?.devices = deviceRepository.devices
        } catch {
            appState?.devices = deviceRepository.devices
            appState?.homeNotice = error.localizedDescription
        }
    }

    func deleteDevice(id: String) async {
        do {
            try await deviceRepository.deleteDevice(id: id)
            appState?.devices = deviceRepository.devices
            appState?.homeNotice = "Device removed."
            appState?.lastSyncDate = .now
        } catch DeviceRepositoryError.banned {
            authRepository.markBanned()
            appState?.isBanned = true
            appState?.devices = deviceRepository.devices
        } catch {
            appState?.devices = deviceRepository.devices
            appState?.homeNotice = error.localizedDescription
        }
    }

    func clearHomeNotice() {
        appState?.homeNotice = nil
    }

    func addRecord(deviceName: String) {
        deviceRepository.addRecord(deviceName: deviceName)
        appState?.records = deviceRepository.records
    }

    func deleteRecord(id: UUID) {
        deviceRepository.deleteRecord(id: id)
        appState?.records = deviceRepository.records
    }

    func clearRecords() {
        deviceRepository.clearRecords()
        appState?.records = deviceRepository.records
    }
}
