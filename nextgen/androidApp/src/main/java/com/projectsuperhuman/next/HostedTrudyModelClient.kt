package com.projectsuperhuman.next

import com.projectsuperhuman.next.trudy.TrudyEvidenceReference
import com.projectsuperhuman.next.trudy.TrudyFormattedModelInput
import com.projectsuperhuman.next.trudy.TrudyModelClient
import com.projectsuperhuman.next.trudy.TrudyModelMetadata
import com.projectsuperhuman.next.trudy.TrudyModelRequest
import com.projectsuperhuman.next.trudy.TrudyModelResult
import com.projectsuperhuman.next.trudy.TrudyPromptFormatter
import com.projectsuperhuman.next.trudy.TrudyToolOperation

/** Provider-neutral hosted settings. Secrets are injected separately and are never logged. */
data class HostedTrudyModelSettings(
    val providerId: String,
    val modelId: String,
    val endpoint: String
)

data class HostedTrudyModelResponse(
    val responseText: String? = null,
    val requestedTools: List<TrudyToolOperation> = emptyList(),
    val evidenceReferences: List<TrudyEvidenceReference> = emptyList(),
    val requestId: String? = null,
    val attributes: Map<String, String> = emptyMap()
)

/**
 * Transport seam for a hosted inference service. A provider implementation owns its HTTP payload,
 * authentication header and response parsing; Trudy core never sees those details.
 */
interface HostedTrudyTransport {
    suspend fun complete(
        settings: HostedTrudyModelSettings,
        credential: String,
        input: TrudyFormattedModelInput
    ): HostedTrudyModelResponse
}

class HostedTrudyConfigurationException(message: String) : IllegalStateException(message)

class HostedTrudyModelClient(
    private val settings: HostedTrudyModelSettings,
    private val credentialProvider: () -> String?,
    private val transport: HostedTrudyTransport,
    private val formatter: TrudyPromptFormatter = TrudyPromptFormatter()
) : TrudyModelClient {
    init {
        require(settings.providerId.isNotBlank()) { "Hosted providerId must not be blank" }
        require(settings.modelId.isNotBlank()) { "Hosted modelId must not be blank" }
        require(settings.endpoint.startsWith("https://")) { "Hosted endpoint must use HTTPS" }
    }

    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        val credential = credentialProvider()?.takeIf { it.isNotBlank() }
            ?: throw HostedTrudyConfigurationException("Hosted Trudy credential is unavailable")
        val result = transport.complete(settings, credential, formatter.format(request))
        return TrudyModelResult(
            responseText = result.responseText,
            requestedTools = result.requestedTools,
            evidenceReferences = result.evidenceReferences,
            metadata = TrudyModelMetadata(
                provider = settings.providerId,
                model = settings.modelId,
                requestId = result.requestId,
                attributes = result.attributes
            )
        )
    }
}
