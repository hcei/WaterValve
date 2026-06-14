package com.hgu.watervalve.shared.data.remote.crypto

internal expect fun tripleDesEncryptPlatform(
    data: ByteArray,
    key: ByteArray,
    iv: ByteArray,
): ByteArray

internal expect fun tripleDesDecryptPlatform(
    data: ByteArray,
    key: ByteArray,
    iv: ByteArray,
): ByteArray

internal expect fun md5HexPlatform(input: ByteArray): String

internal expect fun hmacSha512HexPlatform(
    data: ByteArray,
    key: ByteArray,
): String
