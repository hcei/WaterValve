package com.hgu.watervalve.shared.data.remote.crypto

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

internal fun tripleDesEncryptPure(
    data: ByteArray,
    key: ByteArray,
    iv: ByteArray,
): ByteArray {
    require(key.size == 24) { "TripleDES key must be 24 bytes" }
    require(iv.size == DES_BLOCK_SIZE) { "TripleDES IV must be 8 bytes" }

    val padded = pkcs7Pad(data, DES_BLOCK_SIZE)
    val output = ByteArray(padded.size)
    var previous = iv.copyOf()

    for (offset in padded.indices step DES_BLOCK_SIZE) {
        val block = ByteArray(DES_BLOCK_SIZE) { index ->
            (padded[offset + index].toInt() xor previous[index].toInt()).toByte()
        }
        val encrypted = tripleDesBlock(block, key, encrypt = true)
        encrypted.copyInto(output, destinationOffset = offset)
        previous = encrypted
    }

    return output
}

internal fun tripleDesDecryptPure(
    data: ByteArray,
    key: ByteArray,
    iv: ByteArray,
): ByteArray {
    require(key.size == 24) { "TripleDES key must be 24 bytes" }
    require(iv.size == DES_BLOCK_SIZE) { "TripleDES IV must be 8 bytes" }
    require(data.size % DES_BLOCK_SIZE == 0) { "TripleDES ciphertext must be block aligned" }

    val output = ByteArray(data.size)
    var previous = iv.copyOf()

    for (offset in data.indices step DES_BLOCK_SIZE) {
        val block = data.copyOfRange(offset, offset + DES_BLOCK_SIZE)
        val decrypted = tripleDesBlock(block, key, encrypt = false)
        for (index in 0 until DES_BLOCK_SIZE) {
            output[offset + index] = (decrypted[index].toInt() xor previous[index].toInt()).toByte()
        }
        previous = block
    }

    return pkcs7Unpad(output, DES_BLOCK_SIZE)
}

internal fun md5HexPure(input: ByteArray): String {
    return md5Bytes(input).joinToString("") { byte -> byte.toHexByte() }
}

internal fun hmacSha512HexPure(
    data: ByteArray,
    key: ByteArray,
): String {
    return hmacSha512Bytes(data, key).joinToString("") { byte -> byte.toHexByte() }
}

private const val DES_BLOCK_SIZE = 8

private fun tripleDesBlock(block: ByteArray, key: ByteArray, encrypt: Boolean): ByteArray {
    val k1 = key.copyOfRange(0, 8)
    val k2 = key.copyOfRange(8, 16)
    val k3 = key.copyOfRange(16, 24)

    return if (encrypt) {
        desBlock(
            desBlock(
                desBlock(block, k1, encrypt = true),
                k2,
                encrypt = false,
            ),
            k3,
            encrypt = true,
        )
    } else {
        desBlock(
            desBlock(
                desBlock(block, k3, encrypt = false),
                k2,
                encrypt = true,
            ),
            k1,
            encrypt = false,
        )
    }
}

private fun desBlock(block: ByteArray, key: ByteArray, encrypt: Boolean): ByteArray {
    val subkeys = desSubkeys(key)
    val roundKeys = if (encrypt) subkeys else subkeys.reversedArray()
    val permuted = permute(bytesToLong(block), inputSize = 64, IP)
    var left = ((permuted ushr 32) and 0xFFFFFFFFL).toInt()
    var right = (permuted and 0xFFFFFFFFL).toInt()

    for (subkey in roundKeys) {
        val nextLeft = right
        right = left xor desFeistel(right, subkey)
        left = nextLeft
    }

    val preOutput = ((right.toLong() and 0xFFFFFFFFL) shl 32) or (left.toLong() and 0xFFFFFFFFL)
    return longToBytes(permute(preOutput, inputSize = 64, FP), DES_BLOCK_SIZE)
}

private fun desSubkeys(key: ByteArray): LongArray {
    val permuted = permute(bytesToLong(key), inputSize = 64, PC1)
    var c = ((permuted ushr 28) and 0x0FFFFFFFL).toInt()
    var d = (permuted and 0x0FFFFFFFL).toInt()
    val subkeys = LongArray(16)

    for (round in 0 until 16) {
        c = rotate28(c, SHIFTS[round])
        d = rotate28(d, SHIFTS[round])
        val cd = ((c.toLong() and 0x0FFFFFFFL) shl 28) or (d.toLong() and 0x0FFFFFFFL)
        subkeys[round] = permute(cd, inputSize = 56, PC2)
    }

    return subkeys
}

