package me.rerere.ai.provider

import okhttp3.Response
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Structured status without keeping upstream bodies, prompts or credentials in the exception. */
class ProviderHttpException(val statusCode: Int, val retryAfterMs: Long? = null) : IOException("Provider HTTP $statusCode")

fun Response.providerHttpException(): ProviderHttpException {
    val header = header("Retry-After")?.trim()
    val delay = header?.toLongOrNull()?.takeIf { it >= 0 }?.coerceAtMost(86_400)?.times(1000) ?: runCatching {
        if (header == null) null else (ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - System.currentTimeMillis()).coerceIn(0, 86_400_000)
    }.getOrNull()
    body?.close()
    return ProviderHttpException(code, delay)
}
