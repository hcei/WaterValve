import Foundation
import Shared

enum AuthRepositoryError: LocalizedError {
    case invalidTicket
    case invalidResponse
    case missingSessionCookie
    case missingToken
    case network(String)

    var errorDescription: String? {
        switch self {
        case .invalidTicket:
            return "Unable to recognize the CAS ticket."
        case .invalidResponse:
            return "The server returned an unreadable payload."
        case .missingSessionCookie:
            return "The SESSION cookie was not returned."
        case .missingToken:
            return "The auth token was not returned."
        case let .network(message):
            return message
        }
    }
}

enum DeviceRepositoryError: LocalizedError {
    case notLoggedIn
    case banned
    case invalidPayload
    case network(String)

    var errorDescription: String? {
        switch self {
        case .notLoggedIn:
            return "Sign in before syncing devices."
        case .banned:
            return "This account is banned from device sync."
        case .invalidPayload:
            return "The device sync payload is unreadable."
        case let .network(message):
            return message
        }
    }
}

@MainActor
final class AuthRepository {
    private unowned let container: AppContainer

    init(container: AppContainer) {
        self.container = container
    }

    func startCasLogin() -> LoginConfig {
        LoginConfig(shared: container.sharedBridge.authRepository.startCasLogin())
    }

    static func extractTicket(from url: URL) -> String? {
        URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?
            .first(where: { $0.name == "ticket" })?
            .value
    }

    func currentSession() -> UserSession? {
        guard let snapshot = container.sharedBridge.currentSessionSnapshot() else {
            return nil
        }
        return UserSession(
            userId: snapshot.userId,
            nickname: snapshot.nickname,
            accNum: snapshot.accNum,
            epId: snapshot.epId,
            perCode: snapshot.perCode,
            uisToken: snapshot.uisToken,
            uwcToken: snapshot.uwcToken,
            sessionCookie: snapshot.sessionCookie
        )
    }

    func exchangeCasTicket(
        ticket: String,
        progress: (@MainActor (Int, String) -> Void)? = nil
    ) async throws -> UserSession {
        guard ticket.hasPrefix("ST-") else { throw AuthRepositoryError.invalidTicket }

        await MainActor.run {
            let loading = container.sharedBridge.currentLoginState()
            if loading.phase == "loading" {
                progress?(Int(loading.step), loading.message)
            }
        }

        let result: LoginResult = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<LoginResult, Error>) in
            container.sharedBridge.authRepository.exchangeCasTicket(ticket: ticket) { result, error in
                if let error {
                    continuation.resume(throwing: AuthRepositoryError.network(sharedErrorMessage(error as NSError)))
                    return
                }
                guard let result else {
                    continuation.resume(throwing: AuthRepositoryError.invalidResponse)
                    return
                }
                continuation.resume(returning: result)
            }
        }

        if let success = result as? LoginResult.Success,
           let session = currentSession() {
            return UserSession(sharedUserInfo: success.userInfo, snapshot: SharedSessionSnapshot(
                userId: session.userId,
                nickname: session.nickname,
                accNum: session.accNum,
                epId: session.epId,
                perCode: session.perCode,
                uisToken: session.uisToken,
                uwcToken: session.uwcToken,
                sessionCookie: session.sessionCookie
            ))
        }

        if let failed = result as? LoginResult.Failed {
            throw AuthRepositoryError.network(loginErrorMessage(failed.error))
        }

        throw AuthRepositoryError.invalidResponse
    }

    func refreshUwcToken() async -> Bool {
        await withCheckedContinuation { continuation in
            container.sharedBridge.authRepository.refreshUwcToken { result, _ in
                continuation.resume(returning: result?.boolValue ?? false)
            }
        }
    }

    func markBanned() {
        container.sharedBridge.authRepository.markBanned()
    }

    func clearBannedFlag() {
        container.sharedBridge.clearBannedState()
    }

    func isBanned() -> Bool {
        container.sharedBridge.currentBannedState()
    }

    func clearAuth() {
        container.sharedBridge.authRepository.clearAuth()
    }
}

@MainActor
final class DeviceRepository {
    private unowned let container: AppContainer

    private(set) var devices: [Device] = []
    private(set) var records: [WaterRecord] = []

    init(container: AppContainer) {
        self.container = container
        reloadStoredState()
    }

    func reloadStoredState() {
        devices = container.sharedBridge.currentDeviceSnapshot().map(Device.init(shared:))
        records = container.sharedBridge.currentRecordSnapshot().map(WaterRecord.init(shared:))
        sortDevices()
        sortRecords()
    }

