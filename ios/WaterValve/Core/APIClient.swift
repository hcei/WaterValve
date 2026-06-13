import Foundation

enum APIClientError: Error {
    case invalidResponse
    case httpStatus(Int)
    case decodingFailed
}

protocol JSONClient {
    func getJSON(from url: URL) async throws -> Any
}

final class APIClient: JSONClient {
    func getJSON(from url: URL) async throws -> Any {
        let (data, response) = try await URLSession.shared.data(from: url)
        guard let http = response as? HTTPURLResponse else { throw APIClientError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else { throw APIClientError.httpStatus(http.statusCode) }
        return try JSONSerialization.jsonObject(with: data)
    }

    func postJSON(from url: URL, body: Any) async throws -> Any {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIClientError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else { throw APIClientError.httpStatus(http.statusCode) }
        return try JSONSerialization.jsonObject(with: data)
    }
}
