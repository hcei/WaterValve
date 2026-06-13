package com.hgu.watervalve.shared.data.remote.crypto

import com.hgu.watervalve.shared.platform.currentTimeMillis
import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object UwcCrypto {
    private val json = Json { ignoreUnknownKeys = true }

    private const val TDES_KEY = "684523174589651002354157"
    private const val TDES_IV = "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"
    private const val HMAC_KEY = "hzsun.com.uwc的sign验签加密key"

    fun encrypt(plaintext: String): String {
        val keyBytes = TDES_KEY.encodeToByteArray()
        val ivBytes = TDES_IV.encodeToByteArray()
        val plainBytes = plaintext.encodeToByteArray()
        val encrypted = tripleDesEncrypt(plainBytes, keyBytes, ivBytes)
        return encodeBase64(encrypted)
    }

    fun decrypt(ciphertext: String): String {
        val keyBytes = TDES_KEY.encodeToByteArray()
        val ivBytes = TDES_IV.encodeToByteArray()
        val cipherBytes = decodeBase64(ciphertext)
        val decrypted = tripleDesDecrypt(cipherBytes, keyBytes, ivBytes)
        return decrypted.decodeToString()
    }

    fun md5(input: String): String = md5Hash(input.encodeToByteArray())

    fun hmacSha512(input: String, key: String = HMAC_KEY): String {
        return hmacSha512Hash(input.encodeToByteArray(), key.encodeToByteArray())
    }

    fun sign(params: Map<String, Any?>): String {
        val concat = params.entries
            .sortedBy { it.key }
            .joinToString("&") { entry -> "${entry.key}=${entry.value ?: ""}" }
        return encodeBase64(md5(concat).encodeToByteArray())
    }

    fun buildParamStr(params: Map<String, Any?>): String {
        val withMerchant = params.toMutableMap()
        withMerchant["merchantKey"] = HMAC_KEY
        val signValue = sign(withMerchant)
        withMerchant.remove("merchantKey")
        withMerchant["sign"] = signValue

        val payload = buildJsonObject {
            withMerchant.entries.sortedBy { it.key }.forEach { entry ->
                val key = entry.key
                val value = entry.value
                put(
                    key,
                    when (value) {
                        null -> JsonPrimitive("")
                        is Boolean -> JsonPrimitive(value)
                        is Number -> JsonPrimitive(value)
                        else -> JsonPrimitive(value.toString())
                    }
                )
            }
        }
        return encrypt(payload.toString())
    }

    fun decryptResponse(encryptedBase64: String): Map<String, Any?> {
        val decrypted = decrypt(encryptedBase64)
        return jsonObjectToValueMap(json.parseToJsonElement(decrypted).jsonObject)
    }

    fun parseDataField(response: Map<String, Any?>): Map<String, Any?> {
        val dataField = response["data"] as? String ?: return emptyMap()
        return jsonObjectToValueMap(json.parseToJsonElement(dataField).jsonObject)
    }

    fun signUis(data: String, key: String = HMAC_KEY): String = hmacSha512(data, key)

    fun generateNonce(): String = "${currentTimeMillis()}-${Random.nextInt(100_000, 999_999)}"

    fun generateTimestamp(): Long = currentTimeMillis()

    private fun tripleDesEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val padded = pkcs7Pad(data, 8)
        val key1 = key.copyOfRange(0, 8)
        val key2 = key.copyOfRange(8, 16)
        val key3 = key.copyOfRange(16, 24)
        val result = ByteArray(padded.size)
        var prevBlock = iv
        for (i in padded.indices step 8) {
            val block = padded.copyOfRange(i, i + 8)
            val xored = xorBlocks(block, prevBlock)
            val encrypted = desEncryptBlock(desDecryptBlock(desEncryptBlock(xored, key1), key2), key3)
            encrypted.copyInto(result, i)
            prevBlock = encrypted
        }
        return result
    }

    private fun tripleDesDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val key1 = key.copyOfRange(0, 8)
        val key2 = key.copyOfRange(8, 16)
        val key3 = key.copyOfRange(16, 24)
        val result = ByteArray(data.size)
        var prevBlock = iv
        for (i in data.indices step 8) {
            val block = data.copyOfRange(i, i + 8)
            val decrypted = desDecryptBlock(desEncryptBlock(desDecryptBlock(block, key3), key2), key1)
            val xored = xorBlocks(decrypted, prevBlock)
            xored.copyInto(result, i)
            prevBlock = block
        }
        return pkcs7Unpad(result)
    }

    private fun xorBlocks(a: ByteArray, b: ByteArray): ByteArray {
        return ByteArray(8) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }
    }

    private fun desEncryptBlock(block: ByteArray, key: ByteArray): ByteArray = desProcessBlock(block, key, true)

    private fun desDecryptBlock(block: ByteArray, key: ByteArray): ByteArray = desProcessBlock(block, key, false)

    private fun desProcessBlock(block: ByteArray, key: ByteArray, encrypt: Boolean): ByteArray {
        val subKeys = generateSubKeys(key)
        var left = block.copyOfRange(0, 4)
        var right = block.copyOfRange(4, 8)

        val rounds = if (encrypt) (0..15) else (15 downTo 0)
        for (i in rounds) {
            val temp = right
            right = xorBlocks4(left, feistel(right, subKeys[i]))
            left = temp
        }

        return right + left
    }

    private fun xorBlocks4(a: ByteArray, b: ByteArray): ByteArray {
        return ByteArray(4) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }
    }

    private val PC1 = intArrayOf(
        57, 49, 41, 33, 25, 17, 9, 1, 58, 50, 42, 34, 26, 18,
        10, 2, 59, 51, 43, 35, 27, 19, 11, 3, 60, 52, 44, 36,
        63, 55, 47, 39, 31, 23, 15, 7, 62, 54, 46, 38, 30, 22,
        14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 28, 20, 12, 4
    )

    private val PC2 = intArrayOf(
        14, 17, 11, 24, 1, 5, 3, 28, 15, 6, 21, 10,
        23, 19, 12, 4, 26, 8, 16, 7, 27, 20, 13, 2,
        41, 52, 31, 37, 47, 55, 30, 40, 51, 45, 33, 48,
        44, 49, 39, 56, 34, 53, 46, 42, 50, 36, 29, 32
    )

    private val SHIFTS = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)

    private fun generateSubKeys(key: ByteArray): Array<ByteArray> {
        val keyBits56 = BooleanArray(56) { i -> getBit(key, PC1[i] - 1) }
        val c = keyBits56.copyOfRange(0, 28)
        val d = keyBits56.copyOfRange(28, 56)

        return Array(16) { round ->
            leftShift(c, SHIFTS[round])
            leftShift(d, SHIFTS[round])
            val combined = c + d
            val subKey = ByteArray(6)
            for (i in 0 until 48) {
                setBit(subKey, i, combined[PC2[i] - 1])
            }
            subKey
        }
    }

    private fun leftShift(bits: BooleanArray, count: Int) {
        repeat(count) {
            val first = bits[0]
            for (i in 0 until bits.size - 1) bits[i] = bits[i + 1]
            bits[bits.size - 1] = first
        }
    }

    private val E = intArrayOf(
        32, 1, 2, 3, 4, 5, 4, 5, 6, 7, 8, 9,
        8, 9, 10, 11, 12, 13, 12, 13, 14, 15, 16, 17,
        16, 17, 18, 19, 20, 21, 20, 21, 22, 23, 24, 25,
        24, 25, 26, 27, 28, 29, 28, 29, 30, 31, 32, 1
    )

    private val P = intArrayOf(
        16, 7, 20, 21, 29, 12, 28, 17,
        1, 15, 23, 26, 5, 18, 31, 10,
        2, 8, 24, 14, 32, 27, 3, 9,
        19, 13, 30, 6, 22, 11, 4, 25
    )

    @Suppress("LongMethod")
    private val S_BOXES = arrayOf(
        arrayOf(
            intArrayOf(14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7),
            intArrayOf(0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8),
            intArrayOf(4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0),
            intArrayOf(15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13)
        ),
        arrayOf(
            intArrayOf(15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10),
            intArrayOf(3, 13, 4, 7, 15, 2, 8, 14, 12, 0, 1, 10, 6, 9, 11, 5),
            intArrayOf(0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15),
            intArrayOf(13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9)
        ),
        arrayOf(
            intArrayOf(10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8),
            intArrayOf(13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1),
            intArrayOf(13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7),
            intArrayOf(1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12)
        ),
        arrayOf(
            intArrayOf(7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15),
            intArrayOf(13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9),
            intArrayOf(10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4),
            intArrayOf(3, 15, 0, 6, 10, 1, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14)
        ),
        arrayOf(
            intArrayOf(2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9),
            intArrayOf(14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6),
            intArrayOf(4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14),
            intArrayOf(11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3)
        ),
        arrayOf(
            intArrayOf(12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11),
            intArrayOf(10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8),
            intArrayOf(9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6),
            intArrayOf(4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13)
        ),
        arrayOf(
            intArrayOf(4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1),
            intArrayOf(13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6),
            intArrayOf(1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2),
            intArrayOf(6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12)
        ),
        arrayOf(
            intArrayOf(13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7),
            intArrayOf(1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2),
            intArrayOf(7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8),
            intArrayOf(2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11)
        )
    )

    private fun feistel(r: ByteArray, subKey: ByteArray): ByteArray {
        val expanded = BooleanArray(48) { i -> getBit(r, (E[i] - 1) % 32) }
        for (i in 0 until 48) expanded[i] = expanded[i] xor getBit(subKey, i)

        val output = BooleanArray(32)
        for (i in 0 until 8) {
            val base = i * 6
            val row = (if (expanded[base]) 2 else 0) + (if (expanded[base + 5]) 1 else 0)
            var col = 0
            for (j in 1..4) {
                col = (col shl 1) + (if (expanded[base + j]) 1 else 0)
            }
            val value = S_BOXES[i][row][col]
            for (j in 0..3) {
                output[i * 4 + j] = (value and (1 shl (3 - j))) != 0
            }
        }

        val result = ByteArray(4)
        for (i in 0 until 32) {
            setBit(result, i, output[P[i] - 1])
        }
        return result
    }

    private fun getBit(bytes: ByteArray, index: Int): Boolean {
        val byteIndex = index / 8
        val bitIndex = 7 - (index % 8)
        return if (byteIndex < bytes.size) ((bytes[byteIndex].toInt() shr bitIndex) and 1) == 1 else false
    }

    private fun setBit(bytes: ByteArray, index: Int, value: Boolean) {
        val byteIndex = index / 8
        val bitIndex = 7 - (index % 8)
        if (byteIndex < bytes.size) {
            val mask = 1 shl bitIndex
            bytes[byteIndex] = if (value) {
                (bytes[byteIndex].toInt() or mask).toByte()
            } else {
                (bytes[byteIndex].toInt() and mask.inv()).toByte()
            }
        }
    }

    private fun pkcs7Pad(data: ByteArray, blockSize: Int): ByteArray {
        val padding = blockSize - (data.size % blockSize)
        return data + ByteArray(padding) { padding.toByte() }
    }

    private fun pkcs7Unpad(data: ByteArray): ByteArray {
        val padding = data.last().toInt() and 0xFF
        if (padding !in 1..8) return data
        return data.copyOfRange(0, data.size - padding)
    }

    private val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private fun encodeBase64(data: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0

            sb.append(BASE64_ALPHABET[b0 shr 2])
            sb.append(BASE64_ALPHABET[((b0 shl 4) or (b1 shr 4)) and 0x3F])
            sb.append(if (i + 1 < data.size) BASE64_ALPHABET[((b1 shl 2) or (b2 shr 6)) and 0x3F] else '=')
            sb.append(if (i + 2 < data.size) BASE64_ALPHABET[b2 and 0x3F] else '=')
            i += 3
        }
        return sb.toString()
    }

    private val BASE64_DECODE_MAP: IntArray by lazy {
        val map = IntArray(128) { -1 }
        for (i in BASE64_ALPHABET.indices) {
            map[BASE64_ALPHABET[i].code] = i
        }
        map
    }

    private fun decodeBase64(input: String): ByteArray {
        val s = input.replace("\n", "").replace("\r", "").replace(" ", "")
        val result = mutableListOf<Byte>()
        var i = 0
        while (i < s.length) {
            val c0 = BASE64_DECODE_MAP.getOrElse(s[i].code) { -1 }
            val c1 = if (i + 1 < s.length) BASE64_DECODE_MAP.getOrElse(s[i + 1].code) { -1 } else -1
            val c2 = if (i + 2 < s.length) BASE64_DECODE_MAP.getOrElse(s[i + 2].code) { -1 } else -1
            val c3 = if (i + 3 < s.length) BASE64_DECODE_MAP.getOrElse(s[i + 3].code) { -1 } else -1

            if (c0 >= 0 && c1 >= 0) result.add(((c0 shl 2) or (c1 shr 4)).toByte())
            if (c2 >= 0 && i + 2 < s.length && s[i + 2] != '=') result.add(((c1 shl 4) or (c2 shr 2)).toByte())
            if (c3 >= 0 && i + 3 < s.length && s[i + 3] != '=') result.add(((c2 shl 6) or c3).toByte())
            i += 4
        }
        return result.toByteArray()
    }

    private val MD5_S = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
    )

    private val MD5_K = IntArray(64) { i ->
        ((1L shl 32) * kotlin.math.abs(kotlin.math.sin((i + 1).toDouble()))).toLong().toInt()
    }

    private fun md5Hash(input: ByteArray): String {
        val padded = md5Pad(input)
        var a = 0x67452301
        var b = 0xEFCDAB89.toInt()
        var c = 0x98BADCFE.toInt()
        var d = 0x10325476

        for (offset in padded.indices step 64) {
            val m = IntArray(16)
            for (i in 0 until 16) {
                m[i] = ((padded[offset + i * 4].toInt() and 0xFF)) or
                    ((padded[offset + i * 4 + 1].toInt() and 0xFF) shl 8) or
                    ((padded[offset + i * 4 + 2].toInt() and 0xFF) shl 16) or
                    ((padded[offset + i * 4 + 3].toInt() and 0xFF) shl 24)
            }

            var aa = a
            var bb = b
            var cc = c
            var dd = d

            for (i in 0 until 64) {
                val f: Int
                val g: Int
                when {
                    i < 16 -> {
                        f = (bb and cc) or (bb.inv() and dd)
                        g = i
                    }
                    i < 32 -> {
                        f = (dd and bb) or (dd.inv() and cc)
                        g = (5 * i + 1) % 16
                    }
                    i < 48 -> {
                        f = bb xor cc xor dd
                        g = (3 * i + 5) % 16
                    }
                    else -> {
                        f = cc xor (bb or dd.inv())
                        g = (7 * i) % 16
                    }
                }
                val temp = dd
                dd = cc
                cc = bb
                bb += rotateLeft(aa + f + MD5_K[i] + m[g], MD5_S[i])
                aa = temp
            }

            a += aa
            b += bb
            c += cc
            d += dd
        }

        return toHexStringLittleEndian(a) + toHexStringLittleEndian(b) +
            toHexStringLittleEndian(c) + toHexStringLittleEndian(d)
    }

    private fun md5Pad(input: ByteArray): ByteArray {
        val msgLen = input.size
        val padLen = if (msgLen % 64 < 56) 56 - msgLen % 64 else 120 - msgLen % 64
        val totalLen = msgLen + padLen + 8
        val padded = ByteArray(totalLen)
        input.copyInto(padded)
        padded[msgLen] = 0x80.toByte()
        val bits = msgLen.toLong() * 8
        for (i in 0 until 8) {
            padded[totalLen - 8 + i] = ((bits ushr (i * 8)) and 0xFF).toByte()
        }
        return padded
    }

    private fun toHexStringLittleEndian(value: Int): String {
        val bytes = ByteArray(4)
        bytes[0] = (value and 0xFF).toByte()
        bytes[1] = ((value ushr 8) and 0xFF).toByte()
        bytes[2] = ((value ushr 16) and 0xFF).toByte()
        bytes[3] = ((value ushr 24) and 0xFF).toByte()
        return bytes.joinToString("") { it.toHexByte() }
    }

    private val SHA512_K = longArrayOf(
        0x428A2F98D728AE22uL.toLong(), 0x7137449123EF65CDuL.toLong(), 0xB5C0FBCFEC4D3B2FuL.toLong(), 0xE9B5DBA58189DBBCuL.toLong(),
        0x3956C25BF348B538uL.toLong(), 0x59F111F1B605D019uL.toLong(), 0x923F82A4AF194F9BuL.toLong(), 0xAB1C5ED5DA6D8118uL.toLong(),
        0xD807AA98A3030242uL.toLong(), 0x12835B0145706FBEuL.toLong(), 0x243185BE4EE4B28CuL.toLong(), 0x550C7DC3D5FFB4E2uL.toLong(),
        0x72BE5D74F27B896FuL.toLong(), 0x80DEB1FE3B1696B1uL.toLong(), 0x9BDC06A725C71235uL.toLong(), 0xC19BF174CF692694uL.toLong(),
        0xE49B69C19EF14AD2uL.toLong(), 0xEFBE4786384F25E3uL.toLong(), 0x0FC19DC68B8CD5B5uL.toLong(), 0x240CA1CC77AC9C65uL.toLong(),
        0x2DE92C6F592B0275uL.toLong(), 0x4A7484AA6EA6E483uL.toLong(), 0x5CB0A9DCBD41FBD4uL.toLong(), 0x76F988DA831153B5uL.toLong(),
        0x983E5152EE66DFABuL.toLong(), 0xA831C66D2DB43210uL.toLong(), 0xB00327C898FB213FuL.toLong(), 0xBF597FC7BEEF0EE4uL.toLong(),
        0xC6E00BF33DA88FC2uL.toLong(), 0xD5A79147930AA725uL.toLong(), 0x06CA6351E003826FuL.toLong(), 0x142929670A0E6E70uL.toLong(),
        0x27B70A8546D22FFCuL.toLong(), 0x2E1B21385C26C926uL.toLong(), 0x4D2C6DFC5AC42AEDuL.toLong(), 0x53380D139D95B3DFuL.toLong(),
        0x650A73548BAF63DEuL.toLong(), 0x766A0ABB3C77B2A8uL.toLong(), 0x81C2C92E47EDAEE6uL.toLong(), 0x92722C851482353BuL.toLong(),
        0xA2BFE8A14CF10364uL.toLong(), 0xA81A664BBC423001uL.toLong(), 0xC24B8B70D0F89791uL.toLong(), 0xC76C51A30654BE30uL.toLong(),
        0xD192E819D6EF5218uL.toLong(), 0xD69906245565A910uL.toLong(), 0xF40E35855771202AuL.toLong(), 0x106AA07032BBD1B8uL.toLong(),
        0x19A4C116B8D2D0C8uL.toLong(), 0x1E376C085141AB53uL.toLong(), 0x2748774CDF8EEB99uL.toLong(), 0x34B0BCB5E19B48A8uL.toLong(),
        0x391C0CB3C5C95A63uL.toLong(), 0x4ED8AA4AE3418ACBuL.toLong(), 0x5B9CCA4F7763E373uL.toLong(), 0x682E6FF3D6B2B8A3uL.toLong(),
        0x748F82EE5DEFB2FCuL.toLong(), 0x78A5636F43172F60uL.toLong(), 0x84C87814A1F0AB72uL.toLong(), 0x8CC702081A6439ECuL.toLong(),
        0x90BEFFFA23631E28uL.toLong(), 0xA4506CEBDE82BDE9uL.toLong(), 0xBEF9A3F7B2C67915uL.toLong(), 0xC67178F2E372532BuL.toLong(),
        0xCA273ECEEA26619CuL.toLong(), 0xD186B8C721C0C207uL.toLong(), 0xEADA7DD6CDE0EB1EuL.toLong(), 0xF57D4F7FEE6ED178uL.toLong(),
        0x06F067AA72176FBAuL.toLong(), 0x0A637DC5A2C898A6uL.toLong(), 0x113F9804BEF90DAEuL.toLong(), 0x1B710B35131C471BuL.toLong(),
        0x28DB77F523047D84uL.toLong(), 0x32CAAB7B40C72493uL.toLong(), 0x3C9EBE0A15C9BEBCuL.toLong(), 0x431D67C49C100D4CuL.toLong(),
        0x4CC5D4BECB3E42B6uL.toLong(), 0x597F299CFC657E2AuL.toLong(), 0x5FCB6FAB3AD6FAECuL.toLong(), 0x6C44198C4A475817uL.toLong()
    )

    private fun sha512(input: ByteArray): ByteArray {
        val padded = sha512Pad(input)
        val h = longArrayOf(
            0x6A09E667F3BCC908uL.toLong(), 0xBB67AE8584CAA73BuL.toLong(), 0x3C6EF372FE94F82BuL.toLong(), 0xA54FF53A5F1D36F1uL.toLong(),
            0x510E527FADE682D1uL.toLong(), 0x9B05688C2B3E6C1FuL.toLong(), 0x1F83D9ABFB41BD6BuL.toLong(), 0x5BE0CD19137E2179uL.toLong()
        )

        for (offset in padded.indices step 128) {
            val w = LongArray(80)
            for (i in 0 until 16) {
                w[i] = ((padded[offset + i * 8].toLong() and 0xFF) shl 56) or
                    ((padded[offset + i * 8 + 1].toLong() and 0xFF) shl 48) or
                    ((padded[offset + i * 8 + 2].toLong() and 0xFF) shl 40) or
                    ((padded[offset + i * 8 + 3].toLong() and 0xFF) shl 32) or
                    ((padded[offset + i * 8 + 4].toLong() and 0xFF) shl 24) or
                    ((padded[offset + i * 8 + 5].toLong() and 0xFF) shl 16) or
                    ((padded[offset + i * 8 + 6].toLong() and 0xFF) shl 8) or
                    (padded[offset + i * 8 + 7].toLong() and 0xFF)
            }

            for (i in 16 until 80) {
                val s0 = w[i - 15].rotateRight(1) xor w[i - 15].rotateRight(8) xor (w[i - 15] ushr 7)
                val s1 = w[i - 2].rotateRight(19) xor w[i - 2].rotateRight(61) xor (w[i - 2] ushr 6)
                w[i] = s0 + w[i - 7] + s1 + w[i - 16]
            }

            var a = h[0]
            var b = h[1]
            var c = h[2]
            var d = h[3]
            var e = h[4]
            var f = h[5]
            var g = h[6]
            var hh = h[7]

            for (i in 0 until 80) {
                val s1 = e.rotateRight(14) xor e.rotateRight(18) xor e.rotateRight(41)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = hh + s1 + ch + SHA512_K[i] + w[i]
                val s0 = a.rotateRight(28) xor a.rotateRight(34) xor a.rotateRight(39)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj

                hh = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }

            h[0] += a
            h[1] += b
            h[2] += c
            h[3] += d
            h[4] += e
            h[5] += f
            h[6] += g
            h[7] += hh
        }

        val result = ByteArray(64)
        for (i in 0 until 8) {
            for (j in 0 until 8) {
                result[i * 8 + j] = ((h[i] ushr (56 - j * 8)) and 0xFF).toByte()
            }
        }
        return result
    }

    private fun sha512Pad(input: ByteArray): ByteArray {
        val msgLen = input.size
        val padLen = if (msgLen % 128 < 112) 112 - msgLen % 128 else 240 - msgLen % 128
        val totalLen = msgLen + padLen + 16
        val padded = ByteArray(totalLen)
        input.copyInto(padded)
        padded[msgLen] = 0x80.toByte()
        val bits = msgLen.toLong() * 8
        for (i in 0 until 8) {
            padded[totalLen - 1 - i] = ((bits shr (i * 8)) and 0xFF).toByte()
        }
        return padded
    }

    private fun Long.rotateRight(bits: Int): Long {
        return (this ushr bits) or (this shl (64 - bits))
    }

    private fun hmacSha512Hash(data: ByteArray, keyBytes: ByteArray): String {
        val blockSize = 128
        var key = keyBytes
        if (key.size > blockSize) key = sha512(key)
        if (key.size < blockSize) key = key + ByteArray(blockSize - key.size)

        val oKeyPad = ByteArray(blockSize) { i -> (key[i].toInt() xor 0x5C).toByte() }
        val iKeyPad = ByteArray(blockSize) { i -> (key[i].toInt() xor 0x36).toByte() }
        val inner = sha512(iKeyPad + data)
        val outer = sha512(oKeyPad + inner)
        return outer.joinToString("") { it.toHexByte() }
    }

    private fun rotateLeft(value: Int, bits: Int): Int {
        val normalizedBits = bits and 31
        return (value shl normalizedBits) or (value ushr (32 - normalizedBits))
    }

    private fun Byte.toHexByte(): String {
        return (toInt() and 0xFF).toString(16).padStart(2, '0')
    }

    private fun jsonObjectToValueMap(source: JsonObject): Map<String, Any?> {
        return source.mapValues { (_, value) ->
            when {
                value is JsonObject -> jsonObjectToValueMap(value)
                value.jsonPrimitive.booleanOrNull != null -> value.jsonPrimitive.boolean
                value.jsonPrimitive.doubleOrNull != null -> value.jsonPrimitive.content.toDouble()
                else -> value.jsonPrimitive.content
            }
        }
    }
}
