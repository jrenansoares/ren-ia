package br.com.projetoia.app.data.security

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Portable exports must never carry credentials, including custom headers. */
internal fun sanitizeBackup(value: JsonElement): JsonElement = when (value) {
    is JsonArray -> JsonArray(value.map(::sanitizeBackup))
    is JsonObject -> JsonObject(value.mapValues { (name, child) ->
        val normalized = name.filter(Char::isLetterOrDigit).lowercase(java.util.Locale.ROOT)
        when {
            normalized == "credential" -> JsonNull
            normalized == "mcpservers" -> JsonArray(emptyList())
            normalized in setOf("customheaders", "headers", "custombodies", "custombody") -> when (child) {
                is JsonArray -> JsonArray(emptyList())
                is JsonObject -> JsonObject(emptyMap())
                else -> JsonPrimitive("")
            }
            normalized.contains("password") || normalized.contains("secret") ||
                normalized.endsWith("apikey") || normalized.endsWith("token") ||
                normalized in setOf(
                    "accesskey",
                    "accesskeyid",
                    "privatekey",
                    "authorization",
                    "passphrase"
                ) -> JsonPrimitive("")
            else -> sanitizeBackup(child)
        }
    })
    else -> value
}
