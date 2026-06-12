import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var container: AppContainer
    @StateObject private var viewModel = AuthViewModel()

    var body: some View {
        Group {
            switch viewModel.uiState {
            case .idle:
                introView
            case let .loading(step, message):
                LoginProgressView(currentStep: step, message: message)
            case let .webView(config):
                WebViewScreen(
                    config: WebViewConfig(
                        url: config.url,
                        userAgent: config.userAgent,
                        onUrlChange: { url in
                            guard let ticket = AuthRepository.extractTicket(from: url) else {
                                return .allow
                            }
                            viewModel.handleCasTicket(ticket)
                            return .cancel
                        }
                    )
                )
                .ignoresSafeArea()
            case let .error(message):
                errorView(message)
            }
        }
        .onAppear {
            viewModel.bind(container: container)
        }
    }
}

private extension LoginView {
    var introView: some View {
        VStack(spacing: 18) {
            Spacer()
            Image(systemName: "drop.circle.fill")
                .font(.system(size: 72))
                .foregroundStyle(.tint)
            Text(AppConstants.appDisplayName)
                .font(.largeTitle.bold())
            Text("Sign in with campus CAS, then finish the ticket, UIS token, and UWC token exchange in the native layer.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 24)
            Button("Sign In") {
                viewModel.startLogin()
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            Spacer()
        }
        .padding(24)
    }

    func errorView(_ message: String) -> some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 44))
                .foregroundStyle(.orange)
            Text("Login Failed")
                .font(.title2.bold())
            Text(message)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 24)
            Button("Retry") {
                viewModel.startLogin()
            }
            .buttonStyle(.borderedProminent)
            Spacer()
        }
        .padding(24)
    }
}

@MainActor
final class AuthViewModel: ObservableObject {
    enum UIState: Equatable {
        case idle
        case loading(step: Int, message: String)
        case webView(LoginConfig)
        case error(message: String)
    }

    @Published private(set) var uiState: UIState = .idle

    private weak var container: AppContainer?

    func bind(container: AppContainer) {
        self.container = container
    }

    func startLogin() {
        guard let container else { return }
        let config = container.authRepository.startCasLogin()
        uiState = .loading(step: 1, message: "Loading the CAS login page")
        uiState = .webView(config)
    }

    func handleCasTicket(_ ticket: String) {
        guard let container else { return }
        uiState = .loading(step: 2, message: "Exchanging the CAS ticket")
        Task { [weak self] in
            guard let self else { return }
            do {
                let session = try await container.authRepository.exchangeCasTicket(ticket: ticket) { step, message in
                    self.uiState = .loading(step: step, message: message)
                }
                container.handleLoginSuccess(session: session)
                uiState = .idle
            } catch {
                uiState = .error(message: error.localizedDescription)
            }
        }
    }
}

private struct LoginProgressView: View {
    let currentStep: Int
    let message: String

    var body: some View {
        VStack(spacing: 20) {
            ProgressView()
            VStack(alignment: .leading, spacing: 10) {
                ForEach(1...3, id: \.self) { step in
                    HStack {
                        Circle()
                            .fill(step <= currentStep ? Color.accentColor : Color.gray.opacity(0.35))
                            .frame(width: 10, height: 10)
                        Text(label(for: step))
                        Spacer()
                    }
                }
            }
            .padding()
            Text(message)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(24)
    }

    private func label(for step: Int) -> String {
        switch step {
        case 1:
            return "Load login page"
        case 2:
            return "Verify identity"
        default:
            return "Fetch UWC token"
        }
    }
}
