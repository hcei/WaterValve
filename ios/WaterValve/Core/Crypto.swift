import CommonCrypto
import CryptoKit
import Foundation

enum Crypto {
    private static let tdesKey = "684523174589651002354157"
    private static let tdesIV = "00000000"
    private static let merchantKey = AppConstants.merchantKey

    static func md5(_ input: String) -> String {
        let digest = Insecure.MD5.hash(data: Data(input.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    static func encrypt(_ plaintext: String) -> String {
        guard let data = plaintext.data(using: .utf8),
              let encrypted = crypt(data: data, operation: CCOperation(kCCEncrypt)) else {
            return ""
        }
        return encrypted.base64EncodedString()
    }

    static func decrypt(_ base64: String) -> String {
        guard let data = Data(base64Encoded: base64),
              let decrypted = crypt(data: data, operation: CCOperation(kCCDecrypt)),
              let text = String(data: decrypted, encoding: .utf8) else {
            return ""
        }
        return text
    }

    static func buildParamStr(_ params: [String: String]) -> String {
        var signedParams = params
        signedParams["merchantKey"] = merchantKey
        let sign = sign(signedParams)
        signedParams.removeValue(forKey: "merchantKey")
        signedParams["sign"] = sign

        let sortedPairs = signedParams.sorted { $0.key < $1.key }
        let object = Dictionary(uniqueKeysWithValues: sortedPairs)
        guard JSONSerialization.isValidJSONObject(object),
              let json = try? JSONSerialization.data(withJSONObject: object),
              let text = String(data: json, encoding: .utf8) else {
            return ""
        }
        return encrypt(text)
    }

    static func decryptResponse(_ encryptedBase64: String) -> [String: Any] {
        guard let jsonData = decrypt(encryptedBase64).data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any] else {
            return [:]
        }
        return object
    }

    static func parseDataField(_ response: [String: Any]) -> [String: Any] {
        guard let dataText = response["data"] as? String,
              let data = dataText.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return [:]
        }
        return object
    }

    static func sign(_ params: [String: String]) -> String {
        let concatenated = params.keys.sorted().map { key in
            "\(key)=\(params[key] ?? "")"
        }.joined(separator: "&")
        let digest = md5(concatenated)
        return Data(digest.utf8).base64EncodedString()
    }

    static func signUis(_ data: String, key: String = AppConstants.uisSignKey) -> String {
        let dataBytes = Array(data.utf8)
        let keyBytes = Array(key.utf8)
        var mac = [UInt8](repeating: 0, count: Int(CC_SHA512_DIGEST_LENGTH))
        keyBytes.withUnsafeBytes { keyBuffer in
            dataBytes.withUnsafeBytes { dataBuffer in
                CCHmac(
                    CCHmacAlgorithm(kCCHmacAlgSHA512),
                    keyBuffer.baseAddress,
                    keyBytes.count,
                    dataBuffer.baseAddress,
                    dataBytes.count,
                    &mac
                )
            }
        }
        return mac.map { String(format: "%02x", $0) }.joined()
    }

    static func versionParts(_ version: String) -> [Int] {
        let trimmed = version
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "vV"))
        return trimmed.split(separator: ".").compactMap { Int($0) }
    }

    static func isRemoteVersionNewer(local: String, remote: String) -> Bool {
        let localParts = versionParts(local)
        let remoteParts = versionParts(remote)
        let count = max(localParts.count, remoteParts.count)

        for index in 0..<count {
            let localValue = index < localParts.count ? localParts[index] : 0
            let remoteValue = index < remoteParts.count ? remoteParts[index] : 0
            if remoteValue != localValue {
                return remoteValue > localValue
            }
        }

        return false
    }

    private static func crypt(data: Data, operation: CCOperation) -> Data? {
        let keyData = Data(tdesKey.utf8)
        let ivData = Data(tdesIV.utf8)
        let keyLength = kCCKeySize3DES
        let bufferSize = data.count + kCCBlockSize3DES
        var buffer = Data(count: bufferSize)
        var processedBytes: size_t = 0

        let status = buffer.withUnsafeMutableBytes { bufferBytes in
            data.withUnsafeBytes { dataBytes in
                keyData.withUnsafeBytes { keyBytes in
                    ivData.withUnsafeBytes { ivBytes in
                        CCCrypt(
                            operation,
                            CCAlgorithm(kCCAlgorithm3DES),
                            CCOptions(kCCOptionPKCS7Padding),
                            keyBytes.baseAddress,
                            keyLength,
                            ivBytes.baseAddress,
                            dataBytes.baseAddress,
                            data.count,
                            bufferBytes.baseAddress,
                            bufferSize,
                            &processedBytes
                        )
                    }
                }
            }
        }

        guard status == kCCSuccess else { return nil }
        buffer.removeSubrange(processedBytes..<buffer.count)
        return buffer
    }
}
