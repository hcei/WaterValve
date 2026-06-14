package com.hgu.watervalve.shared.data.remote.crypto

internal actual fun tripleDesEncryptPlatform(
    data: ByteArray,
    key: ByteArray,
    iv: ByteArray,
): ByteArray = tripleDesEncryptPure(data, key, iv)

internal actual fun tripleDesDecryptPlatform(
    data: ByteArray,
    key: ByteArray,
    iv: ByteArray,
): ByteArray = tripleDesDecryptPure(data, key, iv)

internal actual fun md5HexPlatform(input: ByteArray): String = md5HexPure(input)

internal actual fun hmacSha512HexPlatform(
    data: ByteArray,
    key: ByteArray,
): String = hmacSha512HexPure(data, key)
