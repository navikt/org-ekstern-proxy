package no.nav.org.proxy

import io.prometheus.client.exporter.common.TextFormat
import mu.KotlinLogging
import no.nav.org.proxy.token.TokenValidation
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.cookie.StandardCookieSpec
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.util.Timeout
import org.http4k.client.ApacheClient
import org.http4k.core.*
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.NON_AUTHORITATIVE_INFORMATION
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.Netty
import org.http4k.server.asServer
import java.io.File
import java.io.StringWriter

const val NAIS_DEFAULT_PORT = 8080
const val NAIS_ISALIVE = "/internal/isAlive"
const val NAIS_ISREADY = "/internal/isReady"
const val NAIS_METRICS = "/internal/metrics"

const val API_URI_VAR = "rest"
const val API_INTERNAL_TEST_URI = "/internal/test/{$API_URI_VAR:.*}"
const val API_URI = "/{$API_URI_VAR:.*}"

const val TARGET_APP = "target-app"
const val TARGET_CLIENT_ID = "target-client-id"
const val HOST = "host"
const val X_CLOUD_TRACE_CONTEXT = "x-cloud-trace-context"

const val ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method"

const val env_WHITELIST_FILE = "WHITELIST_FILE"

object Application {
    private val log = KotlinLogging.logger { }

    val rules = Rules.parse(System.getenv(env_WHITELIST_FILE))

