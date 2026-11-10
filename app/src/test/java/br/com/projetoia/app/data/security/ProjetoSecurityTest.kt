package br.com.projetoia.app.data.security

import kotlinx.serialization.json.Json
import br.com.projetoia.app.data.ai.isSensitiveHeader
import org.junit.Assert.*
import org.junit.Test

class ProjetoSecurityTest {
    @Test fun backupRemovesNestedSecretsAndOpaqueHeaders() {
        val value = Json.parseToJsonElement("""{"providers":[{"name":"Example","apiKey":"TEST_API_SECRET","credential":{"accessToken":"TEST_OAUTH_SECRET"},"customHeaders":[{"name":"X-Anything","value":"TEST_HEADER_SECRET"}],"customBodies":[{"key":"auth","value":"TEST_BODY_SECRET"}]}],"networkSetting":{"proxyPassword":"TEST_PROXY_SECRET"},"s3Config":{"accessKeyId":"TEST_S3_SECRET"},"prompt":"Keep project decisions"}""")
        val output = sanitizeBackup(value).toString()
        assertFalse(output.contains("TEST_"))
        assertTrue(output.contains("Example"))
        assertTrue(output.contains("Keep project decisions"))
        Json.parseToJsonElement(output)
    }

    @Test fun authenticationHeadersAreAlwaysSensitive() {
        for (name in listOf("Authorization", "Proxy-Authorization", "x-api-key", "X-Goog-Api-Key", "Cookie", "Set-Cookie", "x-custom-token")) assertTrue(name, isSensitiveHeader(name))
        assertFalse(isSensitiveHeader("Content-Type"))
    }
}
