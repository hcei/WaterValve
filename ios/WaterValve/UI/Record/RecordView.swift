import SwiftUI

@MainActor
struct RecordView: View {
    @EnvironmentObject private var appState: AppState
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        Group {
            if appState.records.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "clock.arrow.trianglehead.counterclockwise.rotate.90")
                        .font(.system(size: 42))
                        .foregroundStyle(.secondary)
                    Text("No Valve Records")
                        .font(.headline)
                    Text("Records appear here after the web page triggers a valve action.")
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                .padding()
            } else {
                List(appState.records) { record in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(record.deviceName)
                        Text(DateFormatter.appDateTime.string(from: record.timestamp))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .swipeActions {
                        Button(role: .destructive) {
                            container.deleteRecord(id: record.id)
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
        }
        .navigationTitle("Valve Records")
        .toolbar {
            if !appState.records.isEmpty {
                Button("Clear") {
                    container.clearRecords()
                }
            }
        }
        .onAppear {
            appState.records = container.deviceRepository.records
        }
    }
}
