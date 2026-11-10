package br.com.projetoia.app.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebJwtTest {
    private val now = 1_750_000_000_000L

    private fun sign(secret: String = "secret", expiresAt: Long = now + 60_000) = WebJwt.sign(
        secret = secret,
        issuer = "projetoia-web",
        audience = "projetoia-web-client",
        subject = "web-access",
        issuedAtMillis = now,
        expiresAtMillis = expiresAt,
    )

    private fun verify(
        token: String,
        secret: String = "secret",
        audience: String = "projetoia-web-client",
        nowMillis: Long = now,
    ) = WebJwt.verify(
        token = token,
        secret = secret,
        issuer = "projetoia-web",
        audience = audience,
        subject = "web-access",
        nowMillis = nowMillis,
    )

    @Test
    fun `verifies a token it signed`() {
        assertTrue(verify(sign()))
    }

    @Test
    fun `accepts independently signed tokens in the legacy claim layout`() {
        // Same header/claims layout java-jwt produced, signed with HMAC256("secret")
        val token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9." +
            "eyJzdWIiOiJ3ZWItYWNjZXNzIiwiYXVkIjoicHJvamV0b2lhLXdlYi1jbGllbnQiLCJpc3MiOiJwcm9qZXRvaWEtd2ViIiwiZXhwIjoxODAwMDAwMDAwLCJpYXQiOjE3MDAwMDAwMDB9." +
            "VVx7ZtFUbdLRTi4D6DkuK8ivFrPvz9Jq4mMPEJvQ3xY"
        assertTrue(verify(token))
        assertFalse(verify(token, secret = "other"))
    }

    @Test
    fun `rejects wrong secret, audience and expired tokens`() {
        assertFalse(verify(sign(), secret = "other"))
        assertFalse(verify(sign(), audience = "someone-else"))
        assertFalse(verify(sign(expiresAt = now - 1_000)))
        assertFalse(verify(sign(), nowMillis = now - 10_000))
    }

    @Test
    fun `rejects tampered or malformed tokens`() {
        val (header, payload, signature) = sign().split('.')
        val forged = WebJwt.sign(
            secret = "attacker",
            issuer = "projetoia-web",
            audience = "projetoia-web-client",
            subject = "web-access",
            issuedAtMillis = now,
            expiresAtMillis = now + 3_600_000_000,
        ).split('.')[1]
        assertFalse(verify("$header.$forged.$signature"))
        assertFalse(verify("$header.$payload"))
        assertFalse(verify("$header.$payload.!!!"))
        assertFalse(verify(""))
    }
}
