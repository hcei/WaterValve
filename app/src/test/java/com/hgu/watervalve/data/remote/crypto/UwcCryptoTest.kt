package com.hgu.watervalve.data.remote.crypto

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

/**
 * UwcCrypto 单元测试。
 *
 * 包含：
 * - 加解密往返测试
 * - MD5 签名测试
 * - buildParamStr / decryptResponse 往返测试
 * - 抓包数据验证测试（确保算法与真实服务端一致）
 */
class UwcCryptoTest {

    // ═══════════════════════════════════════════════════════════
    // 基础往返测试
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `encrypt then decrypt returns original`() {
        val original = """{"test":"hello世界","num":123}"""
        val encrypted = UwcCrypto.encrypt(original)
        assertNotNull(encrypted)
        assertTrue(encrypted.isNotEmpty())

        val decrypted = UwcCrypto.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `encrypt produces different output for different input`() {
        val enc1 = UwcCrypto.encrypt("hello")
        val enc2 = UwcCrypto.encrypt("world")
        assertNotEquals(enc1, enc2)
    }

    @Test
    fun `encrypt produces deterministic output`() {
        val plain = "test deterministic"
        val enc1 = UwcCrypto.encrypt(plain)
        val enc2 = UwcCrypto.encrypt(plain)
        assertEquals(enc1, enc2)
    }

    @Test
    fun `decrypt throws on invalid base64`() {
        assertThrows(IllegalArgumentException::class.java) {
            UwcCrypto.decrypt("!!!not valid base64!!!")
        }
    }

    // ═══════════════════════════════════════════════════════════
    // MD5 签名测试
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `sign produces non-empty base64`() {
        val params = mapOf<String, Any>(
            "a" to "1",
            "b" to "2",
            "merchantKey" to "hzsun.com.uwc的sign验签加密key"
        )
        val sign = UwcCrypto.sign(params)
        assertTrue(sign.isNotEmpty())

        // 确认是有效 Base64
        val decoded = Base64.getDecoder().decode(sign)
        assertTrue(decoded.isNotEmpty())
    }

    @Test
    fun `sign is deterministic`() {
        val params = mapOf<String, Any>(
            "b" to "2",
            "a" to "1",
            "merchantKey" to "hzsun.com.uwc的sign验签加密key"
        )
        val sign1 = UwcCrypto.sign(params)
        val sign2 = UwcCrypto.sign(params)
        assertEquals(sign1, sign2)
    }

    @Test
    fun `sign sorts keys alphabetically`() {
        // 不同插入顺序应产出相同 sign
        val params1 = linkedMapOf(
            "c" to "3",
            "a" to "1",
            "b" to "2",
            "merchantKey" to "hzsun.com.uwc的sign验签加密key"
        )
        val params2 = linkedMapOf(
            "a" to "1",
            "b" to "2",
            "c" to "3",
            "merchantKey" to "hzsun.com.uwc的sign验签加密key"
        )
        assertEquals(
            UwcCrypto.sign(params1),
            UwcCrypto.sign(params2)
        )
    }

    @Test
    fun `sign with known input matches expected`() {
        // 与 Python 验证脚本对比：a=1, b=2 的 sign 值
        val params = mapOf<String, Any>(
            "a" to "1",
            "b" to "2",
            "merchantKey" to "hzsun.com.uwc的sign验签加密key"
        )
        val sign = UwcCrypto.sign(params)
        assertEquals("MDZhY2M2NDg5Mjk2MTcwYTE1OWNkNTdiMzllYTJlM2I=", sign)
    }

    // ═══════════════════════════════════════════════════════════
    // buildParamStr / decryptResponse 测试
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `buildParamStr is non-empty`() {
        val params = mapOf<String, Any>("testKey" to "testValue")
        val paramStr = UwcCrypto.buildParamStr(params)
        assertNotNull(paramStr)
        assertTrue(paramStr.isNotEmpty())
    }

    @Test
    fun `buildParamStr is valid base64`() {
        val params = mapOf<String, Any>("key" to "value")
        val paramStr = UwcCrypto.buildParamStr(params)
        // 应该可以直接 Base64 解码
        val decoded = Base64.getDecoder().decode(paramStr)
        assertTrue(decoded.isNotEmpty())
    }

    @Test
    fun `buildParamStr then decryptResponse returns params with sign`() {
        val params = mapOf<String, Any>(
            "optNum" to "46808",
            "epId" to "1"
        )
        val paramStr = UwcCrypto.buildParamStr(params)
        val result = UwcCrypto.decryptResponse(paramStr)

        assertEquals("46808", result["optNum"])
        assertEquals("1", result["epId"])
        assertNotNull(result["sign"])
        assertTrue((result["sign"] as String).isNotEmpty())
    }

    @Test
    fun `empty params buildParamStr roundtrip`() {
        // getSysInfo 使用空参数 {}
        val params = emptyMap<String, Any>()
        val paramStr = UwcCrypto.buildParamStr(params)
        val result = UwcCrypto.decryptResponse(paramStr)

        // 空参数也会有 sign 字段
        assertNotNull(result["sign"])
    }

    @Test
    fun `buildParamStr does NOT contain merchantKey`() {
        val params = mapOf<String, Any>("x" to "y")
        val paramStr = UwcCrypto.buildParamStr(params)
        val result = UwcCrypto.decryptResponse(paramStr)

        assertNull(result["merchantKey"])
    }

    @Test
    fun `decryptResponse returns same regardless of param order in build`() {
        val params1 = linkedMapOf("b" to "2", "a" to "1")
        val params2 = linkedMapOf("a" to "1", "b" to "2")

        val ps1 = UwcCrypto.buildParamStr(params1)
        val ps2 = UwcCrypto.buildParamStr(params2)

        // 不同顺序但相同内容的参数应产出相同 paramStr
        assertEquals(ps1, ps2)
    }

    // ═══════════════════════════════════════════════════════════
    // 抓包数据验证测试（确保与真实服务端一致）
    // ═══════════════════════════════════════════════════════════

    // loginByToken 响应 resultMap（来自 2026-06-06 抓包）
    private val LOGIN_BY_TOKEN_RESP = (
        "sppflaDgPzyJqga7WXiHtzsb6Pj2AtURoYdqsg+JoJLjoNDFaIBw0f9MyVO7chyi+J3k/R8RKv1i8rcD" +
        "bS3c4XAgMulifeFAaRJpNSU7LaAX43RDtreiaV4A4SxvSXsQsVyqM7Up+/07yUt05TjTqbVgLq/4DApr" +
        "+8ksB5EUjzSTM85gNc+UWDugGMYiFx6JfagT2tyFMr5nVLZsBhbHuIVwDjeaZTUnQy0MEohdzyvZivvV" +
        "BS4PcGTTlb6gdE4qCZmxsFv/XpUAnpwL3ICdN+fKOKfhmaOIEBKQ34WKz5Hbba2Z5w26YArbmkQSzbaY" +
        "dGe2+F02EBWaiD79kZEVpbooNGJX8SX/vS1X9Sy5H+DMwp8fJwlspXOmsBuWRNbl3slOFgs0gsTX3j9q" +
        "SGApqh9QD7QJ+kicBy6fH5TofdPNqZfTE89uvPrXtp5f6qTwuQGkBVFiSWwhHFgX5sOVDbeF3GXNa67p" +
        "7k9ecaMYIlssReL3/HwiR/BHXoMQ2KyUzJhHpnLlP1b/3mQkMymBty/io25zJc4HDpCp4X1WoN0SsV5k" +
        "MNDlr2kp5Lps9nWjG9CRzamKHbOh1KHqxyTKzeSIUoCHGrmfGz1hnVpeoWr2RfZY6MHoWO3a+gmWdUBj" +
        "lEmLXq+MfT0="
    )

    @Test
    fun `decrypt loginByToken response from capture`() {
        val result = UwcCrypto.decryptResponse(LOGIN_BY_TOKEN_RESP)

        assertEquals("1", result["code"])
        assertEquals("SUCCESS", result["msg"])

        // data 是二次 JSON 字符串
        val dataField = result["data"] as? String
        assertNotNull(dataField)

        val data = UwcCrypto.parseDataField(result)
        assertNotNull(data["token"])
        assertNotNull(data["accNum"])
        assertNotNull(data["epId"])
    }

    // queryCustom 响应 resultMap
    private val QUERY_CUSTOM_RESP = (
        "sppflaDgPzyJqga7WXiHtzsb6Pj2AtURoYdqsg+JoJLjoNDFaIBw0TUuOnw90UBBRxyHBppjwh8C65GT" +
        "HNO/TijrwbFuIigGrqVtfNAT/YWaGQgkW2Jain7dMwY+w7PBoU9zrhq44Sc4wGgQhvEXcUQloTLKQ6KK" +
        "aJyDMlFqVwCH0w+TPVS9nwWFL543H20NXOtvi+QsrGJkZvsuiYeebpG2libSynri+YSB7o+9Hr8euXm7" +
        "YHJNLMwtxQXU5jJrVcMhCOzg4bMyWBUXpwTTe2YHmPazEhwGgGbc5i3GkW4="
    )

    @Test
    fun `decrypt queryCustom response from capture`() {
        val result = UwcCrypto.decryptResponse(QUERY_CUSTOM_RESP)

        assertEquals("1", result["code"])
        assertEquals("SUCCESS", result["msg"])

        val data = UwcCrypto.parseDataField(result)
        // queryCustom 返回用水系统配置
        assertNotNull(data["allowUseWaterImmediately"])
    }

    // getSysInfo 响应 resultMap
    private val GET_SYS_INFO_RESP = (
        "sppflaDgPzyJqga7WXiHtzsb6Pj2AtURoYdqsg+JoJLjoNDFaIBw0UOlPdlvjQuSNY7nGiaAgrBAfB+0" +
        "bkhdAJuLPA8tRJSIXGVmGKY1rDVocFbO46aLAEynLwZsJGR0amLfnk0p1cjEdTfNfZFDhawIbS7YQmKx" +
        "ap8044tGN/9PUvOWdWpGnevKFq7BHf6wwodlcDR2cj/qQc0iSD085YSd1AAEMSrSUwVwVsJQ58qpetWV" +
        "Cj3DSJH8howZBLBtVyT1C/lhwJtP55bYprzN/9cqC0KmVtLCrtgf7UuXE4lrWtbQEnSqgKJECmYfk5/F" +
        "im9QroC3mEw="
    )

    @Test
    fun `decrypt getSysInfo response from capture`() {
        val result = UwcCrypto.decryptResponse(GET_SYS_INFO_RESP)

        assertEquals("1", result["code"])
        assertEquals("SUCCESS", result["msg"])

        val data = UwcCrypto.parseDataField(result)
        assertNotNull(data["appId"])
        assertNotNull(data["appSecret"])
    }

    // loginByToken 请求 paramStr（URL decoded）
    private val LOGIN_BY_TOKEN_REQ_PARAM = (
        "9saa3Sg3nZzBLxpT+x0zNkThcZuONffANzWwJjaml6gCkZCRwHFZwL0endseoSFTrsXtVlDS8ag3" +
        "/P8xEGdNs0jWaO1BkdUzo3fVAAAUaASnrcVobMl+DajxMqXvttP6eJRrWkcY4GxcXWe7w0J0UYCv" +
        "6MCEUfShmk5Oywb5uEZknBW5/zIsCVnn9C/gS1uPwV7+hruvydYzwdY8qGe81HYgaVnwDyLBdgcC" +
        "ktmZqoOOrVt5n4NcrQf31VECOhlRxwMTpAxADZN5kIeXTV6vKGW0rf0q4g0T6ArL4MmFxypjjSDA" +
        "15HJPXg9+qZiyug8NHljvjojLESSndFVOjmxs/7jXybh/k5gNMNp2jZpOLV1yL1w6GMQtKzw3Ycj" +
        "NTHGI9fFAb0Nj12iiVCDq+opSuH9Xm3PgIYPqFzWE+d/pGaQ2L8d8fYk4qHoxGgYB/hS2ayFAZNz" +
        "MZ/2+JRYSoMJdBdy/cDU3EKkBNdG1LAFLHf3fgVKROdOnc35MGxad8RhixY/my/rdvg8eljdXYek" +
        "rhwKEzlAVS9Nwx2whh8VsG/l3YdSImBakl4QCn7D6wcadeQI4Ver4voJ0KKx47sxysn8HCkZC1Qe" +
        "qkOpzxLG6Dxf4BteyUL0Us+4j25ZxwaTkTIpJB/lc41ArXIyfEucT4thiYhiKtTAirv2e2cuRsXQ" +
        "k7otI5CZFL1ute3g992DGQYa6p1niFKdFa7pWREG8DAfehtiA957y3Ny4QzHVIlcnqZj9da6qJon" +
        "CxdLKvD9FN3KBmprRElVGEuIiVRcwrJGaj4gbka87WvovwCPzGH1MIE5JEU9lIZEG6iYlZUTAJw/" +
        "KsbuUwLhspeG3cPS1A=="
    )

    @Test
    fun `decrypt loginByToken request from capture confirms uiastoken param`() {
        val result = UwcCrypto.decryptResponse(LOGIN_BY_TOKEN_REQ_PARAM)

        // loginByToken 请求体包含 uiastoken（UIS JWT）
        val uiastoken = result["uiastoken"] as? String
        assertNotNull(uiastoken)
        assertTrue(uiastoken!!.startsWith("eyJ"))  // JWT header
    }

    @Test
    fun `decrypt queryCustom request from capture confirms epId param`() {
        // URL decoded queryCustom paramStr
        val reqParam = "6b0hUO2zzmLgioxPoZCbylBVRqW3Xpxqtn+vRStkq7je6VgwtDKU9140m76lPE2n8dbezzw2e8IUTjmA/+GdIFSXUKA9tFu5"
        val result = UwcCrypto.decryptResponse(reqParam)

        assertEquals("1", result["epId"])
    }

    // ═══════════════════════════════════════════════════════════
    // parseDataField 测试
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `parseDataField extracts nested data JSON`() {
        val response = UwcCrypto.decryptResponse(LOGIN_BY_TOKEN_RESP)
        val data = UwcCrypto.parseDataField(response)

        assertNotNull(data["token"])
        assertNotNull(data["accNum"])
        assertNotNull(data["expireTime"])
        assertNotNull(data["perCode"])
    }

    @Test
    fun `parseDataField returns empty map for missing data field`() {
        val result = UwcCrypto.parseDataField(emptyMap())
        assertTrue(result.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════
    // 工具方法测试
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `generateNonce returns UUID format`() {
        val nonce = UwcCrypto.generateNonce()
        // UUID v4 格式: 8-4-4-4-12
        assertTrue(nonce.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun `generateNonce returns unique values`() {
        val nonces = (1..10).map { UwcCrypto.generateNonce() }
        assertEquals(10, nonces.distinct().size)
    }

    @Test
    fun `generateTimestamp returns reasonable value`() {
        val ts = UwcCrypto.generateTimestamp()
        val now = System.currentTimeMillis()
        // 时间戳应在当前时间 ± 10 秒内
        assertTrue(ts in (now - 10_000)..(now + 10_000))
    }
}
