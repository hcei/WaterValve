import SwiftUI
import WebKit

struct WebViewConfig {
    let url: URL
    let userAgent: String
    let onNavigationComplete: (() -> Void)?
    let onUrlChange: ((URL) -> WebViewNavigationPolicy)?
    let onWebViewCreated: ((WKWebView) -> Void)?
    let scriptMessageHandlerName: String?
    let onScriptMessage: ((Any) -> Void)?
    let additionalJavaScript: String?

    init(
        url: URL,
        userAgent: String,
        onNavigationComplete: (() -> Void)? = nil,
        onUrlChange: ((URL) -> WebViewNavigationPolicy)? = nil,
        onWebViewCreated: ((WKWebView) -> Void)? = nil,
        scriptMessageHandlerName: String? = nil,
        onScriptMessage: ((Any) -> Void)? = nil,
        additionalJavaScript: String? = nil
    ) {
        self.url = url
        self.userAgent = userAgent
        self.onNavigationComplete = onNavigationComplete
        self.onUrlChange = onUrlChange
        self.onWebViewCreated = onWebViewCreated
        self.scriptMessageHandlerName = scriptMessageHandlerName
        self.onScriptMessage = onScriptMessage
        self.additionalJavaScript = additionalJavaScript
    }
}

enum WebViewNavigationPolicy {
    case allow
    case cancel
}

struct WebViewScreen: UIViewRepresentable {
    let config: WebViewConfig

    func makeCoordinator() -> Coordinator {
        Coordinator(config: config)
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.processPool = Coordinator.sharedProcessPool
        configuration.websiteDataStore = .default()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        if let handlerName = config.scriptMessageHandlerName, !handlerName.isEmpty {
            configuration.userContentController.add(context.coordinator, name: handlerName)
        }

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.customUserAgent = config.userAgent
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        context.coordinator.attach(webView)
        config.onWebViewCreated?(webView)
        webView.load(URLRequest(url: config.url))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        context.coordinator.config = config
        if uiView.url == nil || uiView.url?.absoluteString != config.url.absoluteString {
            uiView.load(URLRequest(url: config.url))
        }
        uiView.customUserAgent = config.userAgent
    }
}

extension WebViewScreen {
    final class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        static let sharedProcessPool = WKProcessPool()
        var config: WebViewConfig
        weak var webView: WKWebView?

        init(config: WebViewConfig) {
            self.config = config
        }

        func attach(_ webView: WKWebView) {
            self.webView = webView
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = navigationAction.request.url else {
                decisionHandler(.allow)
                return
            }
            if let policy = config.onUrlChange?(url) {
                decisionHandler(policy == .allow ? .allow : .cancel)
                return
            }
            decisionHandler(.allow)
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            if let script = config.additionalJavaScript, !script.isEmpty {
                webView.evaluateJavaScript(script)
            }
            config.onNavigationComplete?()
        }

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard message.name == config.scriptMessageHandlerName else { return }
            config.onScriptMessage?(message.body)
        }
    }
}

extension WKWebView {
    func injectJavaScript(_ script: String) {
        evaluateJavaScript(script)
    }
}
