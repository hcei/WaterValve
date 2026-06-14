import Foundation

final class KeyValueStore {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func string(for key: String) -> String? { defaults.string(forKey: key) }
    func set(_ value: String?, for key: String) {
        defaults.set(value, forKey: key)
    }

    func bool(for key: String) -> Bool {
        defaults.object(forKey: key) as? Bool ?? false
    }

    func set(_ value: Bool, for key: String) {
        defaults.set(value, forKey: key)
    }

    func double(for key: String) -> Double {
        defaults.object(forKey: key) as? Double ?? 0
    }

    func set(_ value: Double, for key: String) {
        defaults.set(value, forKey: key)
    }

    func data(for key: String) -> Data? {
        defaults.data(forKey: key)
    }

    func set(_ value: Data?, for key: String) {
        defaults.set(value, forKey: key)
    }

    func removeAll(keys: [String]) {
        for key in keys {
            defaults.removeObject(forKey: key)
        }
    }

    func decode<T: Decodable>(_ type: T.Type, for key: String) -> T? {
        guard let data = data(for: key) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    func encode<T: Encodable>(_ value: T?, for key: String) {
        guard let value, let data = try? JSONEncoder().encode(value) else {
            defaults.removeObject(forKey: key)
            return
        }
        set(data, for: key)
    }
}
