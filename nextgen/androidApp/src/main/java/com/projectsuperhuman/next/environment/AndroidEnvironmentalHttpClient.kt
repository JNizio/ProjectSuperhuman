package com.projectsuperhuman.next.environment

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidEnvironmentalHttpClient : EnvironmentalHttpClient {
    override suspend fun get(request: EnvironmentalHttpRequest): EnvironmentalHttpResponse = withContext(Dispatchers.IO) {
        val query = request.query.entries.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
        val connection = URI.create("${request.endpoint}?$query").toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { BufferedReader(InputStreamReader(it)).use(BufferedReader::readText) }.orEmpty()
            EnvironmentalHttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
}
