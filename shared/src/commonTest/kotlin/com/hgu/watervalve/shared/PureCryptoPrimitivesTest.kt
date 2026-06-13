package com.hgu.watervalve.shared

import com.hgu.watervalve.shared.data.remote.crypto.hmacSha512HexPlatform
import com.hgu.watervalve.shared.data.remote.crypto.hmacSha512HexPure
import com.hgu.watervalve.shared.data.remote.crypto.md5HexPlatform
import com.hgu.watervalve.shared.data.remote.crypto.md5HexPure
import com.hgu.watervalve.shared.data.remote.crypto.tripleDesDecryptPure
import com.hgu.watervalve.shared.data.remote.crypto.tripleDesEncryptPlatform
import com.hgu.watervalve.shared.data.remote.crypto.tripleDesEncryptPure
import com.hgu.watervalve.shared.util.Constants
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PureCryptoPrimitivesTest {
    @Test
    fun pureTripleDesMatchesJvmProvider() {
        val key = Constants.DES_KEY.encodeToByteArray()
        val iv = Constants.DES_IV.encodeToByteArray()
        val data = """{"epId":"1","optNum":"46808","sign":"sample"}""".encodeToByteArray()

        val expected = tripleDesEncryptPlatform(data, key, iv)
        val actual = tripleDesEncryptPure(data, key, iv)

        assertContentEquals(expected, actual)
        assertContentEquals(data, tripleDesDecryptPure(actual, key, iv))
    }

    @Test
    fun pureMd5MatchesJvmProvider() {
        val data = "a=1&b=2&merchantKey=${Constants.MERCHANT_KEY}".encodeToByteArray()

        assertEquals(md5HexPlatform(data), md5HexPure(data))
    }

    @Test
    fun pureHmacSha512MatchesJvmProvider() {
        val data = "nonce=123&timestamp=456".encodeToByteArray()
        val key = Constants.UIS_SIGN_KEY.encodeToByteArray()

        assertEquals(hmacSha512HexPlatform(data, key), hmacSha512HexPure(data, key))
    }
}
