package com.vukan.agentskillcomposer.model

data class DetectedFramework(
    val name: String,
    val version: String? = null,
    val evidence: String,
)
