import SwiftUI
import WebKit

struct ValveView: View {
    let device: Device

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var container: AppContainer
    @StateObject private var viewModel = ValveViewModel()
    @State private var isShowingScanner = false

    var body: some View {
        Group {
            switch viewModel.uiState {
            case .loading:
                ProgressView("Loading valve page")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            case let .loaded(config):
                ZStack(alignment: .bottom) {
                    WebViewScreen(config: config)
                        .ignoresSafeArea(edges: .bottom)

                    if let banner = viewModel.bannerMessage {
                        Text(banner)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(Color(.secondarySystemBackground), in: Capsule())
                            .padding(.bottom, 18)
                    }
                }
            case let .error(message):
                VStack(spacing: 16) {
                    Image(systemName: "wifi.exclamationmark")
                        .font(.system(size: 36))
                        .foregroundStyle(.orange)
                    Text("Valve Page Failed")
                        .font(.title3.bold())
                    Text(message)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                    Button("Retry") {
                        viewModel.configure(container: container, device: device, force: true)
                    }
                    .buttonStyle(.borderedProminent)
                }
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle("Open Valve")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $isShowingScanner) {
            NavigationStack {
                QRScannerView { result in
                    viewModel.handleNativeScanResult(result)
                }
            }
        }
        .onAppear {
            viewModel.bindCloseAction {
                dismiss()
            }
            viewModel.bindScannerAction {
                isShowingScanner = true
            }
            viewModel.configure(container: container, device: device, force: false)
        }
    }
}

@MainActor
final class ValveViewModel: ObservableObject {
    enum UIState {
        case loading
        case loaded(WebViewConfig)
        case error(String)
    }

    @Published private(set) var uiState: UIState = .loading
    @Published private(set) var bannerMessage: String?

    private weak var container: AppContainer?
    private weak var webView: WKWebView?
    private var configuredDeviceId: String?
    private var openScanner: (() -> Void)?
    private var closePage: (() -> Void)?
    private var pendingScanCallback: String?
    private var lastRecordedEventKey: String?

    func bindScannerAction(_ action: @escaping () -> Void) {
        openScanner = action
    }

    func bindCloseAction(_ action: @escaping () -> Void) {
        closePage = action
    }

    func configure(container: AppContainer, device: Device, force: Bool) {
        if configuredDeviceId == device.id && !force { return }

        self.container = container
        configuredDeviceId = device.id
        uiState = .loading

        guard let session = container.authRepository.currentSession() else {
            uiState = .error("There is no active login session. Please sign in again.")
            return
        }

        guard let url = buildValveURL(for: device) else {
            uiState = .error("Unable to build the valve page URL.")
            return
        }

        let injection = buildTokenInjectionScript(session: session)
        uiState = .loaded(
            WebViewConfig(
                url: url,
                userAgent: AppConstants.chromeIOSUserAgent,
                onNavigationComplete: { [weak self] in
                    self?.webView?.injectJavaScript(injection)
                },
                onUrlChange: { [weak self] url in
                    self?.handle(url: url, deviceName: device.displayName)
                    return url.absoluteString.hasPrefix(AppConstants.h5CallSchemePrefix) ? .cancel : .allow
                },
                onWebViewCreated: { [weak self] webView in
                    self?.webView = webView
                    self?.injectSessionCookie(session.sessionCookie, into: webView)
                },
                scriptMessageHandlerName: AppConstants.valveBridgeMessageHandlerName,
                onScriptMessage: { [weak self] message in
                    self?.handleScriptMessage(message, fallbackDeviceName: device.displayName)
                },
                additionalJavaScript: injection
            )
        )
    }

