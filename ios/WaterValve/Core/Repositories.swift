import Foundation

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

final class AuthRepository {
    private let client: APIClient
    private let keychain: KeychainStore
    private let defaults: KeyValueStore

    init(client: APIClient, keychain: KeychainStore, defaults: KeyValueStore) {
        self.client = client
        self.keychain = keychain
        self.defaults = defaults
    }

    func startCasLogin() -> LoginConfig {
        LoginConfig(url: AppConstants.casLoginURL, userAgent: AppConstants.chromeIOSUserAgent)
    }

    static func extractTicket(from url: URL) -> String? {
        URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?
            .first(where: { $0.name == "ticket" })?
            .value
    }

    func currentSession() -> UserSession? {
        guard let userId = keychain.string(for: AppConstants.userIdKey),
              let uwcToken = keychain.string(for: AppConstants.uwcTokenKey),
              let uisToken = keychain.string(for: AppConstants.uisTokenKey) else {
            return nil
        }

        return UserSession(
            userId: userId,
            nickname: defaults.string(for: AppConstants.nicknameKey) ?? "",
            accNum: defaults.string(for: AppConstants.userAccNumKey) ?? "",
            epId: defaults.string(for: AppConstants.userEpIdKey) ?? "",
            perCode: defaults.string(for: AppConstants.userPerCodeKey) ?? "",
            uisToken: uisToken,
            uwcToken: uwcToken,
            sessionCookie: keychain.string(for: AppConstants.sessionCookieKey) ?? ""
        )
    }

    func saveSession(_ session: UserSession) {
        keychain.set(session.userId, for: AppConstants.userIdKey)
        keychain.set(session.uisToken, for: AppConstants.uisTokenKey)
        keychain.set(session.uwcToken, for: AppConstants.uwcTokenKey)
        keychain.set(session.sessionCookie, for: AppConstants.sessionCookieKey)
        defaults.set(session.nickname, for: AppConstants.nicknameKey)
        defaults.set(session.accNum, for: AppConstants.userAccNumKey)
        defaults.set(session.epId, for: AppConstants.userEpIdKey)
        defaults.set(session.perCode, for: AppConstants.userPerCodeKey)
        defaults.set(false, for: AppConstants.isBannedKey)
    }

    func exchangeCasTicket(
        ticket: String,
        progress: (@MainActor (Int, String) -> Void)? = nil
    ) async throws -> UserSession {
        guard ticket.hasPrefix("ST-") else { throw AuthRepositoryError.invalidTicket }

        await progress?(2, "Verifying the CAS ticket")
        let sessionCookie = try await fetchSessionCookie(ticket: ticket)
        await progress?(2, "Fetching the UIS token")
        let uisToken = try await fetchUisToken(sessionCookie: sessionCookie)
        await progress?(3, "Fetching the UWC token")
        let payload = try await fetchUwcToken(uisToken: uisToken)

        guard let uwcToken = payload["token"] as? String, !uwcToken.isEmpty else {
            throw AuthRepositoryError.missingToken
        }

        return UserSession(
            userId: stringValue(payload["userId"]),
            nickname: stringValue(payload["nickName"] ?? payload["nickname"]),
            accNum: stringValue(payload["accNum"] ?? payload["accountNum"] ?? payload["accnum"]),
            epId: stringValue(payload["epId"] ?? payload["epid"]),
            perCode: stringValue(payload["perCode"] ?? payload["percode"]),
            uisToken: uisToken,
            uwcToken: uwcToken,
            sessionCookie: sessionCookie
        )
    }

    func refreshUwcToken() async -> Bool {
        guard let session = currentSession(), !session.uisToken.isEmpty else { return false }

        do {
            let payload = try await fetchUwcToken(uisToken: session.uisToken)
            guard let uwcToken = payload["token"] as? String, !uwcToken.isEmpty else { return false }

            saveSession(
                UserSession(
                    userId: session.userId,
                    nickname: session.nickname,
                    accNum: stringValue(payload["accNum"] ?? payload["accountNum"]).ifEmpty(session.accNum),
                    epId: stringValue(payload["epId"] ?? payload["epid"]).ifEmpty(session.epId),
                    perCode: stringValue(payload["perCode"] ?? payload["percode"]).ifEmpty(session.perCode),
                    uisToken: session.uisToken,
                    uwcToken: uwcToken,
                    sessionCookie: session.sessionCookie
                )
            )
            defaults.set(Date().timeIntervalSince1970, for: AppConstants.lastRefreshKey)
            return true
        } catch {
            return false
        }
    }

