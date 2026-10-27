package no.nav.org.proxy

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.SignedJWT
import no.nav.org.proxy.token.TokenValidation
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.UUID

class TokenValidationTest {
    private val server = MockOAuth2Server()
    private val clientId = "test-${UUID.randomUUID()}"
    private val issuer = "test-issuer"

    @BeforeEach
    fun setUp() {
        assertFalse(TokenValidation.isDev, "Run characterization tests without the dev whitelist override")
        TokenValidation.validators.clear()
        server.start()
        installValidator(clientId)
    }

    @AfterEach
    fun tearDown() {
        TokenValidation.validators.clear()
        server.shutdown()
    }

    private fun installValidator(audience: String) {
        // Same issuer configuration as production, but local discovery. The env lookup in addValidator is not covered.
        TokenValidation.validators[audience] = JwtTokenValidationHandler(
            MultiIssuerConfiguration(mapOf("azure" to IssuerProperties(server.wellKnownUrl(issuer).toUrl(), listOf(audience)))),
        )
    }

    private fun token(issuerId: String = issuer, audience: String = clientId, expiry: Long = 3600): SignedJWT =
        server.issueToken(issuerId = issuerId, subject = "synthetic-subject", audience = audience, expiry = expiry)

    private fun accepts(header: String?, audience: String = clientId): Boolean {
        val request = Request(GET, "/test")
        return TokenValidation.containsValidToken(
            if (header == null) request else request.header("Authorization", header), audience,
        )
    }

    @Test
    fun `accepts signed token with configured issuer and target audience`() {
        assertTrue(accepts("Bearer ${token().serialize()}"))
    }

    @Test
    fun `rejects token for another audience`() {
        assertFalse(accepts("Bearer ${token(audience = "other-client").serialize()}"))
    }

    @Test
    fun `rejects token from unknown issuer`() {
        assertFalse(accepts("Bearer ${token(issuerId = "unknown-issuer").serialize()}"))
    }

    @Test
    fun `rejects token expired beyond clock skew`() {
        assertFalse(accepts("Bearer ${token(expiry = -3600).serialize()}"))
    }

    @Test
    fun `rejects token signed with a different key even with trusted key id and valid claims`() {
        val valid = token()
        // Keep the trusted key ID so rejection exercises signature verification, not just key lookup.
        val forged = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(valid.header.keyID).build(), valid.jwtClaimsSet)
        forged.sign(RSASSASigner(RSAKeyGenerator(2048).generate()))

        assertFalse(accepts("Bearer ${forged.serialize()}"))
    }

    @Test
    fun `rejects missing malformed and non bearer authorization`() {
        val validToken = token().serialize()
        listOf(null, "", "Bearer", "Bearer not-a-jwt", "Basic $validToken").forEach { header ->
            assertFalse(accepts(header), "Missing or malformed authorization must not validate")
        }
    }

    @Test
    fun `skip audience does not bypass validation outside dev`() {
        installValidator("skip")
        assertFalse(accepts(null, "skip"))
    }
}
