package com.hgu.watervalve.data.remote.crypto

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hgu.watervalve.util.Constants
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * UWC API 加密/签名工具。
 *
 * 算法规格（来自项目文档 + 抓包验证）：
 * - 加密: TripleDES-CBC-Pkcs7（Java 中 PKCS5Padding 兼容）
 * - 密钥: 24 字节 UTF-8 → 192-bit DESede key
 * - IV:   8 字节 UTF-8
 * - 签名: 参数排序 → MD5 → 32 位小写 hex → Base64
 *
 * ## 注意事项
 * - buildParamStr 返回**原始 Base64**（不含 URL encode）—— Retrofit `@FormUrlEncoded` 会自动编码。
 * - decryptResponse 的输入是**标准 Base64**（响应 JSON 中的 resultMap 不含 URL encode）。
 * - 响应中 `data` 字段是**二次 JSON 字符串**，需要调用方自行 parseDataField() 解析。
 */
object UwcCrypto {

    private val gson = Gson()

    // --- 密钥初始化 ---
    // Key: 24 字节 UTF-8 = 192-bit TripleDES key
    // IV:  8 字节 UTF-8 = 64-bit CBC IV

    private val keySpec: SecretKeySpec = SecretKeySpec(
        Constants.DES_KEY.toByteArray(Charsets.UTF_8),
        "DESede"
    )

    private val ivSpec: IvParameterSpec = IvParameterSpec(
        Constants.DES_IV.toByteArray(Charsets.UTF_8)
    )

    // ═══════════════════════════════════════════════════════════
    // TripleDES-CBC 加解密
    // ═══════════════════════════════════════════════════════════

    /**
     * TripleDES-CBC-Pkcs7 加密，输出 Base64。
     * 明文使用 UTF-8 编码。
     */
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    /**
     * Base64 → TripleDES-CBC 解密，输出 UTF-8 明文字符串。
     */
    fun decrypt(cipherBase64: String): String {
        val cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decoded = Base64.getDecoder().decode(cipherBase64)
        val decrypted = cipher.doFinal(decoded)
        return String(decrypted, Charsets.UTF_8)
    }

    // ═══════════════════════════════════════════════════════════
    // MD5 签名
    // ═══════════════════════════════════════════════════════════

    /**
     * UWC 参数签名：
     * 1. 参数按 key 字母排序
     * 2. 拼接为 `key1=val1&key2=val2&...`
     * 3. MD5 → 32 位小写 hex
     * 4. hex 的 UTF-8 字节做 Base64 → 签名值
     *
     * 注意：调用方应先把 `merchantKey` 加入 params 再调用此函数；
     * 签名完成后删除 `merchantKey`，将返回值作为 `sign` 字段。
     */
    fun sign(params: Map<String, Any>): String {
        val sorted = params.toSortedMap()
        val concat = sorted.entries.joinToString("&") { (k, v) -> "$k=$v" }
        val md5Bytes = MessageDigest.getInstance("MD5").digest(
            concat.toByteArray(Charsets.UTF_8)
        )
        val hex = md5Bytes.joinToString("") { "%02x".format(it) }
        return Base64.getEncoder().encodeToString(hex.toByteArray(Charsets.UTF_8))
    }

    // ═══════════════════════════════════════════════════════════
    // buildParamStr / decryptResponse
    // ═══════════════════════════════════════════════════════════

    /**
     * 构建 UWC 请求体 `paramStr` 字段。
     *
     * 流程：params + merchantKey → sign()
     *       → 去掉 merchantKey，加入 sign
     *       → JSON 序列化
     *       → TripleDES 加密 → Base64
     *
     * 返回**原始 Base64**（不 URL encode，由 Retrofit 处理）。
     */
    fun buildParamStr(params: Map<String, Any>): String {
        val withMerchant = params.toMutableMap()
        withMerchant["merchantKey"] = Constants.MERCHANT_KEY
        val signValue = sign(withMerchant)
        withMerchant.remove("merchantKey")
        withMerchant["sign"] = signValue
        // 使用 TreeMap 保证 JSON key 顺序确定（与 sign 的排序一致）
        val sorted = withMerchant.toSortedMap()
        val json = gson.toJson(sorted)
        return encrypt(json)
    }

    /**
     * 解密 UWC 响应的 `resultMap` 字段。
     *
     * 输入是标准 Base64（响应 JSON 中含 `+` `/` `=`，非 URL-encoded）。
     * 流程：Base64 decode → TripleDES 解密 → JSON parse → Map
     */
    fun decryptResponse(encryptedBase64: String): Map<String, Any?> {
        val decrypted = decrypt(encryptedBase64)
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        @Suppress("UNCHECKED_CAST")
        return gson.fromJson(decrypted, type) as Map<String, Any?>
    }

    /**
     * 解密响应后，`data` 字段通常是二次 JSON 字符串。
     * 调用此方法解析 `data` 字段为 Map。
     */
    fun parseDataField(response: Map<String, Any?>): Map<String, Any?> {
        val dataField = response["data"] as? String ?: return emptyMap()
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        @Suppress("UNCHECKED_CAST")
        return gson.fromJson(dataField, type) as Map<String, Any?>
    }

    // ═══════════════════════════════════════════════════════════
    // UIS Sign（HMAC-SHA512）
    // ═══════════════════════════════════════════════════════════

    /**
     * 生成 UIS API 的 Sign 请求头。
     *
     * 算法（推测，待真机验证）：
     * 1. 将请求参数拼接为 `key1=val1&key2=val2&...`
     * 2. 使用 HMAC-SHA512(key, data) 生成 512-bit 签名
     * 3. 输出 128 位小写 hex 字符串
     *
     * @param data 待签名的参数字符串（不含 merchantKey）
     * @param key  签名密钥（来自 [Constants.UIS_SIGN_KEY]）
     * @return 128 字符小写 hex 签名
     */
    fun signUis(data: String, key: String = Constants.UIS_SIGN_KEY): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA512")
        val keySpec = javax.crypto.spec.SecretKeySpec(
            key.toByteArray(Charsets.UTF_8),
            "HmacSHA512"
        )
        mac.init(keySpec)
        val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    // ═══════════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════════

    /** 生成 UUID v4 字符串（用作 nonce） */
    fun generateNonce(): String = UUID.randomUUID().toString()

    /** 生成当前毫秒时间戳 */
    fun generateTimestamp(): Long = System.currentTimeMillis()
}