    func markBanned() {
        defaults.set(true, for: AppConstants.isBannedKey)
    }

    func clearBannedFlag() {
        defaults.set(false, for: AppConstants.isBannedKey)
    }

    func isBanned() -> Bool {
        defaults.bool(for: AppConstants.isBannedKey)
    }

    func clearAuth() {
        keychain.clear()
        defaults.removeAll(keys: [
            AppConstants.nicknameKey,
            AppConstants.userAccNumKey,
            AppConstants.userEpIdKey,
            AppConstants.userPerCodeKey,
            AppConstants.isBannedKey,
            AppConstants.lastRefreshKey
        ])
    }

    private func fetchSessionCookie(ticket: String) async throws -> String {
        let timestamp = String(Int(Date().timeIntervalSince1970 * 1000))
        let nonce = UUID().uuidString
        let sign = Crypto.signUis("service=\(AppConstants.casServiceURL.absoluteString)&ticket=\(ticket)")

        var components = URLComponents(url: AppConstants.casTicketExchangeURL, resolvingAgainstBaseURL: false)
        components?.queryItems = [
            URLQueryItem(name: "ticket", value: ticket),
            URLQueryItem(name: "service", value: AppConstants.casServiceURL.absoluteString)
        ]

        guard let url = components?.url else { throw AuthRepositoryError.invalidResponse }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue(AppConstants.uisAuthorization, forHTTPHeaderField: "Authorization")
        request.setValue(sign, forHTTPHeaderField: "Sign")
        request.setValue(nonce, forHTTPHeaderField: "nonce")
        request.setValue(timestamp, forHTTPHeaderField: "timestamp")
        request.setValue("UTF-8", forHTTPHeaderField: "charset")
        request.setValue(AppConstants.chromeIOSUserAgent, forHTTPHeaderField: "User-Agent")

        let (_, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw AuthRepositoryError.network("CAS verification failed.")
        }

        if let cookieHeader = http.value(forHTTPHeaderField: "Set-Cookie"),
           let cookie = extractCookie(named: "SESSION", from: cookieHeader) {
            return cookie
        }

        if let cookies = HTTPCookieStorage.shared.cookies(for: AppConstants.uisBaseURL),
           let cookie = cookies.first(where: { $0.name == "SESSION" })?.value {
            return cookie
        }

        throw AuthRepositoryError.missingSessionCookie
    }

    private func fetchUisToken(sessionCookie: String) async throws -> String {
        var request = URLRequest(url: AppConstants.uisTokenURL)
        request.httpMethod = "POST"
        request.setValue("SESSION=\(sessionCookie)", forHTTPHeaderField: "Cookie")
        request.setValue(AppConstants.chromeIOSUserAgent, forHTTPHeaderField: "User-Agent")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw AuthRepositoryError.network("Fetching the UIS token failed.")
        }

        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let dataObject = object["data"] as? [String: Any],
              let token = dataObject["value"] as? String,
              !token.isEmpty else {
            throw AuthRepositoryError.missingToken
        }

        return token
    }

    private func fetchUwcToken(uisToken: String) async throws -> [String: Any] {
        let timestamp = String(Int(Date().timeIntervalSince1970 * 1000))
        let nonce = UUID().uuidString
        let paramStr = Crypto.buildParamStr(["uiastoken": uisToken])

        var request = URLRequest(url: AppConstants.loginByTokenURL)
        request.httpMethod = "POST"
        request.setValue(uisToken, forHTTPHeaderField: "token")
        request.setValue(timestamp, forHTTPHeaderField: "timestamp")
        request.setValue(nonce, forHTTPHeaderField: "nonceStr")
        request.setValue("application/x-www-form-urlencoded; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.setValue(AppConstants.chromeIOSUserAgent, forHTTPHeaderField: "User-Agent")
        request.httpBody = "paramStr=\(formEncode(paramStr))".data(using: .utf8)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw AuthRepositoryError.network("Fetching the UWC token failed.")
        }

        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let resultMap = object["resultMap"] as? String else {
            throw AuthRepositoryError.invalidResponse
        }

        let decrypted = Crypto.decryptResponse(resultMap)
        let dataField = Crypto.parseDataField(decrypted)
        return dataField.isEmpty ? decrypted : dataField
    }

    private func formEncode(_ string: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._* ")
        return string
            .addingPercentEncoding(withAllowedCharacters: allowed)?
            .replacingOccurrences(of: " ", with: "+") ?? string
    }

    private func stringValue(_ value: Any?) -> String {
        switch value {
        case let string as String:
            return string
        case let number as NSNumber:
            if floor(number.doubleValue) == number.doubleValue {
                return String(Int64(number.doubleValue))
            }
            return number.stringValue
        default:
            return ""
        }
    }

    private func extractCookie(named name: String, from header: String) -> String? {
        header
            .split(separator: ",")
            .flatMap { $0.split(separator: ";") }
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first(where: { $0.hasPrefix("\(name)=") })?
            .split(separator: "=", maxSplits: 1)
            .last
            .map(String.init)
    }
}

