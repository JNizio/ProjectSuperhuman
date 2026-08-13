package com.projectsuperhuman.next.environment

const val DEFAULT_ENVIRONMENT_FRESH_MS = 15L * 60L * 1_000L
const val DEFAULT_ENVIRONMENT_MAX_STALE_MS = 6L * 60L * 60L * 1_000L

enum class EnvironmentalProviderErrorKind { NETWORK, HTTP, PARSING, PROVIDER_UNAVAILABLE }

data class EnvironmentalProviderError(
    val kind: EnvironmentalProviderErrorKind,
    val recoverable: Boolean,
    val httpStatus: Int? = null
)

data class EnvironmentalHttpRequest(val endpoint: String, val query: Map<String, String>)
data class EnvironmentalHttpResponse(val statusCode: Int, val body: String)

interface EnvironmentalHttpClient {
    suspend fun get(request: EnvironmentalHttpRequest): EnvironmentalHttpResponse
}
