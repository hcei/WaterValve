package com.hgu.watervalve.shared.data.remote.crypto

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal actual fun tripleDesEncryptPlatform(
    data: ByteArray,
    key: ByteArray,
    iv: ByteArray,
): ByteArray {
    val cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding")
    cipher.init(
        Cipher.ENCRYPT_MODE,
        SecretKeySpec(key, "DESede"),
        IvParameterSpec(iv),
    )
    return cipher.doFinal(data)
}

internal actual fun tripleDesDecryptPlatform(
    data: ByteArray,
    key: ByteArray,
    iv: ByteArray,
): ByteArray {
    val cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding")
    cipher.init(
        Cipher.DECRYPT_MODE,
        SecretKeySpec(key, "DESede"),
        IvParameterSpec(iv),
    )
    return cipher.doFinal(data)
}

internal actual fun md5HexPlatform(input: ByteArray): String {
    return MessageDigest.getInstance("MD5")
        .digest(input)
        .joinToString("") { byte -> "%02x".format(byte) }
}

internal actual fun hmacSha512HexPlatform(
    data: ByteArray,
    key: ByteArray,
): String {
    val mac = Mac.getInstance("HmacSHA512")
    mac.init(SecretKeySpec(key, "HmacSHA512"))
    return mac.doFinal(data).joinToString("") { byte -> "%02x".format(byte) }
}