final class DeviceRepository {
    private struct RemoteDeviceDTO {
        let id: String
        let customName: String
        let qrContent: String
        let isFavorite: Bool
        let lastUsedAt: TimeInterval
        let displayOrder: Int
    }

    private let defaults: KeyValueStore
    private let client: APIClient
    private weak var authRepository: AuthRepository?

    private(set) var devices: [Device] = []
    private(set) var records: [WaterRecord] = []

    init(defaults: KeyValueStore, client: APIClient, authRepository: AuthRepository? = nil) {
        self.defaults = defaults
        self.client = client
        self.authRepository = authRepository
        reloadStoredState()
    }

    func bind(authRepository: AuthRepository) {
        self.authRepository = authRepository
        reloadStoredState()
    }

    func reloadStoredState() {
        let scopedDevicesKey = storageKey(base: AppConstants.devicesKey)
        let scopedRecordsKey = storageKey(base: AppConstants.recordsKey)

        let loadedDevices = defaults.decode([Device].self, for: scopedDevicesKey) ?? []
        let loadedRecords = defaults.decode([WaterRecord].self, for: scopedRecordsKey) ?? []

        devices = loadedDevices
        records = loadedRecords
        sortDevices()
        sortRecords()
    }

    @discardableResult
    func addDevice(qrURL: String, name: String? = nil) async throws -> Device {
        let normalized = qrURL.trimmingCharacters(in: .whitespacesAndNewlines)
        let hash = Crypto.md5(normalized)
        let trimmedName = name?.trimmingCharacters(in: .whitespacesAndNewlines)
        let displayName = (trimmedName?.isEmpty == false ? trimmedName! : String(hash.prefix(8)))

        let existing = devices.first(where: { $0.id == hash })
        let device = Device(
            id: hash,
            name: displayName,
            qrURL: normalized,
            starred: existing?.starred ?? false,
            createdAt: existing?.createdAt ?? .now
        )

        upsert(device)
        try await pushToCloud()
        return device
    }

    func renameDevice(id: String, name: String) async throws {
        guard let index = devices.firstIndex(where: { $0.id == id }) else { return }
        devices[index].name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        persistDevices()
        try await pushToCloud()
    }

    func toggleStar(id: String) async throws {
        guard let index = devices.firstIndex(where: { $0.id == id }) else { return }
        devices[index].starred.toggle()
        persistDevices()
        try await pushToCloud()
    }

    func deleteDevice(id: String) async throws {
        devices.removeAll { $0.id == id }
        persistDevices()
        try await pushToCloud()
    }

    func pullFromCloud() async throws {
        let userId = try requireUserId()
        let url = AppConstants.syncBaseURL.appending(path: "api/devices/\(userId)")

        do {
            guard let json = try await client.getJSON(from: url) as? [[String: Any]] else {
                throw DeviceRepositoryError.invalidPayload
            }

            let remoteDevices = try json.map(parseRemoteDevice)
            mergeRemoteDevices(remoteDevices)
            authRepository?.clearBannedFlag()
        } catch APIClientError.httpStatus(let status) where status == 403 {
            authRepository?.markBanned()
            throw DeviceRepositoryError.banned
        } catch let error as DeviceRepositoryError {
            throw error
        } catch {
            throw DeviceRepositoryError.network(error.localizedDescription)
        }
    }

    func pushToCloud() async throws {
        let userId = try requireUserId()
        let url = AppConstants.syncBaseURL.appending(path: "api/devices/\(userId)")

        let payload = devices.enumerated().map { index, device in
            buildRemotePayload(for: device, order: index + 1, userId: userId)
        }

        do {
            _ = try await client.postJSON(from: url, body: payload)
            authRepository?.clearBannedFlag()
        } catch APIClientError.httpStatus(let status) where status == 403 {
            authRepository?.markBanned()
            throw DeviceRepositoryError.banned
        } catch {
            throw DeviceRepositoryError.network(error.localizedDescription)
        }
    }

