import BackgroundTasks
import Foundation

@MainActor
final class BackgroundTaskManager {
    private weak var authRepository: AuthRepository?
    private var hasRegisteredTasks = false
    private var isRefreshing = false
    let refreshIdentifier = AppConstants.backgroundRefreshIdentifier

    init(authRepository: AuthRepository? = nil) {
        self.authRepository = authRepository
    }

    func bind(authRepository: AuthRepository) {
        self.authRepository = authRepository
    }

    func registerTasks() {
        if hasRegisteredTasks { return }
        hasRegisteredTasks = true

        BGTaskScheduler.shared.register(forTaskWithIdentifier: refreshIdentifier, using: nil) { [weak self] task in
            guard let self else {
                task.setTaskCompleted(success: false)
                return
            }
            guard let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            Task { @MainActor in
                self.handleRefresh(task: refreshTask)
            }
        }
    }

    func scheduleNextRefreshIfAuthorized() {
        guard authRepository?.currentSession() != nil else { return }
        scheduleNextRefresh()
    }

    func scheduleNextRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: refreshIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 12 * 60 * 60)
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: refreshIdentifier)
        try? BGTaskScheduler.shared.submit(request)
    }

    func cancelScheduledRefresh() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: refreshIdentifier)
    }

    private func handleRefresh(task: BGAppRefreshTask) {
        guard !isRefreshing else {
            task.setTaskCompleted(success: false)
            return
        }

        isRefreshing = true
        scheduleNextRefresh()

        let authRepository = self.authRepository
        let work = Task {
            await authRepository?.refreshUwcToken() ?? false
        }

        task.expirationHandler = {
            work.cancel()
        }

        Task { @MainActor [weak self] in
            let success = await work.value
            self?.isRefreshing = false
            task.setTaskCompleted(success: success)
        }
    }
}
