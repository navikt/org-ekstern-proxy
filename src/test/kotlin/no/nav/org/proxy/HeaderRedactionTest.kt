package no.nav.org.proxy

import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class HeaderRedactionTest {
    @Test
    fun `redacts every authorization value regardless of case scheme or formatting`() {
        val credentials = listOf(
            "Bearer eyJsynthetic.first.signature",
            "bearer   eyJsynthetic.second.signature",
            "Bearer\teyJsynthetic.third.signature",
            "Bearer eyJfirst, Bearer eyJsecond",
            "Basic synthetic-credentials",
            "unknown-scheme synthetic-secret",
            "",
            null,
        )
        val headers = credentials.mapIndexed { index, value ->
            (if (index % 2 == 0) "authorization" else "AUTHORIZATION") to value
        } + listOf("Content-Type" to "application/json", "X-Test" to null)
        val original = headers.toList()

        val redacted = headers.redactAuthorization()

        assertEquals(headers.take(credentials.size).map { it.first to "<redacted>" }, redacted.take(credentials.size))
        assertEquals(headers.takeLast(2), redacted.takeLast(2))
        assertEquals(credentials.size, redacted.count { it.second == "<redacted>" })
        assertFalse(redacted.toString().contains("eyJ"))
        credentials.filterNotNull().filter { it.isNotEmpty() }.forEach {
            assertFalse(redacted.toString().contains(it))
        }
        assertEquals(original, headers)
    }

    @Test
    fun `redacting diagnostic headers leaves original request unchanged`() {
        val token = "eyJsynthetic.payload.signature"
        val request = Request(GET, "/test").header("Authorization", "Bearer $token").header("X-Test", "unchanged")
        val original = request.headers.toList()

        val diagnosticText = "Headers: ${request.headers.redactAuthorization()}"

        assertFalse(diagnosticText.contains(token))
        assertEquals(original, request.headers)
        assertEquals("Bearer $token", request.toNavRequest().getHeader("Authorization"))
    }
}
