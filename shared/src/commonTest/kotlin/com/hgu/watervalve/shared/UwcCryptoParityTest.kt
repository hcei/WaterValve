package com.hgu.watervalve.shared

import com.hgu.watervalve.shared.data.remote.crypto.UwcCrypto
import com.hgu.watervalve.shared.util.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UwcCryptoParityTest {
    @Test
    fun signMatchesKnownAndroidValue() {
        val sign = UwcCrypto.sign(
            mapOf(
                "a" to "1",
                "b" to "2",
                "merchantKey" to Constants.MERCHANT_KEY,
            )
        )

        assertEquals("MDZhY2M2NDg5Mjk2MTcwYTE1OWNkNTdiMzllYTJlM2I=", sign)
    }

    @Test
    fun buildParamStrRoundTripsAndDropsMerchantKey() {
        val encrypted = UwcCrypto.buildParamStr(
            mapOf(
                "optNum" to "46808",
                "epId" to "1",
            )
        )

        val decrypted = UwcCrypto.decryptResponse(encrypted)
        assertEquals("46808", decrypted["optNum"])
        assertEquals("1", decrypted["epId"])
        assertFalse(decrypted.containsKey("merchantKey"))
        assertTrue((decrypted["sign"] as? String).isNullOrBlank().not())
    }

    @Test
    fun decryptsCapturedLoginByTokenResponse() {
        val response = UwcCrypto.decryptResponse(LOGIN_BY_TOKEN_RESPONSE)

        assertEquals("1", response["code"]?.toString())
        assertEquals("SUCCESS", response["msg"])

        val data = UwcCrypto.parseDataField(response)
        assertTrue((data["token"] as? String).isNullOrBlank().not())
        assertNotNull(data["accNum"])
        assertNotNull(data["epId"])
    }

    @Test
    fun decryptsCapturedRequestPayload() {
        val request = UwcCrypto.decryptResponse(LOGIN_BY_TOKEN_REQUEST_PARAM)
        val uiasToken = request["uiastoken"] as? String

        assertNotNull(uiasToken)
        assertTrue(uiasToken.startsWith("eyJ"))
    }

    @Test
    fun decryptsCapturedQueryCustomResponse() {
        val response = UwcCrypto.decryptResponse(QUERY_CUSTOM_RESPONSE)
        val data = UwcCrypto.parseDataField(response)

        assertEquals("1", response["code"]?.toString())
        assertEquals("SUCCESS", response["msg"])
        assertNotNull(data["allowUseWaterImmediately"])
    }

    @Test
    fun decryptsCapturedGetSysInfoResponse() {
        val response = UwcCrypto.decryptResponse(GET_SYS_INFO_RESPONSE)
        val data = UwcCrypto.parseDataField(response)

        assertEquals("1", response["code"]?.toString())
        assertEquals("SUCCESS", response["msg"])
        assertNotNull(data["appId"])
        assertNotNull(data["appSecret"])
    }

    @Test
    fun encryptIsDeterministicForSharedParity() {
        val plain = """{"test":"hello","num":123}"""

        assertEquals(UwcCrypto.encrypt(plain), UwcCrypto.encrypt(plain))
    }

    private companion object {
        const val LOGIN_BY_TOKEN_RESPONSE: String =
            "sppflaDgPzyJqga7WXiHtzsb6Pj2AtURoYdqsg+JoJLjoNDFaIBw0f9MyVO7chyi+J3k/R8RKv1i8rcD" +
                "bS3c4XAgMulifeFAaRJpNSU7LaAX43RDtreiaV4A4SxvSXsQsVyqM7Up+/07yUt05TjTqbVgLq/4DApr" +
                "+8ksB5EUjzSTM85gNc+UWDugGMYiFx6JfagT2tyFMr5nVLZsBhbHuIVwDjeaZTUnQy0MEohdzyvZivvV" +
                "BS4PcGTTlb6gdE4qCZmxsFv/XpUAnpwL3ICdN+fKOKfhmaOIEBKQ34WKz5Hbba2Z5w26YArbmkQSzbaY" +
                "dGe2+F02EBWaiD79kZEVpbooNGJX8SX/vS1X9Sy5H+DMwp8fJwlspXOmsBuWRNbl3slOFgs0gsTX3j9q" +
                "SGApqh9QD7QJ+kicBy6fH5TofdPNqZfTE89uvPrXtp5f6qTwuQGkBVFiSWwhHFgX5sOVDbeF3GXNa67p" +
                "7k9ecaMYIlssReL3/HwiR/BHXoMQ2KyUzJhHpnLlP1b/3mQkMymBty/io25zJc4HDpCp4X1WoN0SsV5k" +
                "MNDlr2kp5Lps9nWjG9CRzamKHbOh1KHqxyTKzeSIUoCHGrmfGz1hnVpeoWr2RfZY6MHoWO3a+gmWdUBj" +
                "lEmLXq+MfT0="

        const val QUERY_CUSTOM_RESPONSE: String =
            "sppflaDgPzyJqga7WXiHtzsb6Pj2AtURoYdqsg+JoJLjoNDFaIBw0TUuOnw90UBBRxyHBppjwh8C65GT" +
                "HNO/TijrwbFuIigGrqVtfNAT/YWaGQgkW2Jain7dMwY+w7PBoU9zrhq44Sc4wGgQhvEXcUQloTLKQ6KK" +
                "aJyDMlFqVwCH0w+TPVS9nwWFL543H20NXOtvi+QsrGJkZvsuiYeebpG2libSynri+YSB7o+9Hr8euXm7" +
                "YHJNLMwtxQXU5jJrVcMhCOzg4bMyWBUXpwTTe2YHmPazEhwGgGbc5i3GkW4="

        const val GET_SYS_INFO_RESPONSE: String =
            "sppflaDgPzyJqga7WXiHtzsb6Pj2AtURoYdqsg+JoJLjoNDFaIBw0UOlPdlvjQuSNY7nGiaAgrBAfB+0" +
                "bkhdAJuLPA8tRJSIXGVmGKY1rDVocFbO46aLAEynLwZsJGR0amLfnk0p1cjEdTfNfZFDhawIbS7YQmKx" +
                "ap8044tGN/9PUvOWdWpGnevKFq7BHf6wwodlcDR2cj/qQc0iSD085YSd1AAEMSrSUwVwVsJQ58qpetWV" +
                "Cj3DSJH8howZBLBtVyT1C/lhwJtP55bYprzN/9cqC0KmVtLCrtgf7UuXE4lrWtbQEnSqgKJECmYfk5/F" +
                "im9QroC3mEw="

        const val LOGIN_BY_TOKEN_REQUEST_PARAM: String =
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
    }
}