    @discardableResult
    func addDevice(qrURL: String, name: String? = nil) async throws -> Device {
        let normalized = qrURL.trimmingCharacters(in: .whitespacesAndNewlines)
        let newDevice: IosDeviceSnapshot = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<IosDeviceSnapshot, Error>) in
            container.sharedBridge.addDevice(qrUrl: normalized) { result, error in
                if let error {
                    continuation.resume(throwing: self.mapDeviceError(error as NSError))
                    return
                }
                guard let device = result else {
                    continuation.resume(throwing: DeviceRepositoryError.invalidPayload)
                    return
                }
                continuation.resume(returning: device)
            }
        }

        reloadStoredState()

        if let name, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            try await renameDevice(id: newDevice.id, name: name)
            reloadStoredState()
            return devices.first(where: { $0.id == newDevice.id }) ?? Device(shared: IosDeviceSnapshot(id: newDevice.id, name: name, qrUrl: newDevice.qrUrl, starred: newDevice.starred, createdAt: newDevice.createdAt))
        }

        return devices.first(where: { $0.id == newDevice.id }) ?? Device(shared: newDevice)
    }

    func renameDevice(id: String, name: String) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            container.sharedBridge.renameDevice(deviceId: id, name: name) { error in
                if let error {
                    continuation.resume(throwing: self.mapDeviceError(error as NSError))
                } else {
                    continuation.resume(returning: ())
                }
            }
        }
        reloadStoredState()
    }

    func toggleStar(id: String) async throws {
        let current = devices.first(where: { $0.id == id })?.starred ?? false
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            container.sharedBridge.starDevice(deviceId: id, starred: !current) { error in
                if let error {
                    continuation.resume(throwing: self.mapDeviceError(error as NSError))
                } else {
                    continuation.resume(returning: ())
                }
            }
        }
        reloadStoredState()
    }

    func deleteDevice(id: String) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            container.sharedBridge.deleteDevice(deviceId: id) { error in
                if let error {
                    continuation.resume(throwing: self.mapDeviceError(error as NSError))
                } else {
                    continuation.resume(returning: ())
                }
            }
        }
        reloadStoredState()
    }

    func pullFromCloud() async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            container.sharedBridge.pullFromCloud { error in
                if let error {
                    continuation.resume(throwing: self.mapDeviceError(error as NSError))
                } else {
                    continuation.resume(returning: ())
                }
            }
        }
        container.sharedBridge.clearBannedState()
        reloadStoredState()
    }

    func pushToCloud() async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            container.sharedBridge.pushToCloud { error in
                if let error {
                    continuation.resume(throwing: self.mapDeviceError(error as NSError))
                } else {
                    continuation.resume(returning: ())
                }
            }
        }
    }

    func addRecord(deviceName: String) async {
        do {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                container.sharedBridge.addRecord(deviceName: deviceName) { error in
                    if let error {
                        continuation.resume(throwing: self.mapDeviceError(error as NSError))
                    } else {
                        continuation.resume(returning: ())
                    }
                }
            }
            reloadStoredState()
        } catch {
            // Record persistence is best-effort and should not break the valve flow.
        }
    }

    func deleteRecord(id: UUID) async {
        guard let target = records.first(where: { $0.id == id }) else { return }
        let recordTimestamp = Int64(target.timestamp.timeIntervalSince1970 * 1000)
        let snapshot = container.sharedBridge.currentRecordSnapshot().first {
            $0.deviceName == target.deviceName && $0.timestamp == recordTimestamp
        }
        guard let snapshot else { return }

        do {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                container.sharedBridge.deleteRecord(id: snapshot.id) { error in
                    if let error {
                        continuation.resume(throwing: self.mapDeviceError(error as NSError))
                    } else {
                        continuation.resume(returning: ())
                    }
                }
            }
            reloadStoredState()
        } catch {
            // Record deletion failures should not destabilize the record list UI.
        }
    }

    func clearRecords() async {
        do {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                container.sharedBridge.deleteAllRecords { error in
                    if let error {
                        continuation.resume(throwing: self.mapDeviceError(error as NSError))
                    } else {
                        continuation.resume(returning: ())
                    }
                }
            }
            reloadStoredState()
        } catch {
            // Record clearing is best-effort.
        }
    }

    private func sortDevices() {
        devices.sort {
            if $0.starred != $1.starred {
                return $0.starred && !$1.starred
            }
            return $0.createdAt > $1.createdAt
        }
    }

    private func sortRecords() {
        records.sort { $0.timestamp > $1.timestamp }
    }

    private func mapDeviceError(_ error: NSError) -> Error {
        let message = sharedErrorMessage(error)
        if message.localizedCaseInsensitiveContains("BannedException") || message.localizedCaseInsensitiveContains("banned") {
            container.authRepository.markBanned()
            return DeviceRepositoryError.banned
        }
        if message.localizedCaseInsensitiveContains("userId") {
            return DeviceRepositoryError.notLoggedIn
        }
        return DeviceRepositoryError.network(message)
    }
}
