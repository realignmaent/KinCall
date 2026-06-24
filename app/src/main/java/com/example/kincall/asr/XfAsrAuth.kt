package com.example.kincall.asr

import android.util.Base64
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 讯飞语音识别 HMAC-SHA256 鉴权工具类
 *
 * 按照讯飞官方文档构造 WebSocket 连接的鉴权 URL：
 * 1. 构造 signature_origin（签名原始字符串）
 * 2. 使用 HMAC-SHA256 对签名原始字符串计算签名
 * 3. 构造 authorization_origin（鉴权原始字符串）
 * 4. 对 authorization_origin 进行 Base64 编码得到最终的 authorization
 */
object XfAsrAuth {

    /**
     * 构造带鉴权信息的完整 WebSocket URL
     *
     * @param apiKey 讯飞 API Key
     * @param apiSecret 讯飞 API Secret
     * @param host 讯飞服务域名，默认 "iat-api.xfyun.cn"
     * @return 完整的 WebSocket 鉴权 URL
     */
    fun buildAuthUrl(
        apiKey: String,
        apiSecret: String,
        host: String = "iat-api.xfyun.cn"
    ): String {
        // 获取 GMT 格式的日期字符串
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")
        val date = dateFormat.format(Date())

        // 第一步：构造签名原始字符串
        // signature_origin = "host: {host}\ndate: {date}\nGET /v2/iat HTTP/1.1"
        val signatureOrigin = "host: $host\ndate: $date\nGET /v2/iat HTTP/1.1"

        // 第二步：使用 HMAC-SHA256 计算签名
        val signature = hmacSha256(apiSecret, signatureOrigin)

        // 第三步：构造鉴权原始字符串
        // authorization_origin = 'api_key="{apiKey}", algorithm="hmac-sha256", headers="host date request-line", signature="{Base64(signature)}"'
        val signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP)
        val authorizationOrigin =
            "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"$signatureBase64\""

        // 第四步：对鉴权原始字符串进行 Base64 编码
        val authorization = Base64.encodeToString(
            authorizationOrigin.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

        // URL 编码参数，拼接完整的 WebSocket URL
        val encodedAuthorization = URLEncoder.encode(authorization, "UTF-8")
        val encodedDate = URLEncoder.encode(date, "UTF-8")
        val encodedHost = URLEncoder.encode(host, "UTF-8")

        return "wss://$host/v2/iat?authorization=$encodedAuthorization&date=$encodedDate&host=$encodedHost"
    }

    /**
     * HMAC-SHA256 签名计算
     *
     * @param secret 密钥（apiSecret）
     * @param content 待签名的内容
     * @return 签名后的字节数组
     */
    private fun hmacSha256(secret: String, content: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(content.toByteArray(Charsets.UTF_8))
    }
}