private fun desFeistel(right: Int, subkey: Long): Int {
    val expanded = permute(right.toLong() and 0xFFFFFFFFL, inputSize = 32, E) xor subkey
    var substituted = 0

    for (box in 0 until 8) {
        val sixBits = ((expanded ushr (42 - (box * 6))) and 0x3FL).toInt()
        val row = ((sixBits and 0x20) ushr 4) or (sixBits and 0x01)
        val column = (sixBits ushr 1) and 0x0F
        substituted = (substituted shl 4) or S_BOXES[box][(row * 16) + column]
    }

    return permute(substituted.toLong() and 0xFFFFFFFFL, inputSize = 32, P).toInt()
}

private fun permute(input: Long, inputSize: Int, table: IntArray): Long {
    var output = 0L
    for (position in table) {
        output = (output shl 1) or ((input ushr (inputSize - position)) and 1L)
    }
    return output
}

private fun rotate28(value: Int, bits: Int): Int {
    return ((value shl bits) or (value ushr (28 - bits))) and 0x0FFFFFFF
}

private fun pkcs7Pad(data: ByteArray, blockSize: Int): ByteArray {
    val padding = blockSize - (data.size % blockSize)
    val output = data.copyOf(data.size + padding)
    for (index in data.size until output.size) {
        output[index] = padding.toByte()
    }
    return output
}

private fun pkcs7Unpad(data: ByteArray, blockSize: Int): ByteArray {
    require(data.isNotEmpty()) { "Invalid PKCS7 payload" }
    val padding = data.last().toInt() and 0xFF
    require(padding in 1..blockSize && padding <= data.size) { "Invalid PKCS7 padding" }
    for (index in data.size - padding until data.size) {
        require((data[index].toInt() and 0xFF) == padding) { "Invalid PKCS7 padding" }
    }
    return data.copyOf(data.size - padding)
}

private fun md5Bytes(input: ByteArray): ByteArray {
    val padded = md5Pad(input)
    var a = 0x67452301
    var b = 0xEFCDAB89.toInt()
    var c = 0x98BADCFE.toInt()
    var d = 0x10325476
    val words = IntArray(16)

    for (offset in padded.indices step 64) {
        for (index in 0 until 16) {
            val wordOffset = offset + (index * 4)
            words[index] =
                (padded[wordOffset].toInt() and 0xFF) or
                    ((padded[wordOffset + 1].toInt() and 0xFF) shl 8) or
                    ((padded[wordOffset + 2].toInt() and 0xFF) shl 16) or
                    ((padded[wordOffset + 3].toInt() and 0xFF) shl 24)
        }

        var aa = a
        var bb = b
        var cc = c
        var dd = d

        for (index in 0 until 64) {
            val f: Int
            val g: Int
            when (index) {
                in 0..15 -> {
                    f = (bb and cc) or (bb.inv() and dd)
                    g = index
                }
                in 16..31 -> {
                    f = (dd and bb) or (dd.inv() and cc)
                    g = (5 * index + 1) % 16
                }
                in 32..47 -> {
                    f = bb xor cc xor dd
                    g = (3 * index + 5) % 16
                }
                else -> {
                    f = cc xor (bb or dd.inv())
                    g = (7 * index) % 16
                }
            }

            val next = bb + rotateLeft(aa + f + MD5_K[index] + words[g], MD5_S[index])
            aa = dd
            dd = cc
            cc = bb
            bb = next
        }

        a += aa
        b += bb
        c += cc
        d += dd
    }

    return intArrayOf(a, b, c, d).toLittleEndianBytes()
}

private fun md5Pad(input: ByteArray): ByteArray {
    val bitLength = input.size.toLong() * 8L
    var paddedLength = input.size + 1
    while (paddedLength % 64 != 56) {
        paddedLength++
    }

    val output = ByteArray(paddedLength + 8)
    input.copyInto(output)
    output[input.size] = 0x80.toByte()
    for (index in 0 until 8) {
        output[paddedLength + index] = ((bitLength ushr (8 * index)) and 0xFF).toByte()
    }
    return output
}