    val httpClient = HttpClients.custom()
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                .setRedirectsEnabled(false)
                .setCookieSpec(StandardCookieSpec.IGNORE)
                .build()
        ).build()

    val client = ApacheClient(httpClient)

    fun start() {
        log.info { "Starting" }
        apiServer(NAIS_DEFAULT_PORT).start()
        log.info { "Finished!" }
    }

    fun apiServer(port: Int): Http4kServer = api().asServer(Netty(port))

    fun api(): HttpHandler = routes(
        NAIS_ISALIVE bind Method.GET to { Response(OK) },
        NAIS_ISREADY bind Method.GET to { Response(OK) },
        NAIS_METRICS bind Method.GET to {
            runCatching {
                StringWriter().let { str ->
                    TextFormat.write004(str, Metrics.cRegistry.metricFamilySamples())
                    str
                }.toString()
            }
                .onFailure {
                    log.error { "/prometheus failed writing metrics - ${it.localizedMessage}" }
                }
                .getOrDefault("").let {
                    if (it.isNotEmpty()) Response(OK).body(it) else Response(Status.NO_CONTENT)
                }
        },
        API_INTERNAL_TEST_URI bind { req: Request ->
            val path = (req.path(API_URI_VAR) ?: "")
            Metrics.testApiCalls.labels(path).inc()
            log.info { "Test url called with path $path" }
            val method = req.method
            val targetApp = req.header(TARGET_APP)
            if (targetApp == null) {
                Response(BAD_REQUEST).body("Proxy: Missing target-app header")
            } else {
                val team = rules.filter { it.value.keys.contains(targetApp) }.map { it.key }.firstOrNull()
                if (team == null) {
                    Response(NON_AUTHORITATIVE_INFORMATION).body("App not found in rules. Not approved")
                } else {
                    var approved = false
                    var report = "Report:\n"
                    rules[team]?.let { it[targetApp] }?.filter {
                        report += "Evaluating $it on method ${req.method}, path /$path "
                        it.evaluateAsRule(method, "/$path").also { report += "$it\n" }
                    }?.firstOrNull()?.let {
                        approved = true
                    }
                    report += if (approved) "Approved" else "Not approved"
                    Response(OK).body(report)
                }
            }
        },
        API_URI bind { req: Request ->
            val path = req.path(API_URI_VAR) ?: ""
            Metrics.apiCalls.labels(path).inc()

            if (req.method == Method.OPTIONS) { // preflight - send unchanged to backend
                val accessMethodHeader = req.header(ACCESS_CONTROL_REQUEST_METHOD)
                if (accessMethodHeader == null) {
                    log.info { "Proxy: Invalid preflight - missing header ACCESS_CONTROL_REQUEST_METHOD" }
                    Response(BAD_REQUEST).body("Proxy: Invalid preflight - missing header ACCESS_CONTROL_REQUEST_METHOD")
                } else {
                    val accessMethod = Method.valueOf(accessMethodHeader)
                    var preflightUrl: String? = run outer@{
                        rules.forEach { team ->
                            team.value.forEach { app ->
                                app.value.firstOrNull { it.evaluateAsRule(accessMethod, "/$path") }?.let {
                                    val preflightUrl = "http://${app.key}.${team.key}${req.uri}"
                                    log.debug { "Found rule, created preflight proxy: $preflightUrl" }
                                    return@outer preflightUrl
                                }
                            }
                        }
                        null
                    }
                    if (preflightUrl != null) {
                        val forwardHeaders =
                            req.headers.filter {
                                !it.first.startsWith("x-") || it.first == X_CLOUD_TRACE_CONTEXT
                            }.toList()
                        val redirect = Request(req.method, preflightUrl).headers(forwardHeaders)
                        log.info { "Forwarded call to ${req.method} $preflightUrl" }
                        val result = client(redirect)
                        log.debug { result }
                        result
                    } else {
                        log.info { "Proxy: Bad preflight - not whitelisted" }
                        Response(BAD_REQUEST).body("Proxy: Bad preflight - not whitelisted")
                    }
                }
            } else {
                val targetApp = req.header(TARGET_APP)
                val targetClientId = req.header(TARGET_CLIENT_ID)

                if (targetApp == null || targetClientId == null) {
                    log.info { "Proxy: Bad request - missing header" }
                    File("/tmp/missingheader").writeText("Call:\nPath: $path\nMethod: ${req.method}\n Uri: ${req.uri}\nBody: ${req.body}\nHeaders: $${req.headers}")

                    Response(BAD_REQUEST).body("Proxy: Bad request - missing header")
                } else {
                    File("/tmp/latestcall").writeText("Call:\nPath: $path\nMethod: ${req.method}\n Uri: ${req.uri}\nBody: ${req.body}\nHeaders: $${req.headers}")

                    val team = rules.filter { it.value.keys.contains(targetApp) }.map { it.key }.firstOrNull()

                    val approvedByRules =
                        if (team == null) {
                            false
                        } else {
                            rules[team]?.let { it[targetApp] }?.filter {
                                it.evaluateAsRule(req.method, "/$path")
                            }?.firstOrNull()?.let {
                                true
                            } ?: false
                        }

                    if (!approvedByRules) {
                        log.info { "Proxy: Bad request - not whitelisted" }
                        Response(BAD_REQUEST).body("Proxy: Bad request")
                    } else if (!TokenValidation.containsValidToken(req, targetClientId)) {
                        log.info { "Proxy: Not authorized" }
                        Response(UNAUTHORIZED).body("Proxy: Not authorized")
                    } else {
                        val blockFromForwarding = listOf(TARGET_APP, TARGET_CLIENT_ID, HOST)
                        val forwardHeaders =
                            req.headers.filter {
                                !blockFromForwarding.contains(it.first) &&
                                        !it.first.startsWith("x-") || it.first == X_CLOUD_TRACE_CONTEXT
                            }.toList()
                        log.debug { req.headers.filter { it.first.lowercase() != "authorization" }.toList() }
                        log.debug { forwardHeaders.filter { it.first.lowercase() != "authorization" } }
                        val internUrl =
                            "http://$targetApp.$team${req.uri}" // svc.cluster.local skipped due to same cluster
                        val redirect = Request(req.method, internUrl).body(req.body).headers(forwardHeaders)
                        log.info { "Forwarded call to ${req.method} $internUrl" }
                        log.debug { "Body for forwarded call:\n ${req.body}" }
                        val time = System.currentTimeMillis()
                        val result = client(redirect)
                        if (result.status.code == 504) {
                            log.info { "Status Client Timeout after ${System.currentTimeMillis() - time} millis" }
                        }
                        log.debug { result.headers }
                        val filteredHeaders = result.headers.filter { it.first.lowercase() != "transfer-encoding" }
                        log.debug { filteredHeaders }
                        log.debug { "Response body:\n ${result.body}" }
                        log.info { "Returning response to remote Host for ${req.method} $internUrl" }
                        Response(OK).headers(filteredHeaders).body(result.body);
                    }
                }
            }
        },
    )
}
