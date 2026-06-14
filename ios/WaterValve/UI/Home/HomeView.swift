import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var container: AppContainer

    @State private var isShowingAddSheet = false
    @State private var editingDevice: Device?
    @State private var pendingDeleteDevice: Device?
    @State private var renameDraft = ""
    @State private var isRefreshing = false

    var body: some View {
        List {
            if let notice = appState.homeNotice {
                Section {
                    Label(notice, systemImage: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                }
            }

            if let lastSyncDate = appState.lastSyncDate {
                Section {
                    Label("Last synced: \(DateFormatter.appDateTime.string(from: lastSyncDate))", systemImage: "arrow.triangle.2.circlepath")
                        .foregroundStyle(.secondary)
                }
            }

            if appState.devices.isEmpty {
                Section {
                    EmptyStateView(
                        title: "No Devices Yet",
                        systemImage: "drop.circle",
                        message: "Add a QR link or scan a code to start opening valves from iPhone."
                    )
                    .frame(maxWidth: .infinity)
                }
            } else {
                Section("Devices") {
                    ForEach(appState.devices) { device in
                        NavigationLink(destination: ValveView(device: device)) {
                            DeviceRowView(device: device)
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button(role: .destructive) {
                                pendingDeleteDevice = device
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }

                            Button {
                                editingDevice = device
                                renameDraft = device.name
                            } label: {
                                Label("Rename", systemImage: "pencil")
                            }
                            .tint(.orange)
                        }
                        .swipeActions(edge: .leading, allowsFullSwipe: true) {
                            Button {
                                Task {
                                    await container.toggleStar(id: device.id)
                                }
                            } label: {
                                Label(device.starred ? "Unstar" : "Star", systemImage: device.starred ? "star.slash" : "star.fill")
                            }
                            .tint(.yellow)
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(AppConstants.appDisplayName)
        .refreshable {
            guard !isRefreshing else { return }
            isRefreshing = true
            await container.refreshCloudDevices()
            isRefreshing = false
        }
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Logout") {
                    container.logout()
                }
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                NavigationLink(destination: RecordView()) {
                    Image(systemName: "clock.arrow.circlepath")
                }
                Button {
                    isShowingAddSheet = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .sheet(isPresented: $isShowingAddSheet) {
            AddDeviceSheet(isPresented: $isShowingAddSheet)
                .environmentObject(appState)
                .environmentObject(container)
        }
        .sheet(item: $editingDevice) { device in
            NavigationStack {
                Form {
                    Section("Name") {
                        TextField("Device name", text: $renameDraft)
                    }
                }
                .navigationTitle("Rename Device")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") {
                            editingDevice = nil
                            renameDraft = ""
                        }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Save") {
                            Task {
                                await container.renameDevice(id: device.id, name: renameDraft)
                            }
                            editingDevice = nil
                            renameDraft = ""
                        }
                    }
                }
            }
        }
        .confirmationDialog(
            "Delete Device",
            isPresented: Binding(
                get: { pendingDeleteDevice != nil },
                set: { isPresented in
                    if !isPresented {
                        pendingDeleteDevice = nil
                    }
                }
            ),
            titleVisibility: .visible
        ) {
            if let device = pendingDeleteDevice {
                Button("Delete \(device.displayName)", role: .destructive) {
                    Task {
                        await container.deleteDevice(id: device.id)
                    }
                    pendingDeleteDevice = nil
                }
            }
            Button("Cancel", role: .cancel) {
                pendingDeleteDevice = nil
            }
        }
        .onAppear {
            appState.devices = container.deviceRepository.devices
            appState.records = container.deviceRepository.records
        }
    }
}

private struct DeviceRowView: View {
    let device: Device

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: device.starred ? "drop.fill" : "drop")
                .foregroundStyle(device.starred ? .yellow : .blue)
                .font(.title3)
            VStack(alignment: .leading, spacing: 4) {
                Text(device.displayName)
                    .font(.headline)
                Text(device.qrURL)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            if device.starred {
                Image(systemName: "star.fill")
                    .foregroundStyle(.yellow)
            }
        }
        .padding(.vertical, 4)
    }
}

private struct AddDeviceSheet: View {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var container: AppContainer
    @Binding var isPresented: Bool

    @State private var qrURL = ""
    @State private var customName = ""
    @State private var isAdding = false
    @State private var addError: String?

    var body: some View {
        NavigationStack {
            Form {
                if let addError {
                    Section("Error") {
                        Text(addError)
                            .foregroundStyle(.red)
                    }
                }

                Section("Scanner") {
                    NavigationLink("Open QR Scanner") {
                        QRScannerView { result in
                            qrURL = result
                            addError = nil
                        }
                    }
                }

                Section("Manual Input") {
                    TextField("QR URL or device payload", text: $qrURL, axis: .vertical)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .onChange(of: qrURL) { _ in
                            addError = nil
                        }
                    TextField("Optional display name", text: $customName)
                        .onChange(of: customName) { _ in
                            addError = nil
                        }
                }
            }
            .navigationTitle("Add Device")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") {
                        isPresented = false
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isAdding ? "Adding..." : "Add") {
                        isAdding = true
                        Task {
                            let didSucceed = await container.addDevice(
                                qrURL: qrURL,
                                name: customName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : customName
                            )
                            await MainActor.run {
                                isAdding = false
                                if didSucceed {
                                    isPresented = false
                                } else {
                                    addError = appState.homeNotice ?? "Device could not be added."
                                }
                            }
                        }
                    }
                    .disabled(isAdding || qrURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}

private struct EmptyStateView: View {
    let title: String
    let systemImage: String
    let message: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.system(size: 42))
                .foregroundStyle(.secondary)
            Text(title)
                .font(.headline)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(.vertical, 24)
        .frame(maxWidth: .infinity)
    }
}
