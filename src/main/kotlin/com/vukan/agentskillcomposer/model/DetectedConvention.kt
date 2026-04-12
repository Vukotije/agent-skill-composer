package com.vukan.agentskillcomposer.model

typealias Evidence = List<String>

data class DetectedConvention(
    val type: ConventionType,
    val summary: String,
    val confidence: ConventionConfidence,
    val evidence: Evidence,
) {
    fun toDisplayString(): String = buildString {
        append("[${confidence.displayName}] ")
        append(summary)
    }
}