private fun hmacSha512Bytes(data: ByteArray, key: ByteArray): ByteArray {
    val blockSize = 128
    val normalizedKey = if (key.size > blockSize) sha512Bytes(key) else key
    val keyBlock = ByteArray(blockSize)
    normalizedKey.copyInto(keyBlock)

    val innerPad = ByteArray(blockSize)
    val outerPad = ByteArray(blockSize)
    for (index in 0 until blockSize) {
        innerPad[index] = (keyBlock[index].toInt() xor 0x36).toByte()
        outerPad[index] = (keyBlock[index].toInt() xor 0x5C).toByte()
    }

    return sha512Bytes(concat(outerPad, sha512Bytes(concat(innerPad, data))))
}

private fun sha512Bytes(input: ByteArray): ByteArray {
    val padded = sha512Pad(input)
    val hash = SHA512_INITIAL.copyOf()
    val words = LongArray(80)

    for (offset in padded.indices step 128) {
        for (index in 0 until 16) {
            words[index] = bytesToLong(padded, offset + (index * 8))
        }
        for (index in 16 until 80) {
            words[index] = smallSigma1(words[index - 2]) +
                words[index - 7] +
                smallSigma0(words[index - 15]) +
                words[index - 16]
        }

        var a = hash[0]
        var b = hash[1]
        var c = hash[2]
        var d = hash[3]
        var e = hash[4]
        var f = hash[5]
        var g = hash[6]
        var h = hash[7]

        for (index in 0 until 80) {
            val t1 = h + bigSigma1(e) + ch(e, f, g) + SHA512_K[index] + words[index]
            val t2 = bigSigma0(a) + maj(a, b, c)
            h = g
            g = f
            f = e
            e = d + t1
            d = c
            c = b
            b = a
            a = t1 + t2
        }

        hash[0] += a
        hash[1] += b
        hash[2] += c
        hash[3] += d
        hash[4] += e
        hash[5] += f
        hash[6] += g
        hash[7] += h
    }

    val output = ByteArray(64)
    for (index in hash.indices) {
        longToBytes(hash[index], 8).copyInto(output, destinationOffset = index * 8)
    }
    return output
}

private fun sha512Pad(input: ByteArray): ByteArray {
    val bitLength = input.size.toLong() * 8L
    var paddedLength = input.size + 1
    while (paddedLength % 128 != 112) {
        paddedLength++
    }

    val output = ByteArray(paddedLength + 16)
    input.copyInto(output)
    output[input.size] = 0x80.toByte()
    for (index in 0 until 8) {
        output[paddedLength + 8 + index] = ((bitLength ushr (8 * (7 - index))) and 0xFF).toByte()
    }
    return output
}

private fun rotateLeft(value: Int, bits: Int): Int {
    return (value shl bits) or (value ushr (32 - bits))
}

private fun rotateRight(value: Long, bits: Int): Long {
    return (value ushr bits) or (value shl (64 - bits))
}

private fun ch(x: Long, y: Long, z: Long): Long = (x and y) xor (x.inv() and z)

private fun maj(x: Long, y: Long, z: Long): Long = (x and y) xor (x and z) xor (y and z)

private fun bigSigma0(value: Long): Long = rotateRight(value, 28) xor rotateRight(value, 34) xor rotateRight(value, 39)

private fun bigSigma1(value: Long): Long = rotateRight(value, 14) xor rotateRight(value, 18) xor rotateRight(value, 41)

private fun smallSigma0(value: Long): Long = rotateRight(value, 1) xor rotateRight(value, 8) xor (value ushr 7)

private fun smallSigma1(value: Long): Long = rotateRight(value, 19) xor rotateRight(value, 61) xor (value ushr 6)

private fun bytesToLong(bytes: ByteArray, offset: Int = 0): Long {
    var value = 0L
    for (index in 0 until 8) {
        value = (value shl 8) or (bytes[offset + index].toLong() and 0xFFL)
    }
    return value
}

private fun longToBytes(value: Long, size: Int): ByteArray {
    val output = ByteArray(size)
    for (index in 0 until size) {
        output[index] = ((value ushr (8 * (size - 1 - index))) and 0xFFL).toByte()
    }
    return output
}

