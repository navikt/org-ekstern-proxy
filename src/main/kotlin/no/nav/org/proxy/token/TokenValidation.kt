package no.nav.org.proxy.token

import no.nav.org.proxy.env_WHITELIST_FILE
import no.nav.org.proxy.toNavRequest
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import org.http4k.core.Request
import java.net.URI

const val env_AZURE_APP_WELL_KNOWN_URL = "AZURE_APP_WELL_KNOWN_URL"
//const val env_AZURE_APP_CLIENT_ID = "AZURE_APP_CLIENT_ID"

//const val claim_NAME = "name"

object TokenValidation {

    //private val log = KotlinLogging.logger { }

    val validators: MutableMap<String, JwtTokenValidationHandler?> = mutableMapOf()

    private fun addValidator(clientId: String): JwtTokenValidationHandler {
        val validationHandler = JwtTokenValidationHandler(
            MultiIssuerConfiguration(
                mapOf(
                    "azure" to IssuerProperties(
                        URI(System.getenv(env_AZURE_APP_WELL_KNOWN_URL)).toURL(),
                        listOf(clientId),
                    ),
                ),
            ),
        )
        validators[clientId] = validationHandler
        return validationHandler
    }

    fun validatorFor(clientId: String): JwtTokenValidationHandler {
        return validators.get(clientId) ?: addValidator(clientId)
    }

    fun containsValidToken(request: Request, clientId: String): Boolean {
        if (isDev && clientId == "skip") return true
        val firstValidToken = validatorFor(clientId).getValidatedTokens(request.toNavRequest()).firstValidToken
        // For seperation of OBO token and machine token:
        // if (firstValidToken.isPresent) {
        // log.info { "Contains name claim: ${(firstValidToken.get().jwtTokenClaims.get("name") != null)}" }
        // }
        return firstValidToken != null
    }

    val isDev = (System.getenv(env_WHITELIST_FILE) == "/whitelist/dev.json")
}
