package no.nav.org.proxy

internal fun List<Pair<String, String?>>.redactAuthorization(): List<Pair<String, String?>> =
    map { (name, value) ->
        name to if (name.equals("Authorization", ignoreCase = true)) "<redacted>" else value
    }