private fun IntArray.toLittleEndianBytes(): ByteArray {
    val output = ByteArray(size * 4)
    for (index in indices) {
        val value = this[index]
        val offset = index * 4
        output[offset] = (value and 0xFF).toByte()
        output[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        output[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        output[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
    return output
}

private fun concat(first: ByteArray, second: ByteArray): ByteArray {
    val output = ByteArray(first.size + second.size)
    first.copyInto(output)
    second.copyInto(output, destinationOffset = first.size)
    return output
}

private fun Byte.toHexByte(): String {
    return (toInt() and 0xFF).toString(16).padStart(2, '0')
}

private val MD5_S = intArrayOf(
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
)

private val MD5_K = IntArray(64) { index ->
    floor(abs(sin((index + 1).toDouble())) * 4294967296.0).toLong().toInt()
}

private val IP = intArrayOf(
    58, 50, 42, 34, 26, 18, 10, 2,
    60, 52, 44, 36, 28, 20, 12, 4,
    62, 54, 46, 38, 30, 22, 14, 6,
    64, 56, 48, 40, 32, 24, 16, 8,
    57, 49, 41, 33, 25, 17, 9, 1,
    59, 51, 43, 35, 27, 19, 11, 3,
    61, 53, 45, 37, 29, 21, 13, 5,
    63, 55, 47, 39, 31, 23, 15, 7,
)

private val FP = intArrayOf(
    40, 8, 48, 16, 56, 24, 64, 32,
    39, 7, 47, 15, 55, 23, 63, 31,
    38, 6, 46, 14, 54, 22, 62, 30,
    37, 5, 45, 13, 53, 21, 61, 29,
    36, 4, 44, 12, 52, 20, 60, 28,
    35, 3, 43, 11, 51, 19, 59, 27,
    34, 2, 42, 10, 50, 18, 58, 26,
    33, 1, 41, 9, 49, 17, 57, 25,
)

private val E = intArrayOf(
    32, 1, 2, 3, 4, 5,
    4, 5, 6, 7, 8, 9,
    8, 9, 10, 11, 12, 13,
    12, 13, 14, 15, 16, 17,
    16, 17, 18, 19, 20, 21,
    20, 21, 22, 23, 24, 25,
    24, 25, 26, 27, 28, 29,
    28, 29, 30, 31, 32, 1,
)

private val P = intArrayOf(
    16, 7, 20, 21,
    29, 12, 28, 17,
    1, 15, 23, 26,
    5, 18, 31, 10,
    2, 8, 24, 14,
    32, 27, 3, 9,
    19, 13, 30, 6,
    22, 11, 4, 25,
)

private val PC1 = intArrayOf(
    57, 49, 41, 33, 25, 17, 9,
    1, 58, 50, 42, 34, 26, 18,
    10, 2, 59, 51, 43, 35, 27,
    19, 11, 3, 60, 52, 44, 36,
    63, 55, 47, 39, 31, 23, 15,
    7, 62, 54, 46, 38, 30, 22,
    14, 6, 61, 53, 45, 37, 29,
    21, 13, 5, 28, 20, 12, 4,
)

private val PC2 = intArrayOf(
    14, 17, 11, 24, 1, 5,
    3, 28, 15, 6, 21, 10,
    23, 19, 12, 4, 26, 8,
    16, 7, 27, 20, 13, 2,
    41, 52, 31, 37, 47, 55,
    30, 40, 51, 45, 33, 48,
    44, 49, 39, 56, 34, 53,
    46, 42, 50, 36, 29, 32,
)

private val SHIFTS = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)

private val S_BOXES = arrayOf(
    intArrayOf(
        14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7,
        0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
        4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0,
        15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13,
    ),
    intArrayOf(
        15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10,
        3, 13, 4, 7, 15, 2, 8, 14, 12, 0, 1, 10, 6, 9, 11, 5,
        0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15,
        13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9,
    ),
    intArrayOf(
        10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8,
        13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
        13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7,
        1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12,
    ),
    intArrayOf(
        7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15,
        13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
        10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4,
        3, 15, 0, 6, 10, 1, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14,
    ),
    intArrayOf(
        2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9,
        14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
        4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14,
        11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3,
    ),
    intArrayOf(
        12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11,
        10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
        9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6,
        4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13,
    ),
    intArrayOf(
        4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1,
        13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
        1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2,
        6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12,
    ),
    intArrayOf(
        13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7,
        1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
        7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8,
        2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11,
    ),
)

private val SHA512_INITIAL = longArrayOf(
    0x6a09e667f3bcc908UL.toLong(),
    0xbb67ae8584caa73bUL.toLong(),
    0x3c6ef372fe94f82bUL.toLong(),
    0xa54ff53a5f1d36f1UL.toLong(),
    0x510e527fade682d1UL.toLong(),
    0x9b05688c2b3e6c1fUL.toLong(),
    0x1f83d9abfb41bd6bUL.toLong(),
    0x5be0cd19137e2179UL.toLong(),
)

private val SHA512_K = longArrayOf(
    0x428a2f98d728ae22UL.toLong(), 0x7137449123ef65cdUL.toLong(), 0xb5c0fbcfec4d3b2fUL.toLong(), 0xe9b5dba58189dbbcUL.toLong(),
    0x3956c25bf348b538UL.toLong(), 0x59f111f1b605d019UL.toLong(), 0x923f82a4af194f9bUL.toLong(), 0xab1c5ed5da6d8118UL.toLong(),
    0xd807aa98a3030242UL.toLong(), 0x12835b0145706fbeUL.toLong(), 0x243185be4ee4b28cUL.toLong(), 0x550c7dc3d5ffb4e2UL.toLong(),
    0x72be5d74f27b896fUL.toLong(), 0x80deb1fe3b1696b1UL.toLong(), 0x9bdc06a725c71235UL.toLong(), 0xc19bf174cf692694UL.toLong(),
    0xe49b69c19ef14ad2UL.toLong(), 0xefbe4786384f25e3UL.toLong(), 0x0fc19dc68b8cd5b5UL.toLong(), 0x240ca1cc77ac9c65UL.toLong(),
    0x2de92c6f592b0275UL.toLong(), 0x4a7484aa6ea6e483UL.toLong(), 0x5cb0a9dcbd41fbd4UL.toLong(), 0x76f988da831153b5UL.toLong(),
    0x983e5152ee66dfabUL.toLong(), 0xa831c66d2db43210UL.toLong(), 0xb00327c898fb213fUL.toLong(), 0xbf597fc7beef0ee4UL.toLong(),
    0xc6e00bf33da88fc2UL.toLong(), 0xd5a79147930aa725UL.toLong(), 0x06ca6351e003826fUL.toLong(), 0x142929670a0e6e70UL.toLong(),
    0x27b70a8546d22ffcUL.toLong(), 0x2e1b21385c26c926UL.toLong(), 0x4d2c6dfc5ac42aedUL.toLong(), 0x53380d139d95b3dfUL.toLong(),
    0x650a73548baf63deUL.toLong(), 0x766a0abb3c77b2a8UL.toLong(), 0x81c2c92e47edaee6UL.toLong(), 0x92722c851482353bUL.toLong(),
    0xa2bfe8a14cf10364UL.toLong(), 0xa81a664bbc423001UL.toLong(), 0xc24b8b70d0f89791UL.toLong(), 0xc76c51a30654be30UL.toLong(),
    0xd192e819d6ef5218UL.toLong(), 0xd69906245565a910UL.toLong(), 0xf40e35855771202aUL.toLong(), 0x106aa07032bbd1b8UL.toLong(),
    0x19a4c116b8d2d0c8UL.toLong(), 0x1e376c085141ab53UL.toLong(), 0x2748774cdf8eeb99UL.toLong(), 0x34b0bcb5e19b48a8UL.toLong(),
    0x391c0cb3c5c95a63UL.toLong(), 0x4ed8aa4ae3418acbUL.toLong(), 0x5b9cca4f7763e373UL.toLong(), 0x682e6ff3d6b2b8a3UL.toLong(),
    0x748f82ee5defb2fcUL.toLong(), 0x78a5636f43172f60UL.toLong(), 0x84c87814a1f0ab72UL.toLong(), 0x8cc702081a6439ecUL.toLong(),
    0x90befffa23631e28UL.toLong(), 0xa4506cebde82bde9UL.toLong(), 0xbef9a3f7b2c67915UL.toLong(), 0xc67178f2e372532bUL.toLong(),
    0xca273eceea26619cUL.toLong(), 0xd186b8c721c0c207UL.toLong(), 0xeada7dd6cde0eb1eUL.toLong(), 0xf57d4f7fee6ed178UL.toLong(),
    0x06f067aa72176fbaUL.toLong(), 0x0a637dc5a2c898a6UL.toLong(), 0x113f9804bef90daeUL.toLong(), 0x1b710b35131c471bUL.toLong(),
    0x28db77f523047d84UL.toLong(), 0x32caab7b40c72493UL.toLong(), 0x3c9ebe0a15c9bebcUL.toLong(), 0x431d67c49c100d4cUL.toLong(),
    0x4cc5d4becb3e42b6UL.toLong(), 0x597f299cfc657e2aUL.toLong(), 0x5fcb6fab3ad6faecUL.toLong(), 0x6c44198c4a475817UL.toLong(),
)