    func addRecord(deviceName: String) {
        records.insert(WaterRecord(deviceName: deviceName), at: 0)
        persistRecords()
    }

    func deleteRecord(id: UUID) {
        records.removeAll { $0.id == id }
        persistRecords()
    }

    func clearRecords() {
        records.removeAll()
        persistRecords()
    }

    private func requireUserId() throws -> String {
        guard let userId = authRepository?.currentSession()?.userId,
              !userId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw DeviceRepositoryError.notLoggedIn
        }
        return userId
    }

    private func parseRemoteDevice(_ object: [String: Any]) throws -> RemoteDeviceDTO {
        let id = stringValue(object["id"])
        let qrContent = stringValue(object["qrContent"])
        guard !id.isEmpty, !qrContent.isEmpty else {
            throw DeviceRepositoryError.invalidPayload
        }

        return RemoteDeviceDTO(
            id: id,
            customName: stringValue(object["customName"]),
            qrContent: qrContent,
            isFavorite: boolValue(object["isFavorite"]),
            lastUsedAt: numberValue(object["lastUsedAt"]),
            displayOrder: Int(numberValue(object["displayOrder"]))
        )
    }

    private func mergeRemoteDevices(_ remoteDevices: [RemoteDeviceDTO]) {
        let localByID = Dictionary(uniqueKeysWithValues: devices.map { ($0.id, $0) })
        let merged = remoteDevices.enumerated().map { index, remote -> Device in
            let local = localByID[remote.id]
            return Device(
                id: remote.id,
                name: remote.customName.ifEmpty(local?.name ?? String(remote.id.prefix(8))),
                qrURL: remote.qrContent,
                starred: remote.isFavorite,
                createdAt: local?.createdAt ?? createdDate(for: remote, fallbackOrder: index)
            )
        }

        devices = merged
        persistDevices()
    }

    private func createdDate(for remote: RemoteDeviceDTO, fallbackOrder: Int) -> Date {
        if remote.lastUsedAt > 0 {
            return Date(timeIntervalSince1970: remote.lastUsedAt / 1000)
        }
        return Date(timeIntervalSince1970: TimeInterval(max(1, remote.displayOrder == 0 ? fallbackOrder + 1 : remote.displayOrder)))
    }

    private func buildRemotePayload(for device: Device, order: Int, userId: String) -> [String: Any] {
        [
            "id": device.id,
            "customName": device.name,
            "name": device.qrURL,
            "qrContent": device.qrURL,
            "isFavorite": device.starred,
            "displayOrder": order,
            "lastUsedAt": Int(device.createdAt.timeIntervalSince1970 * 1000),
            "macAddress": "",
            "rssi": 0,
            "userId": userId
        ]
    }

    private func upsert(_ device: Device) {
        if let index = devices.firstIndex(where: { $0.id == device.id }) {
            devices[index] = device
        } else {
            devices.append(device)
        }
        persistDevices()
    }

    private func persistDevices() {
        sortDevices()
        defaults.encode(devices, for: storageKey(base: AppConstants.devicesKey))
    }

    private func persistRecords() {
        sortRecords()
        defaults.encode(records, for: storageKey(base: AppConstants.recordsKey))
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

    private func stringValue(_ value: Any?) -> String {
        switch value {
        case let string as String:
            return string
        case let number as NSNumber:
            if floor(number.doubleValue) == number.doubleValue {
                return String(Int64(number.doubleValue))
            }
            return number.stringValue
        default:
            return ""
        }
    }

    private func numberValue(_ value: Any?) -> Double {
        switch value {
        case let number as NSNumber:
            return number.doubleValue
        case let string as String:
            return Double(string) ?? 0
        default:
            return 0
        }
    }

    private func boolValue(_ value: Any?) -> Bool {
        switch value {
        case let bool as Bool:
            return bool
        case let number as NSNumber:
            return number.boolValue
        case let string as String:
            return ["1", "true", "yes"].contains(string.lowercased())
        default:
            return false
        }
    }

    private func storageKey(base: String) -> String {
        guard let userId = authRepository?.currentSession()?.userId,
              !userId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return base
        }
        return "\(base)_\(userId)"
    }
}

final class UpdateRepository {
    func parseRelease(from json: [String: Any]) -> UpdateRelease? {
        let releaseObject = (json["release"] as? [String: Any]) ?? json
        guard let tag = releaseObject["tag_name"] as? String ?? releaseObject["tagName"] as? String else { return nil }
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
            return url.hasSuffix(".ipa")
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

private extension String {
    func ifEmpty(_ fallback: String) -> String {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? fallback : self
    }
}
