package com.vukan.agentskillcomposer.model

data class DetectedFramework(
    val name: String,
    val version: String? = null,
    val evidence: String,
) {
    fun toDisplayString(): String = buildString {
        append(name)
        version?.let { append(" ($it)") }
    }
}
