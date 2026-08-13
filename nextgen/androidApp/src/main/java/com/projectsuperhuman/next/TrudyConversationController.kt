package com.projectsuperhuman.next

/**
 * Narrow UI-facing seam for Trudy conversation requests.
 *
 * The native screen depends only on this contract. A future application-layer implementation can
 * replace [LocalTrudyConversationController] without giving the UI direct access to health storage,
 * module services, SQL, Health Connect, or model-provider details.
 */
interface TrudyConversationController {
    suspend fun respondTo(text: String): TrudyReply
}

/** Deterministic local demo implementation. It never reads personal or health data. */
class LocalTrudyConversationController : TrudyConversationController {
    override suspend fun respondTo(text: String): TrudyReply {
        val normalized = text.trim().lowercase()
        return when {
            "sleep" in normalized -> TrudyReply(
                text = "Demo mode: I’m not connected to your health data yet. When the Trudy backend is attached, I’ll be able to explain your sleep context here rather than inventing a measurement.",
                evidence = listOf("Local demo response · no health data accessed")
            )
            "changed" in normalized || "today" in normalized -> TrudyReply(
                text = "Demo mode: this shell can show a daily-change explanation once the backend is connected. Right now I’m only verifying the conversation flow and won’t pretend to know what changed.",
                evidence = listOf("Local demo response · no health data accessed")
            )
            "pay attention" in normalized || "attention" in normalized -> TrudyReply(
                text = "Demo mode: future Trudy can surface the most relevant things to review and explain why they matter. This local build deliberately has no access to your measurements.",
                evidence = listOf("Local demo response · no health data accessed")
            )
            else -> TrudyReply(
                text = "Demo mode: the Trudy UI is working. This reply is generated locally and does not use personal measurements, Data Vault, Health Connect, or an AI model.",
                evidence = listOf("Local deterministic placeholder")
            )
        }
    }
}
