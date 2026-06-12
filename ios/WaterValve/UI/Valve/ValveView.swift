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
        let escaped = escape(result)
        webView?.injectJavaScript("window.__waterValveLastScan='\(escaped)';")
        webView?.injectJavaScript("window.dispatchEvent(new CustomEvent('waterValveScanResult',{detail:{result:'\(escaped)'}}));")
        if let callback = pendingScanCallback?.trimmingCharacters(in: .whitespacesAndNewlines), !callback.isEmpty {
            let callbackScript = buildScanCallbackScript(callback: callback, escapedResult: escaped)
            webView?.injectJavaScript(callbackScript)
        }
        pendingScanCallback = nil
    }

    private func buildValveURL(for device: Device) -> URL? {
        if let direct = URL(string: device.qrURL),
           let scheme = direct.scheme,
           scheme.hasPrefix("http") {
            return direct
        }

        if !device.qrURL.isEmpty {
            let encoded = device.qrURL.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? device.id
            return URL(string: "\(AppConstants.uwcSpaBaseURL.absoluteString)#/openValve?deviceId=\(encoded)")
        }

        return URL(string: "\(AppConstants.uwcSpaBaseURL.absoluteString)#/openValve")
    }

    private func buildTokenInjectionScript(session: UserSession) -> String {
        var script = "(function(){try{"
        script += "if(!window.wx||!window.wx.ready){window.wx={ready:function(cb){if(cb)cb()},config:function(){},error:function(){},checkJsApi:function(opts){if(opts&&opts.success)opts.success({checkResult:{}})},invoke:function(){}};}"
        script += "window.__waterValveBridge={scan:function(){window.location.href='com.hzsun.h5call://bridge?paramjson='+encodeURIComponent(JSON.stringify({action:'openScan',callback:'nativeScan'}));}};"
        script += "window.__valveBridge={token:'\(escape(session.uwcToken))',userId:'\(escape(session.userId))'};"
        script += "localStorage.setItem('uwcToken','\(escape(session.uwcToken))');"
        script += "localStorage.setItem('uisToken','\(escape(session.uisToken))');"
        script += "localStorage.setItem('uiastoken','\(escape(session.uisToken))');"
        script += "localStorage.setItem('uwcAccNum','\(escape(session.accNum))');"
        script += "localStorage.setItem('uwcEpid','\(escape(session.epId))');"
        script += "localStorage.setItem('uwcUserId','\(escape(session.userId))');"
        script += "localStorage.setItem('uwcPerCode','\(escape(session.perCode))');"
        script += "localStorage.setItem('wxMark','1');"
        script += "localStorage.setItem('isSdk',JSON.stringify(true));"
        script += "}catch(e){console.error('[WaterValve iOS] '+e.message);}})();"
        return script
    }

    private func handle(url: URL, deviceName: String) {
        guard url.absoluteString.hasPrefix(AppConstants.h5CallSchemePrefix) else { return }

        let payload = H5CallPayload.parse(url: url)
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
        let payload = parseBridgePayload(message)
        let event = payload["event"] as? String ?? payload["action"] as? String ?? ""

        switch event {
        case "valveOpened":
            let deviceName = stringValue(payload["deviceName"]).ifEmpty(fallbackDeviceName)
            let timestamp = stringValue(payload["timestamp"])
            recordValveActionIfNeeded(deviceName: deviceName, eventKey: "valveOpened:\(deviceName):\(timestamp)")
            bannerMessage = "Recorded a valve action for \(deviceName)."
        case "error":
            let message = stringValue(payload["message"]).ifEmpty("The valve page reported an unknown error.")
            bannerMessage = message
        case "closeWin":
            bannerMessage = "The page requested a close action."
            closePage?()
        case "openScan":
            pendingScanCallback = stringValue(payload["callback"])
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

    private func parseBridgePayload(_ message: Any) -> [String: Any] {
        if let object = message as? [String: Any] {
            return object
        }

        if let text = message as? String,
           let data = text.data(using: .utf8),
           let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            return object
        }

        return [:]
    }

    private func buildScanCallbackScript(callback: String, escapedResult: String) -> String {
        let escapedCallback = escape(callback)
        return """
        (function(){
            try{
                var callbackName='\(escapedCallback)';
                var target=window[callbackName];
                if(typeof target==='function'){
                    target('\(escapedResult)');
                    return;
                }
                var path=callbackName.split('.');
                var scope=window;
                for(var i=0;i<path.length;i++){
                    scope=scope && scope[path[i]];
                }
                if(typeof scope==='function'){
                    scope('\(escapedResult)');
                }
            }catch(e){
                console.error('[WaterValve iOS] '+e.message);
            }
        })();
        """
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

    private func escape(_ string: String) -> String {
        string
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "'", with: "\\'")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\r", with: "\\r")
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
}

private struct H5CallPayload {
    let action: String
    let callback: String

    static func parse(url: URL) -> H5CallPayload? {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let paramJSON = components.queryItems?.first(where: { $0.name == "paramjson" })?.value,
              let decoded = paramJSON.removingPercentEncoding,
              let data = decoded.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }

        return H5CallPayload(
            action: object["action"] as? String ?? "",
            callback: object["callback"] as? String ?? ""
        )
    }
}

private extension String {
    func ifEmpty(_ fallback: String) -> String {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? fallback : self
    }
}
