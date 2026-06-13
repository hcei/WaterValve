@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.hgu.watervalve.shared.data.remote.crypto

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.CommonCrypto.CCAlgorithm
import platform.CommonCrypto.CCCrypt
import platform.CommonCrypto.CCCryptorStatus
import platform.CommonCrypto.CCHmac
import platform.CommonCrypto.CCHmacAlgorithm
import platform.CommonCrypto.CC_LONG
import platform.CommonCrypto.CC_MD5
import platform.CommonCrypto.CC_MD5_DIGEST_LENGTH
import platform.CommonCrypto.CCOperation
import platform.CommonCrypto.CCOptions
import platform.CommonCrypto.CC_SHA512_DIGEST_LENGTH
import platform.CommonCrypto.kCCAlgorithm3DES
import platform.CommonCrypto.kCCDecrypt
import platform.CommonCrypto.kCCEncrypt
import platform.CommonCrypto.kCCOptionPKCS7Padding
import platform.CommonCrypto.kCCSuccess
import platform.CommonCrypto.kCCHmacAlgSHA512
import platform.posix.size_tVar

internal actual fun tripleDesEncryptPlatform(
    data: ByteArray,
    key: ByteArray,
    iv: ByteArray,
): ByteArray = crypt(
    data = data,
    key = key,
    iv = iv,
    operation = CCOperation(kCCEncrypt),
)

internal actual fun tripleDesDecryptPlatform(
    data: ByteArray,
    key: ByteArray,
    iv: ByteArray,
): ByteArray = crypt(
    data = data,
    key = key,
    iv = iv,
    operation = CCOperation(kCCDecrypt),
)

internal actual fun md5HexPlatform(input: ByteArray): String {
    val digest = ByteArray(CC_MD5_DIGEST_LENGTH.toInt())
    input.usePinned { inputPinned ->
        digest.usePinned { digestPinned ->
            CC_MD5(
                inputPinned.addressOf(0),
                input.size.convert<CC_LONG>(),
                digestPinned.addressOf(0),
            )
        }
    }
    return digest.joinToString("") { byte -> byte.toHexByte() }
}

internal actual fun hmacSha512HexPlatform(
    data: ByteArray,
    key: ByteArray,
): String {
    val digest = ByteArray(CC_SHA512_DIGEST_LENGTH.toInt())
    key.usePinned { keyPinned ->
        data.usePinned { dataPinned ->
            digest.usePinned { digestPinned ->
                CCHmac(
                    CCHmacAlgorithm(kCCHmacAlgSHA512),
                    keyPinned.addressOf(0),
                    key.size.convert(),
                    dataPinned.addressOf(0),
                    data.size.convert(),
                    digestPinned.addressOf(0),
                )
            }
        }
    }
    return digest.joinToString("") { byte -> byte.toHexByte() }
}

private fun crypt(
    data: ByteArray,
    key: ByteArray,
    iv: ByteArray,
    operation: CCOperation,
): ByteArray {
    val output = ByteArray(data.size + 32)
    memScoped {
        val outputLength = alloc<size_tVar>()
        val status: CCCryptorStatus = data.usePinned { dataPinned ->
            key.usePinned { keyPinned ->
                iv.usePinned { ivPinned ->
                    output.usePinned { outputPinned ->
                        CCCrypt(
                            operation,
                            CCAlgorithm(kCCAlgorithm3DES),
                            CCOptions(kCCOptionPKCS7Padding),
                            keyPinned.addressOf(0),
                            key.size.convert(),
                            ivPinned.addressOf(0),
                            dataPinned.addressOf(0),
                            data.size.convert(),
                            outputPinned.addressOf(0),
                            output.size.convert(),
                            outputLength.ptr,
                        )
                    }
                }
            }
        }
        check(status == kCCSuccess) { "CCCrypt failed: $status" }
        return output.copyOf(outputLength.value.toInt())
    }
}

private fun Byte.toHexByte(): String {
    return (toInt() and 0xFF).toString(16).padStart(2, '0')
}