    func handleNativeScanResult(_ result: String) {
        guard !result.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        bannerMessage = "Native scan completed."
        webView?.injectJavaScript(ValveBridgeLogic.buildScanResultEventScript(result: result))
        if let callback = pendingScanCallback?.trimmingCharacters(in: .whitespacesAndNewlines), !callback.isEmpty {
            let callbackScript = ValveBridgeLogic.buildScanCallbackScript(callback: callback, result: result)
            webView?.injectJavaScript(callbackScript)
        }
        pendingScanCallback = nil
    }

    private func buildValveURL(for device: Device) -> URL? {
        ValveBridgeLogic.buildValveURL(deviceId: device.id, qrURL: device.qrURL)
    }

    private func buildTokenInjectionScript(session: UserSession) -> String {
        ValveBridgeLogic.buildTokenInjectionScript(
            session: ValveSessionSnapshot(
                userId: session.userId,
                accNum: session.accNum,
                epId: session.epId,
                perCode: session.perCode,
                uisToken: session.uisToken,
                uwcToken: session.uwcToken
            )
        )
    }

    private func handle(url: URL, deviceName: String) {
        guard url.absoluteString.hasPrefix(AppConstants.h5CallSchemePrefix) else { return }

        let payload = ValveBridgePayload.parse(url: url)
        switch payload?.action {
        case "openScan":
            bannerMessage = "The page requested native scanning."
            pendingScanCallback = payload?.callback
            openScanner?()
        case "closeWin":
            bannerMessage = "The page requested a close action."
            closePage?()
        case "setNativeHeadColor":
            bannerMessage = "The page requested a native header color update."
        default:
            recordValveActionIfNeeded(deviceName: deviceName, eventKey: payload?.action ?? url.absoluteString)
            bannerMessage = "Recorded a valve action for \(deviceName)."
        }
    }

    private func handleScriptMessage(_ message: Any, fallbackDeviceName: String) {
        let payload = ValveBridgeLogic.parseScriptMessage(message)
        let event = payload["event"] as? String ?? payload["action"] as? String ?? ""

        switch event {
        case "valveOpened":
            let deviceName = ValveBridgeLogic.stringValue(payload["deviceName"]).ifEmpty(fallbackDeviceName)
            let timestamp = ValveBridgeLogic.stringValue(payload["timestamp"])
            recordValveActionIfNeeded(deviceName: deviceName, eventKey: "valveOpened:\(deviceName):\(timestamp)")
            bannerMessage = "Recorded a valve action for \(deviceName)."
        case "error":
            let message = ValveBridgeLogic.stringValue(payload["message"]).ifEmpty("The valve page reported an unknown error.")
            bannerMessage = message
        case "closeWin":
            bannerMessage = "The page requested a close action."
            closePage?()
        case "openScan":
            pendingScanCallback = ValveBridgeLogic.stringValue(payload["callback"])
            bannerMessage = "The page requested native scanning."
            openScanner?()
        default:
            guard !event.isEmpty else { return }
            recordValveActionIfNeeded(deviceName: fallbackDeviceName, eventKey: event)
            bannerMessage = "Recorded a valve action for \(fallbackDeviceName)."
        }
    }

    private func recordValveActionIfNeeded(deviceName: String, eventKey: String) {
        guard !deviceName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        guard lastRecordedEventKey != eventKey else { return }
        lastRecordedEventKey = eventKey
        container?.addRecord(deviceName: deviceName)
    }

    private func injectSessionCookie(_ sessionCookie: String, into webView: WKWebView) {
        guard !sessionCookie.isEmpty else { return }

        let properties: [HTTPCookiePropertyKey: Any] = [
            .domain: ".hgu.edu.cn",
            .path: "/",
            .name: "SESSION",
            .value: sessionCookie,
            .secure: "TRUE"
        ]

        if let cookie = HTTPCookie(properties: properties) {
            webView.configuration.websiteDataStore.httpCookieStore.setCookie(cookie)
            HTTPCookieStorage.shared.setCookie(cookie)
        }
    }

}

private extension String {
    func ifEmpty(_ fallback: String) -> String {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? fallback : self
    }
}
