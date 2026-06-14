package com.hgu.watervalve.shared.data.remote.crypto

import com.hgu.watervalve.shared.platform.currentTimeMillis
import com.hgu.watervalve.shared.util.Constants
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

    fun encrypt(plaintext: String): String {
        val encrypted = tripleDesEncryptPlatform(
            data = plaintext.encodeToByteArray(),
            key = Constants.DES_KEY.encodeToByteArray(),
            iv = Constants.DES_IV.encodeToByteArray(),
        )
        return encodeBase64(encrypted)
    }

    fun decrypt(ciphertext: String): String {
        val decrypted = tripleDesDecryptPlatform(
            data = decodeBase64(ciphertext),
            key = Constants.DES_KEY.encodeToByteArray(),
            iv = Constants.DES_IV.encodeToByteArray(),
        )
        return decrypted.decodeToString()
    }

    fun md5(input: String): String = md5HexPlatform(input.encodeToByteArray())

    fun hmacSha512(input: String, key: String = Constants.UIS_SIGN_KEY): String {
        return hmacSha512HexPlatform(
            data = input.encodeToByteArray(),
            key = key.encodeToByteArray(),
        )
    }

    fun sign(params: Map<String, Any?>): String {
        val concat = params.entries
            .sortedBy { it.key }
            .joinToString("&") { entry -> "${entry.key}=${entry.value ?: ""}" }
        return encodeBase64(md5(concat).encodeToByteArray())
    }

    fun buildParamStr(params: Map<String, Any?>): String {
        val withMerchant = params.toMutableMap()
        withMerchant["merchantKey"] = Constants.MERCHANT_KEY
        val signValue = sign(withMerchant)
        withMerchant.remove("merchantKey")
        withMerchant["sign"] = signValue

        val payload = buildJsonObject {
            withMerchant.entries.sortedBy { it.key }.forEach { entry ->
                put(
                    entry.key,
                    when (val value = entry.value) {
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

    fun signUis(data: String, key: String = Constants.UIS_SIGN_KEY): String = hmacSha512(data, key)

    fun generateNonce(): String = "${currentTimeMillis()}-${Random.nextInt(100_000, 999_999)}"

    fun generateTimestamp(): Long = currentTimeMillis()

    private val base64Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private fun encodeBase64(data: ByteArray): String {
        val sb = StringBuilder()
        var index = 0
        while (index < data.size) {
            val b0 = data[index].toInt() and 0xFF
            val b1 = if (index + 1 < data.size) data[index + 1].toInt() and 0xFF else 0
            val b2 = if (index + 2 < data.size) data[index + 2].toInt() and 0xFF else 0

            sb.append(base64Alphabet[b0 shr 2])
            sb.append(base64Alphabet[((b0 shl 4) or (b1 shr 4)) and 0x3F])
            sb.append(if (index + 1 < data.size) base64Alphabet[((b1 shl 2) or (b2 shr 6)) and 0x3F] else '=')
            sb.append(if (index + 2 < data.size) base64Alphabet[b2 and 0x3F] else '=')
            index += 3
        }
        return sb.toString()
    }

    private val base64DecodeMap: IntArray by lazy {
        val map = IntArray(128) { -1 }
        for (i in base64Alphabet.indices) {
            map[base64Alphabet[i].code] = i
        }
        map
    }

    private fun decodeBase64(input: String): ByteArray {
        val cleanInput = input.replace("\n", "").replace("\r", "").replace(" ", "")
        val result = mutableListOf<Byte>()
        var index = 0
        while (index < cleanInput.length) {
            val c0 = base64DecodeMap.getOrElse(cleanInput[index].code) { -1 }
            val c1 = if (index + 1 < cleanInput.length) base64DecodeMap.getOrElse(cleanInput[index + 1].code) { -1 } else -1
            val c2 = if (index + 2 < cleanInput.length) base64DecodeMap.getOrElse(cleanInput[index + 2].code) { -1 } else -1
            val c3 = if (index + 3 < cleanInput.length) base64DecodeMap.getOrElse(cleanInput[index + 3].code) { -1 } else -1

            if (c0 >= 0 && c1 >= 0) result.add(((c0 shl 2) or (c1 shr 4)).toByte())
            if (c2 >= 0 && index + 2 < cleanInput.length && cleanInput[index + 2] != '=') {
                result.add(((c1 shl 4) or (c2 shr 2)).toByte())
            }
            if (c3 >= 0 && index + 3 < cleanInput.length && cleanInput[index + 3] != '=') {
                result.add(((c2 shl 6) or c3).toByte())
            }
            index += 4
        }
        return result.toByteArray()
    }

    private fun jsonObjectToValueMap(source: JsonObject): Map<String, Any?> {
        return source.mapValues { (_, value) ->
            when {
                value is JsonObject -> jsonObjectToValueMap(value)
                isJsonString(value.jsonPrimitive) -> value.jsonPrimitive.content
                value.jsonPrimitive.booleanOrNull != null -> value.jsonPrimitive.boolean
                value.jsonPrimitive.doubleOrNull != null -> value.jsonPrimitive.content.toDouble()
                else -> value.jsonPrimitive.content
            }
        }
    }

    private fun isJsonString(value: JsonPrimitive): Boolean {
        val rendered = value.toString()
        return rendered.length >= 2 && rendered.first() == '"' && rendered.last() == '"'
    }
}
